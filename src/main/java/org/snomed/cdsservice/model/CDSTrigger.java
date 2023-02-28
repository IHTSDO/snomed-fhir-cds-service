package org.snomed.cdsservice.model;

import org.hl7.fhir.r4.model.Coding;

import java.util.Collection;

public class CDSTrigger {

	private final Collection<Coding> drugCodings;
	private final Collection<Coding> findingCodings;
	private final CDSCard card;

	public CDSTrigger(Collection<Coding> drugCodings, Collection<Coding> findingCodings, CDSCard card) {
		this.drugCodings = drugCodings;
		this.findingCodings = findingCodings;
		this.card = card;
	}

	public boolean isTriggered(Collection<Coding> activeDiagnosesCodings, Collection<Coding> draftMedicationOrderCodings) {
		return anyOverlap(activeDiagnosesCodings, findingCodings) && anyOverlap(draftMedicationOrderCodings, drugCodings);
	}

	private boolean anyOverlap(Collection<Coding> codingsA, Collection<Coding> codingsB) {
		for (Coding codingA : codingsA) {
			for (Coding codingB : codingsB) {
				if (codingA.getSystem().equals(codingB.getSystem()) && codingA.getCode().equals(codingB.getCode())) {
					return true;
				}
			}
		}
		return false;
	}

	public Collection<Coding> getDrugCodings() {
		return drugCodings;
	}

	public Collection<Coding> getFindingCodings() {
		return findingCodings;
	}

	public CDSCard getCard() {
		return card;
	}
}
