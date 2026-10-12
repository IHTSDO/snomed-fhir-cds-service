package org.snomed.cdsservice.service.cql;

import ca.uhn.fhir.context.FhirContext;
import jakarta.annotation.PostConstruct;
import org.hl7.fhir.r4.model.CarePlan;
import org.hl7.fhir.r4.model.DataRequirement;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.PlanDefinition;
import org.hl7.fhir.r4.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.cdsservice.model.CDSCard;
import org.snomed.cdsservice.rest.pojo.CDSRequest;
import org.snomed.cdsservice.service.CDSService;
import org.snomed.cdsservice.service.PrefetchResources;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A CDS service backed by one FHIR {@code PlanDefinition}, evaluated with the embedded Clinical Reasoning engine.
 * <p>
 * On startup it loads the content directory (shared with the other PlanDefinitions from the same directory),
 * derives the prefetch from the PlanDefinition's data requirements (the Patient, plus a patient-scoped search per
 * resource type its logic retrieves), and compiles its CQL, so invalid CQL fails startup and the first request
 * pays no compilation. On each request it applies the PlanDefinition to the prefetched resources and maps the
 * result to cards.
 */
public class PlanDefinitionCDSService extends CDSService {

	private final Path contentDirectory;
	private final String planDefinitionId;

	@Autowired
	private PlanDefinitionEngineProvider engineProvider;
	@Autowired
	private FhirContext fhirContext;

	private final CarePlanCardMapper cardMapper = new CarePlanCardMapper();

	private PlanDefinitionEngine engine;
	private PlanDefinition planDefinition;

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
		planDefinition = engine.getPlanDefinition(planDefinitionId);
		setTitle(planDefinition.hasTitle() ? planDefinition.getTitle() : planDefinition.getName());
		setDescription(planDefinition.hasDescription() ? planDefinition.getDescription().strip() : getTitle());
		setPrefetch(prefetchFor(engine.dataRequirements(planDefinitionId)));
		long warmUpMs;
		try {
			warmUpMs = engine.warmUp(planDefinitionId);
		} catch (RuntimeException e) {
			throw new IllegalStateException("Failed to compile the CQL of PlanDefinition/%s: %s".formatted(planDefinitionId, e.getMessage()), e);
		}
		logger.info("Registered PlanDefinition CDS service '{}' (PlanDefinition/{}, prefetch {}, CQL compiled in {} ms).",
				getId(), planDefinitionId, getPrefetch().keySet(), warmUpMs);
	}

	static Map<String, String> prefetchFor(List<DataRequirement> dataRequirements) {
		Map<String, String> prefetch = new TreeMap<>();
		prefetch.put("patient", "Patient/{{context.patientId}}");
		for (DataRequirement requirement : dataRequirements) {
			String type = requirement.getType();
			if (type != null && !type.equals("Patient")) {
				prefetch.putIfAbsent(Character.toLowerCase(type.charAt(0)) + type.substring(1),
						type + "?patient={{context.patientId}}");
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

		CarePlan carePlan = (CarePlan) engine.apply(planDefinitionId, patientId, resources, null);
		return cardMapper.toCards(carePlan, planDefinition, engine.getTerminologyVersions());
	}
}
