package org.snomed.cdsservice.service.cql;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.repository.IRepository;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DataRequirement;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Library;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.PlanDefinition;
import org.hl7.fhir.r4.model.PrimitiveType;
import org.hl7.fhir.r4.model.Resource;
import org.opencds.cqf.cql.engine.runtime.Code;
import org.opencds.cqf.fhir.cql.EvaluationSettings;
import org.opencds.cqf.fhir.cql.LibraryEngine;
import org.opencds.cqf.fhir.cql.engine.retrieve.RetrieveSettings;
import org.opencds.cqf.fhir.cql.engine.terminology.TerminologySettings;
import org.opencds.cqf.fhir.cr.CrSettings;
import org.opencds.cqf.fhir.cr.plandefinition.PlanDefinitionProcessor;
import org.opencds.cqf.fhir.utility.monad.Eithers;
import org.opencds.cqf.fhir.utility.repository.InMemoryFhirRepository;
import org.opencds.cqf.fhir.utility.repository.Repositories;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Runs FHIR Clinical Reasoning {@code PlanDefinition/$apply} in-process, without a HAPI server.
 * <p>
 * Knowledge artifacts (PlanDefinitions, Libraries with CQL/ELM, ValueSets, ActivityDefinitions, ...) are held in
 * an in-memory content repository that also serves terminology. Patient data is supplied per call, typically from
 * the CDS Hooks prefetch, and lives in its own short-lived in-memory repository.
 * <p>
 * The engine settings, and with them the compiled-library and value set caches, are shared across calls.
 * {@link PlanDefinitionProcessor} keeps per-call state, so a new processor is created for every call.
 */
public class PlanDefinitionEngine {

	private static final String WARM_UP_PATIENT_ID = "warm-up";

	private final FhirContext fhirContext;
	private final List<IBaseResource> knowledgeArtifacts;
	private final IRepository contentRepository;
	private final CrSettings crSettings;
	private final Map<String, String> valueSetVersions = new ConcurrentHashMap<>();
	private volatile ActionPriorities actionPriorities = ActionPriorities.NONE;

	public PlanDefinitionEngine(FhirContext fhirContext, Collection<? extends IBaseResource> knowledgeArtifacts) {
		this.fhirContext = fhirContext;
		this.knowledgeArtifacts = List.copyOf(knowledgeArtifacts);
		this.contentRepository = new InMemoryFhirRepository(fhirContext);
		for (IBaseResource artifact : knowledgeArtifacts) {
			contentRepository.update(artifact);
		}

		EvaluationSettings evaluationSettings = EvaluationSettings.getDefault();
		evaluationSettings.setRetrieveSettings(new RetrieveSettings()
				.setSearchParameterMode(RetrieveSettings.SEARCH_FILTER_MODE.FILTER_IN_MEMORY)
				.setTerminologyParameterMode(RetrieveSettings.TERMINOLOGY_FILTER_MODE.FILTER_IN_MEMORY));
		evaluationSettings.setTerminologySettings(new TerminologySettings()
				.setValuesetExpansionMode(TerminologySettings.VALUESET_EXPANSION_MODE.PERFORM_NAIVE_EXPANSION)
				.setValuesetMembershipMode(TerminologySettings.VALUESET_MEMBERSHIP_MODE.USE_EXPANSION)
				.setValuesetPreExpansionMode(TerminologySettings.VALUESET_PRE_EXPANSION_MODE.USE_IF_PRESENT)
				.setCodeLookupMode(TerminologySettings.CODE_LOOKUP_MODE.USE_CODESYSTEM_URL));
		evaluationSettings.setValueSetCache(new ConcurrentHashMap<>());
		this.crSettings = CrSettings.getDefault().withEvaluationSettings(evaluationSettings);
	}

	/**
	 * Creates an engine from every FHIR JSON resource ({@code *.json}) in a directory, e.g. the {@code package}
	 * folder of a FHIR NPM package or a curated subset of one.
	 */
	public static PlanDefinitionEngine fromDirectory(FhirContext fhirContext, Path directory) throws IOException {
		IParser parser = fhirContext.newJsonParser();
		List<IBaseResource> artifacts = new ArrayList<>();
		try (Stream<Path> files = Files.list(directory)) {
			for (Path file : files.filter(f -> f.getFileName().toString().endsWith(".json")).sorted().toList()) {
				artifacts.add(parser.parseResource(Files.readString(file)));
			}
		}
		return new PlanDefinitionEngine(fhirContext, artifacts);
	}

	/**
	 * Applies a PlanDefinition for one patient.
	 *
	 * @param planDefinitionId logical id of the PlanDefinition in the content repository
	 * @param patientId        logical id of the Patient, who must be present in {@code patientData}
	 * @param patientData      the patient's FHIR resources (e.g. from the CDS Hooks prefetch)
	 * @param parameters       CQL parameters (e.g. {@code Today}), or null
	 * @return the $apply result: a CarePlan with the RequestGroup and the generated requests as contained resources
	 */
	public IBaseResource apply(String planDefinitionId, String patientId, List<? extends Resource> patientData, Parameters parameters) {
		IRepository repository = repositoryFor(patientData);
		PlanDefinitionProcessor processor = new PlanDefinitionProcessor(repository, crSettings);
		return processor.apply(
				Eithers.forMiddle3(new IdType("PlanDefinition", planDefinitionId)),
				"Patient/" + patientId,
				null, null, null, null, null, null, null, null,
				parameters,
				null,
				null,
				new LibraryEngine(repository, crSettings.getEvaluationSettings()));
	}

	/**
	 * Compiles the CQL of a PlanDefinition's libraries (and their includes) to ELM and runs {@code $apply} once
	 * for an empty patient, so compiled libraries are cached before the first real request and CQL errors surface
	 * when the content loads rather than at request time.
	 *
	 * @return the time taken, in milliseconds
	 * @throws RuntimeException (typically {@code CqlException}) when a library does not compile or fails to run
	 */
	public long warmUp(String planDefinitionId) {
		long start = System.nanoTime();
		Patient patient = new Patient();
		patient.setId(WARM_UP_PATIENT_ID);
		List<Patient> data = List.of(patient);
		for (CanonicalType library : getPlanDefinition(planDefinitionId).getLibrary()) {
			// An empty expression set evaluates every definition: this compiles the library and its includes.
			evaluate(library.getValue(), WARM_UP_PATIENT_ID, data, Set.of());
		}
		apply(planDefinitionId, WARM_UP_PATIENT_ID, data, null);
		return (System.nanoTime() - start) / 1_000_000;
	}

	/**
	 * Evaluates named expressions of a CQL Library for one patient, e.g. to inspect individual criteria.
	 *
	 * @return one parameter per expression; a null (unknown) result has no value
	 */
	public Parameters evaluate(String libraryUrl, String patientId, List<? extends Resource> patientData, Set<String> expressions) {
		IRepository repository = repositoryFor(patientData);
		return (Parameters) new LibraryEngine(repository, crSettings.getEvaluationSettings())
				.evaluate(libraryUrl, "Patient/" + patientId, null, null, null, null, expressions);
	}

	private IRepository repositoryFor(List<? extends Resource> patientData) {
		Bundle dataBundle = new Bundle().setType(Bundle.BundleType.COLLECTION);
		int anonymous = 0;
		for (Resource resource : patientData) {
			// The in-memory repository indexes resources by id; give id-less resources (allowed in a prefetch) one.
			Resource entry = resource;
			if (!resource.hasIdElement() || resource.getIdElement().getIdPart() == null) {
				entry = resource.copy();
				entry.setId("anonymous-" + ++anonymous);
			}
			dataBundle.addEntry().setResource(entry);
		}
		IRepository dataRepository = new InMemoryFhirRepository(fhirContext, dataBundle);
		return Repositories.proxy(dataRepository, contentRepository, contentRepository);
	}

	/**
	 * @return the patient data the PlanDefinition's logic retrieves ({@code $data-requirements}), used to derive
	 * the CDS Hooks prefetch.
	 */
	public List<DataRequirement> dataRequirements(String planDefinitionId) {
		Library library = (Library) new PlanDefinitionProcessor(contentRepository, crSettings)
				.dataRequirements(Eithers.forMiddle3(new IdType("PlanDefinition", planDefinitionId)), null);
		// $data-requirements reads each action's definition through HAPI's auto-creating getter, which leaves an empty
		// definition on actions that have none (e.g. the WHO SMART ANC decision tables); $apply then fails on it.
		removeEmptyDefinitions(getPlanDefinition(planDefinitionId).getAction());
		return library.getDataRequirement();
	}

	private static void removeEmptyDefinitions(List<PlanDefinition.PlanDefinitionActionComponent> actions) {
		for (PlanDefinition.PlanDefinitionActionComponent action : actions) {
			if (action.getDefinition() instanceof PrimitiveType<?> definition && definition.isEmpty()) {
				action.setDefinition(null);
			}
			removeEmptyDefinitions(action.getAction());
		}
	}

	/**
	 * Supplies the expansion of a value set the engine cannot expand by itself, such as a SNOMED CT ECL value set
	 * expanded by a terminology server. CQL membership tests ({@code in}) against {@code url} then use these codes.
	 *
	 * @param codeSystemVersion the version the expansion was made with, or null if unknown or not applicable
	 */
	public void preloadValueSetExpansion(String url, Collection<Coding> codes, String codeSystemVersion) {
		List<Code> expansion = codes.stream()
				.filter(coding -> coding.hasSystem() && coding.hasCode())
				.map(coding -> new Code().withSystem(coding.getSystem()).withCode(coding.getCode()).withDisplay(coding.getDisplay()))
				// The engine's membership test binary-searches the expansion by code.
				.sorted(Comparator.comparing(Code::getCode))
				.collect(Collectors.toCollection(ArrayList::new));
		crSettings.getEvaluationSettings().getValueSetCache().put(url, expansion);
		if (codeSystemVersion != null) {
			valueSetVersions.put(url, codeSystemVersion);
		}
	}

	/**
	 * @return the code system versions of the preloaded value set expansions (e.g. the SNOMED CT edition and release
	 * an ECL was expanded against), so results can say which terminology they were evaluated with
	 */
	public Set<String> getTerminologyVersions() {
		return new TreeSet<>(valueSetVersions.values());
	}

	/** The content set's curated card indicators ({@link ActionPriorities}), for actions that carry no priority. */
	public ActionPriorities getActionPriorities() {
		return actionPriorities;
	}

	public void setActionPriorities(ActionPriorities actionPriorities) {
		this.actionPriorities = actionPriorities == null ? ActionPriorities.NONE : actionPriorities;
	}

	/**
	 * Registers a CQL namespace, so libraries that include namespaced dependencies (e.g.
	 * {@code include hl7.fhir.uv.cql.FHIRHelpers}) resolve them by canonical URL ({@code <uri>/Library/<name>}).
	 */
	public void registerNamespace(String name, String uri) {
		crSettings.getEvaluationSettings().addRegisteredNamespace(name, uri);
	}

	public List<IBaseResource> getKnowledgeArtifacts() {
		return knowledgeArtifacts;
	}

	/** Resolves a PlanDefinition canonical ({@code url} or {@code url|version}) among the loaded artifacts. */
	public Optional<PlanDefinition> findPlanDefinition(String canonical) {
		int bar = canonical.indexOf('|');
		String url = bar < 0 ? canonical : canonical.substring(0, bar);
		String version = bar < 0 ? null : canonical.substring(bar + 1);
		return knowledgeArtifacts.stream()
				.filter(PlanDefinition.class::isInstance).map(PlanDefinition.class::cast)
				.filter(planDefinition -> url.equals(planDefinition.getUrl()) && (version == null || version.equals(planDefinition.getVersion())))
				.findFirst();
	}

	/**
	 * @return the PlanDefinitions that this PlanDefinition composes through its actions' {@code definitionCanonical}
	 * (a strategy, e.g. a national immunization schedule made of WHO decision tables), in action order; empty for a
	 * plain rule
	 */
	public List<PlanDefinition> composedPlanDefinitions(String planDefinitionId) {
		List<PlanDefinition> composed = new ArrayList<>();
		for (PlanDefinition.PlanDefinitionActionComponent action : getPlanDefinition(planDefinitionId).getAction()) {
			if (action.hasDefinitionCanonicalType()) {
				findPlanDefinition(action.getDefinitionCanonicalType().getValue()).ifPresent(composed::add);
			}
		}
		return composed;
	}

	public PlanDefinition getPlanDefinition(String planDefinitionId) {
		return contentRepository.read(PlanDefinition.class, new IdType("PlanDefinition", planDefinitionId));
	}

	public FhirContext getFhirContext() {
		return fhirContext;
	}
}
