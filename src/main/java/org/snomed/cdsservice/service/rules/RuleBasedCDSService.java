package org.snomed.cdsservice.service.rules;

import ca.uhn.fhir.context.FhirContext;
import jakarta.annotation.PostConstruct;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.cdsservice.model.CDSCard;
import org.snomed.cdsservice.model.rules.Rule;
import org.snomed.cdsservice.model.rules.RuleEvaluationResult;
import org.snomed.cdsservice.model.rules.RuleSet;
import org.snomed.cdsservice.service.CDSService;
import org.snomed.cdsservice.service.ServiceException;
import org.snomed.cdsservice.service.rules.evaluator.RuleEvaluationContext;
import org.snomed.cdsservice.rest.pojo.CDSRequest;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A data-driven CDS service backed by one diagnostic {@link RuleSet}. One instance is registered per
 * {@code service_id} discovered in the rules directory, so adding a diagnostic domain requires no code.
 * <p>
 * On startup it loads its rule set and warms up any terminology expansions (failing fast if a required
 * expansion cannot be resolved). On each request it parses the prefetched FHIR resources, evaluates every
 * enabled rule with the shared {@link RuleEngine}, suppresses rules whose outcome the patient already
 * carries, and returns a card per fired rule.
 */
public class RuleBasedCDSService extends CDSService {

	private static final String SNOMED_SYSTEM = "http://snomed.info/sct";

	private final String domain;
	private final String criteriaTsvPath;
	private final String rulesTsvPath;
	private final String hook;

	@Autowired
	private RuleLoaderService ruleLoaderService;
	@Autowired
	private EclCodeResolver codeResolver;
	@Autowired
	private RuleEngine ruleEngine;
	@Autowired
	private RuleCardFactory ruleCardFactory;
	@Autowired
	private FhirContext fhirContext;

	private RuleSet ruleSet;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public RuleBasedCDSService(String serviceId, String domain, String criteriaTsvPath, String rulesTsvPath, String hook) {
		super(serviceId);
		this.domain = domain;
		this.criteriaTsvPath = criteriaTsvPath;
		this.rulesTsvPath = rulesTsvPath;
		this.hook = hook;
	}

	@PostConstruct
	public void init() {
		try {
			ruleSet = ruleLoaderService.loadRuleSet(domain, criteriaTsvPath, rulesTsvPath);
			codeResolver.warmUp(ruleSet.criteria().values());
		} catch (ServiceException e) {
			throw new IllegalStateException("Failed to initialise diagnostic CDS service '%s'.".formatted(getId()), e);
		}
		setHook(hook);
		setTitle("Diagnostic decision support (%s)".formatted(domain));
		setDescription("SNOMED CT driven diagnostic decision support for %s, evaluated from editable TSV rules.".formatted(domain));
		setPrefetch(Map.of(
				"patient", "Patient/{{context.patientId}}",
				"conditions", "Condition?patient={{context.patientId}}",
				"observations", "Observation?patient={{context.patientId}}"));
		logger.info("Registered diagnostic CDS service '{}' ({} enabled rules).", getId(), ruleSet.enabledRules().size());
	}

	@Override
	public List<CDSCard> call(CDSRequest cdsRequest) {
		List<Resource> resources = parsePrefetchResources(cdsRequest);
		Patient patient = resources.stream().filter(Patient.class::isInstance).map(Patient.class::cast).findFirst().orElse(null);
		Map<String, Object> hookContext = cdsRequest.getContext() == null ? Map.of() : cdsRequest.getContext();
		RuleEvaluationContext context = new RuleEvaluationContext(patient, resources, hookContext);

		logRequest(resources, hookContext);

		List<CDSCard> cards = new ArrayList<>();
		for (Rule rule : ruleSet.enabledRules()) {
			RuleEvaluationResult result = ruleEngine.evaluateRule(rule, ruleSet.criteria(), context);
			if (result.fired()) {
				boolean suppressed = isSuppressed(rule, resources);
				logger.debug("[{}] rule '{}' FIRED (outcome {}), suppressed={}", getId(), rule.ruleId(), rule.outcomeCode(), suppressed);
				if (!suppressed) {
					cards.add(ruleCardFactory.createCard(result));
				}
			}
		}
		return cards;
	}

	/** Debug dump of what the service received, to diagnose prefetch/suppression issues. Enable with
	 * logging.level.org.snomed.cdsservice.service.rules=DEBUG. */
	private void logRequest(List<Resource> resources, Map<String, Object> hookContext) {
		if (!logger.isDebugEnabled()) {
			return;
		}
		Map<String, Long> byType = new java.util.TreeMap<>();
		for (Resource resource : resources) {
			byType.merge(resource.getResourceType().name(), 1L, Long::sum);
		}
		logger.debug("[{}] received {} resource(s) {}; context keys {}", getId(), resources.size(), byType, hookContext.keySet());
		for (Resource resource : resources) {
			if (resource instanceof Condition condition) {
				String codes = condition.getCode().getCoding().stream()
						.map(c -> c.getSystem() + "|" + c.getCode()).collect(java.util.stream.Collectors.joining(", "));
				String clinical = condition.hasClinicalStatus() ? condition.getClinicalStatus().getCoding().stream()
						.map(Coding::getCode).collect(java.util.stream.Collectors.joining(",")) : "(none)";
				String verification = condition.hasVerificationStatus() ? condition.getVerificationStatus().getCoding().stream()
						.map(Coding::getCode).collect(java.util.stream.Collectors.joining(",")) : "(none)";
				logger.debug("[{}]   Condition codes=[{}] clinicalStatus=[{}] verificationStatus=[{}] relevantForSuppression={}",
						getId(), codes, clinical, verification, isActive(condition));
			}
		}
	}

	private List<Resource> parsePrefetchResources(CDSRequest cdsRequest) {
		List<Resource> resources = new ArrayList<>();
		Map<String, String> prefetchStrings = cdsRequest.getPrefetchStrings();
		if (prefetchStrings == null) {
			return resources;
		}
		for (String json : prefetchStrings.values()) {
			if (json == null || json.isBlank() || json.equals("null")) {
				continue;
			}
			IBaseResource parsed = fhirContext.newJsonParser().parseResource(json);
			if (parsed instanceof Bundle bundle) {
				for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
					if (entry.hasResource()) {
						resources.add(entry.getResource());
					}
				}
			} else if (parsed instanceof Resource resource) {
				resources.add(resource);
			}
		}
		return resources;
	}

	/**
	 * Suppresses a fired rule when the patient already carries the rule's outcome as an active condition.
	 * When the outcome is a SNOMED concept and a terminology server is available, matching is
	 * subtype-aware ({@code << outcome_code}), so a recorded <em>subtype</em> of the proposed diagnosis
	 * also suppresses the recommendation; otherwise it falls back to exact
	 * {@code outcome_code_system + outcome_code} matching. Entered-in-error or inactive conditions never
	 * suppress a current recommendation.
	 */
	private boolean isSuppressed(Rule rule, List<Resource> resources) {
		if (!rule.suppressIfOutcomePresent() || rule.outcomeCode() == null || rule.outcomeCode().isBlank()) {
			return false;
		}
		Set<CodeKey> suppressingCodes = suppressionCodes(rule);
		for (Resource resource : resources) {
			if (resource instanceof Condition condition && isActive(condition) && hasAnyCode(condition.getCode(), suppressingCodes)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * The codes whose presence as an active condition suppresses the rule: the outcome concept plus, when
	 * it is a SNOMED concept and a terminology server can resolve it, all of its subtypes. Expansion is
	 * cached; if it cannot be resolved the set is just the exact outcome code.
	 */
	private Set<CodeKey> suppressionCodes(Rule rule) {
		CodeKey exact = new CodeKey(rule.outcomeCodeSystem(), rule.outcomeCode());
		if (SNOMED_SYSTEM.equals(rule.outcomeCodeSystem())) {
			return codeResolver.tryExpand("<< " + rule.outcomeCode()).orElseGet(() -> Set.of(exact));
		}
		return Set.of(exact);
	}

	private boolean hasAnyCode(CodeableConcept concept, Set<CodeKey> codes) {
		if (concept == null) {
			return false;
		}
		for (Coding coding : concept.getCoding()) {
			if (codes.contains(new CodeKey(coding.getSystem(), coding.getCode()))) {
				return true;
			}
		}
		return false;
	}

	private boolean isActive(Condition condition) {
		if (condition.hasVerificationStatus() && condition.getVerificationStatus().getCoding().stream()
				.anyMatch(c -> "entered-in-error".equalsIgnoreCase(c.getCode()))) {
			return false;
		}
		if (!condition.hasClinicalStatus() || condition.getClinicalStatus().getCoding().isEmpty()) {
			return true;
		}
		return condition.getClinicalStatus().getCoding().stream().anyMatch(c -> {
			String code = c.getCode();
			return "active".equalsIgnoreCase(code) || "recurrence".equalsIgnoreCase(code) || "relapse".equalsIgnoreCase(code);
		});
	}

	public RuleSet getRuleSet() {
		return ruleSet;
	}
}
