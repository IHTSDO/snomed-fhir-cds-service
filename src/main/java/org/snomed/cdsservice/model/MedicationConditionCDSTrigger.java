package org.snomed.cdsservice.model;

import org.hl7.fhir.r4.model.Coding;

import java.util.Collection;
import java.util.Set;

public class MedicationConditionCDSTrigger extends CDSTrigger {

    public MedicationConditionCDSTrigger(String medicationLabel, Collection<Coding> medicationCodings, String conditionLabel, Collection<Coding> conditionCodings, CDSCard card) {
        super(medicationLabel, medicationCodings, conditionLabel, conditionCodings, card);
    }

    public CDSCard createRelevantCard(Set<Coding> activeDiagnosesCodings, Set<Coding> draftMedicationOrderCodings) {
        Collection<Coding> conditionIntersection = getIntersection(activeDiagnosesCodings, getConditionCodings());
        Collection<Coding> medicationIntersection = getIntersection(draftMedicationOrderCodings, getMedicationCodings());
        if (!conditionIntersection.isEmpty() && !medicationIntersection.isEmpty()) {
            CDSCard cardInstance = getCard().cloneCard();

            cardInstance.setSummary(processTextTemplate(cardInstance.getSummary(), conditionIntersection, medicationIntersection));
            cardInstance.setDetail(processTextTemplate(cardInstance.getDetail(), conditionIntersection, medicationIntersection));
            addReferenceMedicationToCDSCard(medicationIntersection, cardInstance);
            addReferenceConditionToCDSCard(conditionIntersection, cardInstance);

            return cardInstance;
        } else {
            return null;
        }
    }
    public String processTextTemplate(String text, Collection<Coding> conditionIntersection, Collection<Coding> medicationIntersection) {
        if (text == null) {
            return null;
        }

        text = text.replace("{{RuleMedication}}", getMedicationLabel());
        text = text.replace("{{ActualMedication}}", toHumanReadable(medicationIntersection));
        text = text.replace("{{RuleCondition}}", getConditionLabel());
        text = text.replace("{{ActualCondition}}", toHumanReadable(conditionIntersection));

        return text;
    }
}
