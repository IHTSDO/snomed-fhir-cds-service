package org.snomed.cdsservice.model;

import org.hl7.fhir.r4.model.Coding;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
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
		List<CDSReference> cdsReferences = medicationIntersection.stream().map(coding -> new CDSCoding(coding.getSystem(), coding.getCode())).map(cdsCoding -> new CDSReference(Collections.singletonList(cdsCoding))).collect(Collectors.toList());
		if (cardInstance.getReferenceMedications() == null) {
			cardInstance.setReferenceMedications(new ArrayList<>());
		}
		if (cardInstance.getReferenceMedications().isEmpty()) {
			cardInstance.getReferenceMedications().addAll(cdsReferences);
			return;
		}
		List<String> codes  = new ArrayList<>();
		cardInstance.getReferenceMedications().forEach(cdsReference -> codes.addAll(cdsReference.getCoding().stream().map(CDSCoding::getCode).toList()));
		cardInstance.getReferenceMedications().addAll(cdsReferences.stream().filter(cdsReference -> !codes.contains(cdsReference.getCoding().get(0).getCode())).toList());
	}

	public void addReferenceConditionToCDSCard(Collection<Coding> conditionIntersection, CDSCard cardInstance) {
		List<CDSReference> cdsReferences = conditionIntersection.stream().map(coding -> new CDSCoding(coding.getSystem(), coding.getCode())).map(cdsCoding -> new CDSReference(Collections.singletonList(cdsCoding))).collect(Collectors.toList());
		if(cardInstance.getReferenceConditions() == null) {
			cardInstance.setReferenceConditions( new ArrayList<>());
		}
		if (cardInstance.getReferenceConditions().isEmpty()) {
			cardInstance.getReferenceConditions().addAll(cdsReferences);
			return;
		}
		List<String> codes  = new ArrayList<>();
		cardInstance.getReferenceConditions().forEach(cdsReference -> codes.addAll(cdsReference.getCoding().stream().map(CDSCoding::getCode).toList()));
		cardInstance.getReferenceConditions().addAll(cdsReferences.stream().filter(cdsReference -> !codes.contains(cdsReference.getCoding().get(0).getCode())).toList());
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
		Collection<String> codingsDisplay = codings.stream().map(Coding::getDisplay).collect(Collectors.toSet());
		if (codingsDisplay.isEmpty()) {
			return "";
		} else if (codingsDisplay.size() == 1) {
			return format("\"%s\"", codingsDisplay.iterator().next());
		} else {
			StringBuilder builder = new StringBuilder();
			for (String codingDisplay : codingsDisplay) {
				if (!builder.isEmpty()) {
					builder.append(" and ");
				}
				builder.append("\"");
				builder.append(codingDisplay);
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
