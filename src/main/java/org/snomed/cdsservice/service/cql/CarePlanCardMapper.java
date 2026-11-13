package org.snomed.cdsservice.service.cql;

import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.CarePlan;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.CommunicationRequest;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.PlanDefinition;
import org.hl7.fhir.r4.model.RelatedArtifact;
import org.hl7.fhir.r4.model.RequestGroup;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.snomed.cdsservice.model.CDSCard;
import org.snomed.cdsservice.model.CDSCoding;
import org.snomed.cdsservice.model.CDSIndicator;
import org.snomed.cdsservice.model.CDSLink;
import org.snomed.cdsservice.model.CDSReference;
import org.snomed.cdsservice.model.CDSSource;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Turns the result of {@code PlanDefinition/$apply} (a CarePlan whose contained resources are the RequestGroup and
 * the requests it generated) into CDS Hooks cards.
 * <ul>
 *     <li>Each {@link CommunicationRequest} (the guidance for the clinician) becomes a card: the first line of the
 *     message is the summary, the full message is the detail, and the request priority sets the indicator. A card
 *     that proposes an order is actionable and is raised to at least {@code warning}, so the EHR can tell "vaccinate
 *     now" from "not due yet" (WHO tables give both {@code routine} priority).</li>
 *     <li>Proposed requests ({@link MedicationRequest}, {@link ServiceRequest}) are attached to those cards as
 *     references. If there is no guidance, each proposal gets a card of its own.</li>
 *     <li>Rules whose actions create no resources (e.g. the WHO SMART ANC decision tables, whose actions are just
 *     "Refer urgently to a hospital", "Conduct HIV test") get a card per applicable action: the action title is the
 *     summary; the detail is the action's guidance ({@code textEquivalent}), the decision-table logic that was met
 *     (the condition's description) and its references. Decisions with the same title are merged, and a decision with
 *     several outcomes (sub-actions, e.g. "Provide counselling and treatment") lists them. The engine keeps an action
 *     whose applicability condition evaluated to null (missing data), flagged with the CPG extension
 *     {@value #CONDITION_RESULT_URL}; such actions did not apply and get no card.</li>
 * </ul>
 * Sources and links point at material a clinician can read: the guideline the rule documents, or, for content
 * published in a FHIR implementation guide ({@code igPageBases}: canonical base → page base), the IG pages of the
 * rule and of its CQL. A rule run as part of a strategy (e.g. a national immunization schedule) also links to the
 * strategy's documentation.
 * <p>
 * Every card carries a {@value #EXTENSION_KEY} extension recording which knowledge artifact (and strategy) produced it
 * and which terminology versions its value sets were expanded with, so a card can be traced to the logic that was
 * active.
 */
public class CarePlanCardMapper {

	static final String ALERT_TYPE = "Recommendation";
	static final String EXTENSION_KEY = "org.snomed.cds-service";
	static final String CONDITION_RESULT_URL = "http://hl7.org/fhir/uv/cpg/StructureDefinition/cpg-action-condition-result";
	private static final int MAX_SUMMARY_LENGTH = 140;

	// Canonical base (e.g. http://smart.who.int/anc) → base URL of its published IG pages.
	private final Map<String, String> igPageBases;

	public CarePlanCardMapper(Map<String, String> igPageBases) {
		this.igPageBases = Map.copyOf(igPageBases);
	}

	/** Parses {@code canonicalBase=pageBase} entries, e.g. from a comma-separated property. */
	public static Map<String, String> parseIgPageBases(Collection<String> entries) {
		Map<String, String> bases = new LinkedHashMap<>();
		for (String entry : entries) {
			int equals = entry.indexOf('=');
			if (equals > 0) {
				bases.put(trimSlash(entry.substring(0, equals).strip()), trimSlash(entry.substring(equals + 1).strip()));
			}
		}
		return bases;
	}

	private static String trimSlash(String url) {
		return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
	}

	/**
	 * @param planDefinition the rule whose {@code $apply} produced the CarePlan
	 * @param strategy       the PlanDefinition that composed the rule (e.g. a national schedule), or null
	 */
	public List<CDSCard> toCards(CarePlan carePlan, PlanDefinition planDefinition, PlanDefinition strategy, Set<String> terminologyVersions) {
		List<Resource> contained = distinctById(carePlan.getContained());

		List<CDSReference> proposals = new ArrayList<>();
		List<String> proposalLabels = new ArrayList<>();
		for (Resource resource : contained) {
			CodeableConcept proposed = null;
			if (resource instanceof MedicationRequest medicationRequest && medicationRequest.hasMedicationCodeableConcept()) {
				proposed = medicationRequest.getMedicationCodeableConcept();
			} else if (resource instanceof ServiceRequest serviceRequest && serviceRequest.hasCode()) {
				proposed = serviceRequest.getCode();
			}
			if (proposed != null) {
				proposals.add(new CDSReference(proposed.getCoding().stream()
						.map(coding -> new CDSCoding(coding.getSystem(), coding.getCode(), coding.getDisplay())).toList()));
				proposalLabels.add(label(proposed));
			}
		}

		CDSSource source = source(planDefinition);
		List<CDSCard> cards = new ArrayList<>();
		for (Resource resource : contained) {
			if (resource instanceof CommunicationRequest communicationRequest) {
				String message = communicationRequest.getPayload().stream()
						.filter(CommunicationRequest.CommunicationRequestPayloadComponent::hasContentStringType)
						.map(payload -> payload.getContentStringType().getValue())
						.findFirst().orElse(null);
				if (message == null || message.isBlank()) {
					continue;
				}
				String detail = proposalLabels.isEmpty() ? message : message + "\n\n**Proposed:** " + String.join(", ", proposalLabels);
				cards.add(new CDSCard(cardUuid(planDefinition, resource, message), summary(message), detail,
						indicator(communicationRequest.getPriority(), !proposals.isEmpty()), source,
						proposals.isEmpty() ? null : proposals, null, ALERT_TYPE));
			}
		}
		if (cards.isEmpty() && proposals.isEmpty()) {
			cards.addAll(actionCards(contained, planDefinition, source));
		}
		if (cards.isEmpty()) {
			for (int i = 0; i < proposals.size(); i++) {
				String summary = summary("Proposed: " + proposalLabels.get(i));
				cards.add(new CDSCard(cardUuid(planDefinition, null, summary), summary, null, CDSIndicator.info, source,
						List.of(proposals.get(i)), null, ALERT_TYPE));
			}
		}
		Map<String, Object> provenance = new LinkedHashMap<>();
		provenance.put("knowledgeArtifact", canonical(planDefinition));
		if (strategy != null) {
			provenance.put("strategy", canonical(strategy));
		}
		if (!terminologyVersions.isEmpty()) {
			provenance.put("terminologyVersions", List.copyOf(terminologyVersions));
		}
		List<CDSLink> links = links(planDefinition, strategy, source);
		cards.forEach(card -> {
			card.setExtension(Map.of(EXTENSION_KEY, provenance));
			if (!links.isEmpty()) {
				card.setLinks(links);
			}
		});
		return cards;
	}

	/**
	 * One card per applicable decision (an action with an applicability condition), merging decisions with the same
	 * title. A decision with several outcomes lists its sub-actions as the actions to take.
	 */
	private List<CDSCard> actionCards(List<Resource> contained, PlanDefinition planDefinition, CDSSource source) {
		Map<String, List<RequestGroup.RequestGroupActionComponent>> byTitle = new LinkedHashMap<>();
		for (Resource resource : contained) {
			if (resource instanceof RequestGroup requestGroup) {
				collectDecisions(requestGroup.getAction(), byTitle);
			}
		}
		List<CDSCard> cards = new ArrayList<>();
		for (Map.Entry<String, List<RequestGroup.RequestGroupActionComponent>> entry : byTitle.entrySet()) {
			List<RequestGroup.RequestGroupActionComponent> decisions = entry.getValue();
			List<RequestGroup.RequestGroupActionComponent> actions = new ArrayList<>();
			decisions.forEach(decision -> collectSubtree(decision, actions));
			StringBuilder detail = new StringBuilder();
			actions.stream().map(RequestGroup.RequestGroupActionComponent::getTextEquivalent)
					.filter(text -> text != null && !text.isBlank()).map(String::strip).distinct()
					.forEach(text -> detail.append(text).append("\n\n"));
			List<String> outcomes = actions.stream().filter(action -> !action.hasAction() && action.hasTitle())
					.map(action -> action.getTitle().strip()).filter(title -> !title.equals(entry.getKey())).distinct().toList();
			if (!outcomes.isEmpty()) {
				detail.append("**Actions:**\n");
				outcomes.forEach(outcome -> detail.append("- ").append(outcome).append('\n'));
				detail.append('\n');
			}
			List<String> reasons = decisions.stream().flatMap(action -> action.getCondition().stream())
					.map(condition -> condition.getExpression().getDescription())
					.filter(reason -> reason != null && !reason.isBlank()).map(String::strip).distinct().toList();
			if (!reasons.isEmpty()) {
				detail.append("**Why:** ").append(String.join("; or ", reasons)).append("\n\n");
			}
			List<String> references = actions.stream().flatMap(action -> action.getDocumentation().stream())
					.map(documentation -> documentation.hasLabel() ? documentation.getLabel() : documentation.getDisplay())
					.filter(label -> label != null && !label.isBlank()).map(String::strip).distinct().toList();
			if (!references.isEmpty()) {
				detail.append("**Reference:** ").append(String.join("; ", references));
			}
			CDSIndicator indicator = actions.stream().map(action -> indicator(action.getPriority()))
					.max(Comparator.naturalOrder()).orElse(CDSIndicator.info);
			String summary = summary(entry.getKey());
			cards.add(new CDSCard(cardUuid(planDefinition, null, summary), summary, detail.toString().strip(), indicator, source,
					null, null, ALERT_TYPE));
		}
		return cards;
	}

	/**
	 * The applicable decisions, in order, grouped by title: actions with a condition that was met and no resource.
	 * Groups without conditions are descended into; a decision whose condition was not met (or unknown) is dropped
	 * with its sub-actions, and so are groups left empty and actions without a condition.
	 */
	private void collectDecisions(List<RequestGroup.RequestGroupActionComponent> actions,
			Map<String, List<RequestGroup.RequestGroupActionComponent>> byTitle) {
		for (RequestGroup.RequestGroupActionComponent action : actions) {
			if (action == null) {
				// The engine can leave null entries in a RequestGroup's actions.
				continue;
			}
			if (action.hasCondition()) {
				if (conditionsMet(action) && !action.hasResource() && action.hasTitle() && !action.getTitle().isBlank()) {
					byTitle.computeIfAbsent(action.getTitle().strip(), title -> new ArrayList<>()).add(action);
				}
			} else if (action.hasAction()) {
				collectDecisions(action.getAction(), byTitle);
			}
		}
	}

	private static void collectSubtree(RequestGroup.RequestGroupActionComponent action, List<RequestGroup.RequestGroupActionComponent> into) {
		into.add(action);
		action.getAction().stream().filter(Objects::nonNull).forEach(child -> collectSubtree(child, into));
	}

	/**
	 * False when the engine flagged a condition's result as false or unknown (CPG {@code cpg-action-condition-result}
	 * with a data-absent-reason: the expression evaluated to null).
	 */
	static boolean conditionsMet(RequestGroup.RequestGroupActionComponent action) {
		for (RequestGroup.RequestGroupActionConditionComponent condition : action.getCondition()) {
			Extension result = condition.getExtensionByUrl(CONDITION_RESULT_URL);
			if (result != null && !(result.getValue() instanceof BooleanType value && value.booleanValue())) {
				return false;
			}
		}
		return true;
	}

	private CDSIndicator indicator(RequestGroup.RequestPriority priority) {
		if (priority == null) {
			return CDSIndicator.info;
		}
		return switch (priority) {
			case URGENT, ASAP -> CDSIndicator.warning;
			case STAT -> CDSIndicator.critical;
			default -> CDSIndicator.info;
		};
	}

	/**
	 * Drops repeated copies of the same request. The engine can add a request twice with the same id; and different
	 * requests built from the same ActivityDefinition can share an id too (e.g. the bOPV and IPV proposals of the WHO
	 * polio table), so only content-identical resources are treated as duplicates.
	 */
	static List<Resource> distinctById(List<Resource> resources) {
		List<Resource> distinct = new ArrayList<>();
		for (Resource resource : resources) {
			boolean duplicate = distinct.stream().anyMatch(kept -> kept.fhirType().equals(resource.fhirType())
					&& Objects.equals(kept.getIdPart(), resource.getIdPart()) && kept.equalsDeep(resource));
			if (!duplicate) {
				distinct.add(resource);
			}
		}
		return distinct;
	}

	private CDSIndicator indicator(CommunicationRequest.CommunicationPriority priority, boolean proposesOrder) {
		CDSIndicator fromPriority = priority == null ? CDSIndicator.info : switch (priority) {
			case URGENT, ASAP -> CDSIndicator.warning;
			case STAT -> CDSIndicator.critical;
			default -> CDSIndicator.info;
		};
		return proposesOrder && fromPriority == CDSIndicator.info ? CDSIndicator.warning : fromPriority;
	}

	private String summary(String message) {
		String firstLine = message.strip().lines().findFirst().orElse("").strip();
		return firstLine.length() <= MAX_SUMMARY_LENGTH ? firstLine : firstLine.substring(0, MAX_SUMMARY_LENGTH - 1) + "…";
	}

	private String label(CodeableConcept concept) {
		if (concept.hasText()) {
			return concept.getText();
		}
		return concept.getCoding().stream().filter(coding -> coding.hasDisplay()).map(coding -> coding.getDisplay())
				.findFirst().orElse(concept.getCodingFirstRep().getCode());
	}

	/**
	 * The guideline the PlanDefinition documents itself with; otherwise the PlanDefinition's page in its published
	 * implementation guide; otherwise its canonical URL.
	 */
	private CDSSource source(PlanDefinition planDefinition) {
		String title = planDefinition.hasTitle() ? planDefinition.getTitle() : planDefinition.getName();
		return planDefinition.getRelatedArtifact().stream()
				.filter(artifact -> artifact.getType() == RelatedArtifact.RelatedArtifactType.DOCUMENTATION && artifact.hasLabel())
				.findFirst()
				.map(artifact -> new CDSSource(artifact.getLabel(),
						artifact.hasUrl() ? artifact.getUrl() : igPage(planDefinition.getUrl()).orElse(planDefinition.getUrl())))
				.orElseGet(() -> new CDSSource(title, igPage(planDefinition.getUrl()).orElse(planDefinition.getUrl())));
	}

	/** Links to the rule's IG page (when not already the source), its CQL libraries' IG pages, and the strategy's documentation. */
	private List<CDSLink> links(PlanDefinition planDefinition, PlanDefinition strategy, CDSSource source) {
		List<CDSLink> links = new ArrayList<>();
		igPage(planDefinition.getUrl())
				.filter(page -> !page.equals(source.getUrl()))
				.ifPresent(page -> links.add(new CDSLink("Rule in the implementation guide", page)));
		for (CanonicalType library : planDefinition.getLibrary()) {
			String url = library.getValue();
			igPage(url).ifPresent(page -> links.add(new CDSLink("Decision logic (CQL): " + url.substring(url.lastIndexOf('/') + 1).split("\\|")[0], page)));
		}
		if (strategy != null) {
			strategy.getRelatedArtifact().stream()
					.filter(artifact -> artifact.getType() == RelatedArtifact.RelatedArtifactType.DOCUMENTATION && artifact.hasUrl())
					.forEach(artifact -> links.add(new CDSLink(artifact.hasLabel() ? artifact.getLabel() : "Schedule documentation", artifact.getUrl())));
		}
		return links;
	}

	/**
	 * The page an IG Publisher build renders for a canonical, e.g. {@code http://smart.who.int/immunizations/PlanDefinition/X}
	 * → {@code https://smart.who.int/immunizations/PlanDefinition-X.html}; only for canonical bases with known
	 * published pages.
	 */
	Optional<String> igPage(String canonical) {
		if (canonical == null) {
			return Optional.empty();
		}
		String url = canonical.split("\\|")[0];
		for (Map.Entry<String, String> base : igPageBases.entrySet()) {
			if (url.startsWith(base.getKey() + "/")) {
				String[] typeAndId = url.substring(base.getKey().length() + 1).split("/");
				if (typeAndId.length == 2) {
					return Optional.of(base.getValue() + "/" + typeAndId[0] + "-" + typeAndId[1] + ".html");
				}
			}
		}
		return Optional.empty();
	}

	private static String canonical(PlanDefinition planDefinition) {
		return planDefinition.getUrl() + (planDefinition.hasVersion() ? "|" + planDefinition.getVersion() : "");
	}

	private String cardUuid(PlanDefinition planDefinition, Resource resource, String text) {
		String key = planDefinition.getUrl() + "|" + (resource == null ? "" : resource.getIdPart()) + "|" + text;
		return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
	}
}
