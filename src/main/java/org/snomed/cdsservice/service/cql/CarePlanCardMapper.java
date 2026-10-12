package org.snomed.cdsservice.service.cql;

import org.hl7.fhir.r4.model.CarePlan;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.CommunicationRequest;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.PlanDefinition;
import org.hl7.fhir.r4.model.RelatedArtifact;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.snomed.cdsservice.model.CDSCard;
import org.snomed.cdsservice.model.CDSCoding;
import org.snomed.cdsservice.model.CDSIndicator;
import org.snomed.cdsservice.model.CDSReference;
import org.snomed.cdsservice.model.CDSSource;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Turns the result of {@code PlanDefinition/$apply} (a CarePlan whose contained resources are the RequestGroup and
 * the requests it generated) into CDS Hooks cards.
 * <ul>
 *     <li>Each {@link CommunicationRequest} (the guidance for the clinician) becomes a card: the first line of the
 *     message is the summary, the full message is the detail, and the request priority sets the indicator.</li>
 *     <li>Proposed requests ({@link MedicationRequest}, {@link ServiceRequest}) are attached to those cards as
 *     references. If there is no guidance, each proposal gets a card of its own.</li>
 * </ul>
 * Every card carries a {@value #EXTENSION_KEY} extension recording which knowledge artifact produced it and which
 * terminology versions its value sets were expanded with, so a card can be traced to the logic that was active.
 */
public class CarePlanCardMapper {

	static final String ALERT_TYPE = "Recommendation";
	static final String EXTENSION_KEY = "org.snomed.cds-service";
	private static final int MAX_SUMMARY_LENGTH = 140;

	public List<CDSCard> toCards(CarePlan carePlan, PlanDefinition planDefinition, Set<String> terminologyVersions) {
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
						indicator(communicationRequest.getPriority()), source,
						proposals.isEmpty() ? null : proposals, null, ALERT_TYPE));
			}
		}
		if (cards.isEmpty()) {
			for (int i = 0; i < proposals.size(); i++) {
				String summary = summary("Proposed: " + proposalLabels.get(i));
				cards.add(new CDSCard(cardUuid(planDefinition, null, summary), summary, null, CDSIndicator.info, source,
						List.of(proposals.get(i)), null, ALERT_TYPE));
			}
		}
		Map<String, Object> provenance = new LinkedHashMap<>();
		provenance.put("knowledgeArtifact", planDefinition.getUrl() + (planDefinition.hasVersion() ? "|" + planDefinition.getVersion() : ""));
		if (!terminologyVersions.isEmpty()) {
			provenance.put("terminologyVersions", List.copyOf(terminologyVersions));
		}
		cards.forEach(card -> card.setExtension(Map.of(EXTENSION_KEY, provenance)));
		return cards;
	}

	/** The engine can add the same request twice with the same id; serialization would collapse them too. */
	private List<Resource> distinctById(List<Resource> resources) {
		Map<String, Resource> byId = new LinkedHashMap<>();
		for (Resource resource : resources) {
			byId.putIfAbsent(resource.fhirType() + "/" + resource.getIdPart(), resource);
		}
		return new ArrayList<>(byId.values());
	}

	private CDSIndicator indicator(CommunicationRequest.CommunicationPriority priority) {
		if (priority == null) {
			return CDSIndicator.info;
		}
		return switch (priority) {
			case URGENT, ASAP -> CDSIndicator.warning;
			case STAT -> CDSIndicator.critical;
			default -> CDSIndicator.info;
		};
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

	/** The guideline the PlanDefinition documents itself with, falling back to the PlanDefinition itself. */
	private CDSSource source(PlanDefinition planDefinition) {
		return planDefinition.getRelatedArtifact().stream()
				.filter(artifact -> artifact.getType() == RelatedArtifact.RelatedArtifactType.DOCUMENTATION && artifact.hasLabel())
				.findFirst()
				.map(artifact -> new CDSSource(artifact.getLabel(), artifact.hasUrl() ? artifact.getUrl() : planDefinition.getUrl()))
				.orElseGet(() -> new CDSSource(planDefinition.hasTitle() ? planDefinition.getTitle() : planDefinition.getName(), planDefinition.getUrl()));
	}

	private String cardUuid(PlanDefinition planDefinition, Resource resource, String text) {
		String key = planDefinition.getUrl() + "|" + (resource == null ? "" : resource.getIdPart()) + "|" + text;
		return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
	}
}
