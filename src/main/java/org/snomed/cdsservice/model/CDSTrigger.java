package org.snomed.cdsservice.model;

import org.hl7.fhir.r4.model.Coding;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

import static java.lang.String.format;

public class CDSTrigger {

	private final String medicationLabel;
	private final Collection<Coding> medicationCodings;
	private final String conditionLabel;
	private final Collection<Coding> conditionCodings;
	private final CDSCard card;

	public CDSTrigger(String medicationLabel, Collection<Coding> medicationCodings, String conditionLabel, Collection<Coding> conditionCodings, CDSCard card) {
		this.medicationLabel = medicationLabel;
		this.medicationCodings = medicationCodings;
		this.conditionLabel = conditionLabel;
		this.conditionCodings = conditionCodings;
		this.card = card;
	}

	public CDSCard createRelevantCard(Set<Coding> activeDiagnosesCodings, Set<Coding> draftMedicationOrderCodings) {
		Collection<Coding> conditionIntersection = getIntersection(activeDiagnosesCodings, conditionCodings);
		Collection<Coding> medicationIntersection = getIntersection(draftMedicationOrderCodings, medicationCodings);
		if (!conditionIntersection.isEmpty() && !medicationIntersection.isEmpty()) {
			CDSCard cardInstance = card.cloneCard();

			cardInstance.setSummary(processTextTemplate(cardInstance.getSummary(), conditionIntersection, medicationIntersection));
			cardInstance.setDetail(processTextTemplate(cardInstance.getDetail(), conditionIntersection, medicationIntersection));

			return cardInstance;
		} else {
			return null;
		}
	}

	private String processTextTemplate(String text, Collection<Coding> conditionIntersection, Collection<Coding> medicationIntersection) {
		if (text == null) {
			return null;
		}
		text = text.replace("{{RuleMedication}}", medicationLabel);
		text = text.replace("{{ActualMedication}}", toHumanReadable(medicationIntersection));
		text = text.replace("{{RuleCondition}}", conditionLabel);
		text = text.replace("{{ActualCondition}}", toHumanReadable(conditionIntersection));
		return text;
	}

	private Collection<Coding> getIntersection(Collection<Coding> codingsA, Collection<Coding> codingsB) {
		return codingsA.stream().filter(codingA -> {
			for (Coding codingB : codingsB) {
				if (codingA.getSystem() != null && codingA.getSystem().equals(codingB.getSystem()) && codingA.getCode().equals(codingB.getCode())) {
					return true;
				}
			}
			return false;
		}).collect(Collectors.toList());
	}

	private String toHumanReadable(Collection<Coding> codings) {
		if (codings.isEmpty()) {
			return "";
		} else if (codings.size() == 1) {
			return format("\"%s\"", codings.iterator().next().getDisplay());
		} else {
			StringBuilder builder = new StringBuilder();
			for (Coding coding : codings) {
				if (!builder.isEmpty()) {
					builder.append(" and ");
				}
				builder.append("\"");
				builder.append(coding.getDisplay());
				builder.append("\"");
			}
			return builder.toString();
		}
	}

}
