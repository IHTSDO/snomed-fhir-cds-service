package org.snomed.cdsservice.model;

import org.hl7.fhir.r4.model.Coding;

import java.util.Collection;
import java.util.Set;

public class MedicationInterationCDSTrigger extends CDSTrigger {

    public MedicationInterationCDSTrigger(String medication1Label, Collection<Coding> medication1Codings, String medication2Label, Collection<Coding> medication2Codings, CDSCard card) {
        super(medication1Label, medication1Codings, medication2Label, medication2Codings, card);
    }

    @Override
    public CDSCard createRelevantCard(Set<Coding> activeDiagnosesOrMedicationCodings, Set<Coding> draftMedicationOrderCodings) {
        Collection<Coding> medication1Intersection = getIntersection(draftMedicationOrderCodings, getMedicationCodings());
        Collection<Coding> medication2Intersection = getIntersection(activeDiagnosesOrMedicationCodings, getMedication2Codings());

        if (!medication2Intersection.isEmpty() && !medication1Intersection.isEmpty()) {
            CDSCard cardInstance = getCard().cloneCard();

            cardInstance.setSummary(processTextTemplate(cardInstance.getSummary(), medication1Intersection, medication2Intersection));
            cardInstance.setDetail(processTextTemplate(cardInstance.getDetail(), medication1Intersection, medication2Intersection));
            addReferenceMedicationToCDSCard(medication1Intersection, cardInstance);
            addReferenceMedicationToCDSCard(medication2Intersection, cardInstance);

            return cardInstance;
        } else {
            return null;
        }
    }

    @Override
    String processTextTemplate(String text, Collection<Coding> medication1Intersection, Collection<Coding> medication2Intersection) {
        if (text == null) {
            return null;
        }

        text = text.replace("{{RuleMedication1}}", getMedicationLabel());
        text = text.replace("{{ActualMedication1}}", toHumanReadable(medication1Intersection));
        text = text.replace("{{RuleMedication2}}", getMedication2Label());
        text = text.replace("{{ActualMedication2}}", toHumanReadable(medication2Intersection));

        return text;
    }

}
