package org.snomed.cdsservice.model;

import org.hl7.fhir.r4.model.Coding;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

import static java.lang.String.format;

public abstract class CDSTrigger {

	private final String medicationLabel;
	private final Collection<Coding> medicationCodings;
	private final String conditionOrMedicationLabel;
	private final Collection<Coding> conditionOrMedicationCodings;
	private final CDSCard card;

	public CDSTrigger(String medicationLabel, Collection<Coding> medicationCodings, String conditionOrMedicationLabel, Collection<Coding> conditionOrMedicationCodings, CDSCard card) {
		this.medicationLabel = medicationLabel;
		this.medicationCodings = medicationCodings;
		this.conditionOrMedicationLabel = conditionOrMedicationLabel;
		this.conditionOrMedicationCodings = conditionOrMedicationCodings;
		this.card = card;
	}

	abstract String processTextTemplate(String text, Collection<Coding> conditionIntersection, Collection<Coding> medicationIntersection);

	public abstract CDSCard createRelevantCard(Set<Coding> activeDiagnosesOrMedicationCodings, Set<Coding> draftMedicationOrderCodings);

	public void addReferenceMedicationToCDSCard(Collection<Coding> medicationIntersection, CDSCard cardInstance) {
		CDSReference cdsReference = new CDSReference(medicationIntersection.stream().map(coding -> new CDSCoding(coding.getSystem(), coding.getCode())).collect(Collectors.toList()));
		if (cardInstance.getReferenceMedications() == null) {
			cardInstance.setReferenceMedications(new ArrayList<>());
		}
		cardInstance.getReferenceMedications().add(cdsReference);
	}

	public void addReferenceConditionToCDSCard(Collection<Coding> conditionIntersection, CDSCard cardInstance) {
		cardInstance.setReferenceCondition(new CDSReference(conditionIntersection.stream().map(coding -> new CDSCoding(coding.getSystem(), coding.getCode())).collect(Collectors.toList())));
	}

	public Collection<Coding> getIntersection(Collection<Coding> codingsA, Collection<Coding> codingsB) {
		return codingsA.stream().filter(codingA -> {
			for (Coding codingB : codingsB) {
				if (codingA.getSystem() != null && codingA.getSystem().equals(codingB.getSystem()) && codingA.getCode().equals(codingB.getCode())) {
					return true;
				}
			}
			return false;
		}).collect(Collectors.toList());
	}

	public String toHumanReadable(Collection<Coding> codings) {
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

	public String getMedicationLabel() {
		return medicationLabel;
	}

	public Collection<Coding> getMedicationCodings() {
		return medicationCodings;
	}

	public String getMedication2Label() {
		return conditionOrMedicationLabel;
	}

	public String getConditionLabel() {
		return conditionOrMedicationLabel;
	}

	public Collection<Coding> getConditionCodings() {
		return conditionOrMedicationCodings;
	}

	public Collection<Coding> getMedication2Codings() {
		return conditionOrMedicationCodings;
	}

	public CDSCard getCard() {
		return card;
	}
}
