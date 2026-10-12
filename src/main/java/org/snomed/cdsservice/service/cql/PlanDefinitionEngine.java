package org.snomed.cdsservice.service.cql;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.repository.IRepository;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Resource;
import org.opencds.cqf.fhir.cql.EvaluationSettings;
import org.opencds.cqf.fhir.cql.LibraryEngine;
import org.opencds.cqf.fhir.cql.engine.retrieve.RetrieveSettings;
import org.opencds.cqf.fhir.cql.engine.terminology.TerminologySettings;
import org.opencds.cqf.fhir.cr.CrSettings;
import org.opencds.cqf.fhir.cr.plandefinition.PlanDefinitionProcessor;
import org.opencds.cqf.fhir.utility.monad.Eithers;
import org.opencds.cqf.fhir.utility.repository.InMemoryFhirRepository;
import org.opencds.cqf.fhir.utility.repository.Repositories;

import java.util.Collection;
import java.util.List;

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

	private final FhirContext fhirContext;
	private final IRepository contentRepository;
	private final CrSettings crSettings;

	public PlanDefinitionEngine(FhirContext fhirContext, Collection<? extends IBaseResource> knowledgeArtifacts) {
		this.fhirContext = fhirContext;
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
		this.crSettings = CrSettings.getDefault().withEvaluationSettings(evaluationSettings);
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
		Bundle dataBundle = new Bundle().setType(Bundle.BundleType.COLLECTION);
		patientData.forEach(resource -> dataBundle.addEntry().setResource(resource));
		IRepository dataRepository = new InMemoryFhirRepository(fhirContext, dataBundle);

		IRepository repository = Repositories.proxy(dataRepository, contentRepository, contentRepository);
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

	public FhirContext getFhirContext() {
		return fhirContext;
	}
}
