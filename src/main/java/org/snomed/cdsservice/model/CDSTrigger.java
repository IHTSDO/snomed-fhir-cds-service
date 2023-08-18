package org.snomed.cdsservice.model;

import org.hl7.fhir.r4.model.Coding;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

import static java.lang.String.format;

public class CDSTrigger {

	private final String medicationLabel;
	private final Collection<Coding> medicationCodings;
	private final String conditionOrMedicationLabel;
	private final Collection<Coding> conditionOrMedicationCodings;
	private final CDSCard card;

	private final CDSTriggerType cdsTriggerType;

	public CDSTrigger(String medicationLabel, Collection<Coding> medicationCodings, String conditionOrMedicationLabel, Collection<Coding> conditionOrMedicationCodings, CDSCard card, CDSTriggerType cdsTriggerType) {
		this.medicationLabel = medicationLabel;
		this.medicationCodings = medicationCodings;
		this.conditionOrMedicationLabel = conditionOrMedicationLabel;
		this.conditionOrMedicationCodings = conditionOrMedicationCodings;
		this.card = card;
		this.cdsTriggerType = cdsTriggerType;
	}

	public CDSCard createRelevantCard(Set<Coding> activeDiagnosesOrMedicationCodings, Set<Coding> draftMedicationOrderCodings) {
		Collection<Coding> conditionOrMedicationIntersection = getIntersection(activeDiagnosesOrMedicationCodings, conditionOrMedicationCodings);
		Collection<Coding> medicationIntersection = getIntersection(draftMedicationOrderCodings, medicationCodings);
		if (!conditionOrMedicationIntersection.isEmpty() && !medicationIntersection.isEmpty()) {
			CDSCard cardInstance = card.cloneCard();

			cardInstance.setSummary(processTextTemplate(cardInstance.getSummary(), conditionOrMedicationIntersection, medicationIntersection));
			cardInstance.setDetail(processTextTemplate(cardInstance.getDetail(), conditionOrMedicationIntersection, medicationIntersection));
			addReferenceMedicationToCDSCard(medicationIntersection, cardInstance);
			if (CDSTriggerType.DRUG_DIAGNOSIS.equals(cdsTriggerType)) {
				addReferenceConditionToCDSCard(conditionOrMedicationIntersection, cardInstance);
			} else if (CDSTriggerType.DRUG_DRUG.equals(cdsTriggerType)) {
				addReferenceMedicationToCDSCard(conditionOrMedicationIntersection, cardInstance);
			}

			return cardInstance;
		} else {
			return null;
		}
	}

	private void addReferenceMedicationToCDSCard(Collection<Coding> medicationIntersection, CDSCard cardInstance) {
		CDSReference cdsReference = new CDSReference(medicationIntersection.stream().map(coding -> new CDSCoding(coding.getSystem(), coding.getCode())).collect(Collectors.toList()));
		if (cardInstance.getReferenceMedications() == null) {
			cardInstance.setReferenceMedications(new ArrayList<>());
		}
		cardInstance.getReferenceMedications().add(cdsReference);
	}

	private void addReferenceConditionToCDSCard(Collection<Coding> conditionIntersection, CDSCard cardInstance) {
		cardInstance.setReferenceCondition(new CDSReference(conditionIntersection.stream().map(coding -> new CDSCoding(coding.getSystem(), coding.getCode())).collect(Collectors.toList())));
	}

	private String processTextTemplate(String text, Collection<Coding> conditionIntersection, Collection<Coding> medicationIntersection) {
		if (text == null) {
			return null;
		}
		if (cdsTriggerType.equals(CDSTriggerType.DRUG_DIAGNOSIS)) {
			text = text.replace("{{RuleMedication}}", medicationLabel);
			text = text.replace("{{ActualMedication}}", toHumanReadable(medicationIntersection));
			text = text.replace("{{RuleCondition}}", conditionOrMedicationLabel);
			text = text.replace("{{ActualCondition}}", toHumanReadable(conditionIntersection));
		} else if (cdsTriggerType.equals(CDSTriggerType.DRUG_DRUG)) {
			text = text.replace("{{RuleMedication1}}", medicationLabel);
			text = text.replace("{{ActualMedication1}}", toHumanReadable(medicationIntersection));
			text = text.replace("{{RuleMedication2}}", conditionOrMedicationLabel);
			text = text.replace("{{ActualMedication2}}", toHumanReadable(conditionIntersection));
		}

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
