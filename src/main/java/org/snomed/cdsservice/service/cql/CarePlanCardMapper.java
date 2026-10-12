package org.snomed.cdsservice.service.cql;

import org.hl7.fhir.r4.model.CanonicalType;
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
import org.snomed.cdsservice.model.CDSLink;
import org.snomed.cdsservice.model.CDSReference;
import org.snomed.cdsservice.model.CDSSource;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
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
 * </ul>
 * Sources and links point at material a clinician can read: the guideline the rule documents, or, for content
 * published in a FHIR implementation guide (hosts in {@code publishedIgHosts}, e.g. {@code smart.who.int}), the IG
 * pages of the rule and of its CQL. A rule run as part of a strategy (e.g. a national immunization schedule) also
 * links to the strategy's documentation.
 * <p>
 * Every card carries a {@value #EXTENSION_KEY} extension recording which knowledge artifact (and strategy) produced it
 * and which terminology versions its value sets were expanded with, so a card can be traced to the logic that was
 * active.
 */
public class CarePlanCardMapper {

	static final String ALERT_TYPE = "Recommendation";
	static final String EXTENSION_KEY = "org.snomed.cds-service";
	private static final int MAX_SUMMARY_LENGTH = 140;

	private final Set<String> publishedIgHosts;

	public CarePlanCardMapper(Set<String> publishedIgHosts) {
		this.publishedIgHosts = Set.copyOf(publishedIgHosts);
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
	 * → {@code https://smart.who.int/immunizations/PlanDefinition-X.html}; only for hosts known to publish their IG at
	 * their canonical base.
	 */
	Optional<String> igPage(String canonical) {
		if (canonical == null) {
			return Optional.empty();
		}
		String url = canonical.split("\\|")[0];
		URI uri;
		try {
			uri = URI.create(url);
		} catch (IllegalArgumentException e) {
			return Optional.empty();
		}
		if (uri.getHost() == null || !publishedIgHosts.contains(uri.getHost())) {
			return Optional.empty();
		}
		String[] segments = uri.getPath().split("/");
		if (segments.length < 3) {
			return Optional.empty();
		}
		String type = segments[segments.length - 2];
		String id = segments[segments.length - 1];
		String base = String.join("/", Arrays.copyOf(segments, segments.length - 2));
		return Optional.of("https://" + uri.getHost() + base + "/" + type + "-" + id + ".html");
	}

	private static String canonical(PlanDefinition planDefinition) {
		return planDefinition.getUrl() + (planDefinition.hasVersion() ? "|" + planDefinition.getVersion() : "");
	}

	private String cardUuid(PlanDefinition planDefinition, Resource resource, String text) {
		String key = planDefinition.getUrl() + "|" + (resource == null ? "" : resource.getIdPart()) + "|" + text;
		return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
	}
}
