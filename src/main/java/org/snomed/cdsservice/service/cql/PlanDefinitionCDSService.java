package org.snomed.cdsservice.service.cql;

import ca.uhn.fhir.context.FhirContext;
import jakarta.annotation.PostConstruct;
import org.hl7.fhir.r4.model.CarePlan;
import org.hl7.fhir.r4.model.DataRequirement;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.PlanDefinition;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.StringType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.cdsservice.model.CDSCard;
import org.snomed.cdsservice.rest.pojo.CDSRequest;
import org.snomed.cdsservice.service.CDSService;
import org.snomed.cdsservice.service.PrefetchResources;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * A CDS service backed by one FHIR {@code PlanDefinition}, evaluated with the embedded Clinical Reasoning engine.
 * <p>
 * On startup it loads the content directory (shared with the other PlanDefinitions from the same directory),
 * derives the prefetch from the PlanDefinition's data requirements (the Patient, plus a patient-scoped search per
 * resource type its logic retrieves), and compiles its CQL, so invalid CQL fails startup and the first request
 * pays no compilation. On each request it applies the PlanDefinition to the prefetched resources and maps the
 * result to cards.
 * <p>
 * A PlanDefinition whose actions point at other PlanDefinitions (a strategy, e.g. a national immunization schedule
 * composed of WHO decision tables) is served by applying each composed rule separately and merging the cards, so
 * every card cites the rule that produced it. The engine's own nested {@code $apply} is not used: rules built from
 * the same ActivityDefinitions produce requests with the same ids, which collide when merged into one CarePlan.
 */
public class PlanDefinitionCDSService extends CDSService {

	static final String ENCOUNTER_PARAMETER = "encounter";
	// CDS Hooks whose context carries encounterId.
	static final Set<String> ENCOUNTER_HOOKS = Set.of("encounter-start", "encounter-discharge");

	private final Path contentDirectory;
	private final String planDefinitionId;

	@Autowired
	private PlanDefinitionEngineProvider engineProvider;
	@Autowired
	private FhirContext fhirContext;

	@Value("${cards.ig-page-bases:http://smart.who.int/immunizations=https://smart.who.int/immunizations}")
	private List<String> igPageBases;

	private CarePlanCardMapper cardMapper;

	private PlanDefinitionEngine engine;
	private PlanDefinition planDefinition;
	// The rules this service applies: the PlanDefinition itself, or the rules it composes.
	private List<PlanDefinition> rules;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public PlanDefinitionCDSService(String serviceId, String contentDirectory, String planDefinitionId, String hook) {
		super(serviceId);
		this.contentDirectory = Path.of(contentDirectory);
		this.planDefinitionId = planDefinitionId;
		setHook(hook);
	}

	@PostConstruct
	public void init() {
		engine = engineProvider.getEngine(contentDirectory);
		cardMapper = new CarePlanCardMapper(CarePlanCardMapper.parseIgPageBases(igPageBases));
		planDefinition = engine.getPlanDefinition(planDefinitionId);
		List<PlanDefinition> composed = engine.composedPlanDefinitions(planDefinitionId);
		rules = composed.isEmpty() ? List.of(planDefinition) : composed;
		setTitle(planDefinition.hasTitle() ? planDefinition.getTitle() : planDefinition.getName());
		setDescription(planDefinition.hasDescription() ? planDefinition.getDescription().strip() : getTitle());
		if (planDefinition.hasUsage()) {
			setUsageRequirements(planDefinition.getUsage().strip());
		}

		List<DataRequirement> dataRequirements = new ArrayList<>();
		long warmUpMs = 0;
		for (PlanDefinition rule : rules) {
			dataRequirements.addAll(engine.dataRequirements(rule.getIdPart()));
			try {
				warmUpMs += engine.warmUp(rule.getIdPart());
			} catch (RuntimeException e) {
				throw new IllegalStateException("Failed to compile the CQL of PlanDefinition/%s: %s".formatted(rule.getIdPart(), e.getMessage()), e);
			}
		}
		setPrefetch(prefetchFor(dataRequirements, getHook()));
		logger.info("Registered PlanDefinition CDS service '{}' (PlanDefinition/{}, {} rule(s), prefetch {}, CQL compiled in {} ms).",
				getId(), planDefinitionId, rules.size(), getPrefetch().keySet(), warmUpMs);
	}

	/**
	 * The current encounter, for logic that evaluates one contact (e.g. WHO SMART ANC's {@code encounter} CQL
	 * parameter): the hook's {@code context.encounterId}, or else the only in-progress Encounter in the prefetch.
	 * Libraries that do not declare the parameter ignore it.
	 */
	static Parameters encounterParameter(CDSRequest cdsRequest, List<Resource> resources) {
		String encounterId = cdsRequest.getContextString("encounterId");
		if (encounterId == null) {
			List<String> inProgress = resources.stream()
					.filter(Encounter.class::isInstance).map(Encounter.class::cast)
					.filter(encounter -> encounter.getStatus() == Encounter.EncounterStatus.INPROGRESS)
					.map(Resource::getIdPart).distinct().toList();
			encounterId = inProgress.size() == 1 ? inProgress.get(0) : null;
		}
		if (encounterId == null) {
			return null;
		}
		Parameters parameters = new Parameters();
		parameters.addParameter().setName(ENCOUNTER_PARAMETER).setValue(new StringType(encounterId.replaceFirst("^Encounter/", "")));
		return parameters;
	}

	/**
	 * The Patient, plus a patient-scoped search per other resource type the logic retrieves. Hooks whose context
	 * names the encounter ({@code encounter-start}, {@code encounter-discharge}) prefetch that Encounter alone.
	 */
	static Map<String, String> prefetchFor(List<DataRequirement> dataRequirements, String hook) {
		boolean encounterContext = ENCOUNTER_HOOKS.contains(hook);
		Map<String, String> prefetch = new TreeMap<>();
		prefetch.put("patient", "Patient/{{context.patientId}}");
		for (DataRequirement requirement : dataRequirements) {
			String type = requirement.getType();
			if (type != null && !type.equals("Patient")) {
				prefetch.putIfAbsent(Character.toLowerCase(type.charAt(0)) + type.substring(1),
						encounterContext && type.equals("Encounter") ? "Encounter/{{context.encounterId}}"
								: type + "?patient={{context.patientId}}");
			}
		}
		return prefetch;
	}

	@Override
	public List<CDSCard> call(CDSRequest cdsRequest) {
		List<Resource> resources = PrefetchResources.parse(fhirContext, cdsRequest);
		String patientId = cdsRequest.getContextString("patientId");
		if (patientId == null) {
			patientId = resources.stream().filter(Patient.class::isInstance).map(Resource::getIdPart).findFirst().orElse(null);
		}
		if (patientId == null) {
			throw new ResponseStatusException(400, "Request has no context.patientId and no Patient in the prefetch.", null);
		}
		String finalPatientId = patientId;
		if (resources.stream().noneMatch(resource -> resource instanceof Patient && finalPatientId.equals(resource.getIdPart()))) {
			throw new ResponseStatusException(412, "Request does not include the required prefetch: Patient/" + patientId + ".", null);
		}

		PlanDefinition strategy = rules.size() == 1 && rules.get(0) == planDefinition ? null : planDefinition;
		Parameters parameters = encounterParameter(cdsRequest, resources);
		List<CDSCard> cards = new ArrayList<>();
		for (PlanDefinition rule : rules) {
			CarePlan carePlan = (CarePlan) engine.apply(rule.getIdPart(), patientId, resources, parameters);
			cards.addAll(cardMapper.toCards(carePlan, rule, strategy, engine.getTerminologyVersions()));
		}
		// Most severe first (critical, warning, info); otherwise keep the strategy's order.
		cards.sort(Comparator.comparing((CDSCard card) -> card.getIndicator().ordinal()).reversed());
		return cards;
	}
}
