package org.snomed.cdsservice.service;

import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.utilities.CSVReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.cdsservice.model.CDSCard;
import org.snomed.cdsservice.model.CDSIndicator;
import org.snomed.cdsservice.model.CDSSource;
import org.snomed.cdsservice.model.CDSTrigger;
import org.snomed.cdsservice.model.CDSTriggerType;
import org.snomed.cdsservice.service.tsclient.FHIRTerminologyServerClient;
import org.snomed.cdsservice.util.SnomedValueSetUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Service
public class MedicationRuleLoaderService {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Value("${rules.medication-medication.csv}")
    private String csvPath;

    @Autowired
    private FHIRTerminologyServerClient tsClient;


    public List<CDSTrigger> loadTriggers() {
        List<CDSTrigger> triggers = new ArrayList<>();
        try (FileInputStream file = new FileInputStream(csvPath)) {
            CSVReader csvReader = new CSVReader(file);
            String[] expectedHeadings = new String[]{
                    "UUID",
                    "Medication1",
                    "Medication1 SNOMED Code",
                    "Medication2",
                    "Medication2 SNOMED Code",
                    "Card Indicator",
                    "Card Summary",
                    "Card Detail",
                    "Source",
                    "Source Link"
            };

            csvReader.readHeaders();
            while (csvReader.line()) {
                String uuid = csvReader.cell(expectedHeadings[0]);
                String medication1Label = csvReader.cell(expectedHeadings[1]);
                String medication1SnomedCode = csvReader.cell(expectedHeadings[2]);
                String medication2Label = csvReader.cell(expectedHeadings[3]);
                String medication2SnomedCode = csvReader.cell(expectedHeadings[4]);
                String cardIndicator = csvReader.cell(expectedHeadings[5]);
                String cardSummary = csvReader.cell(expectedHeadings[6]);
                String cardDetail = csvReader.cell(expectedHeadings[7]);
                String source = csvReader.cell(expectedHeadings[8]);
                String sourceLink = csvReader.cell(expectedHeadings[9]);

                if (medication1SnomedCode != null && medication2SnomedCode != null) {
                    CDSCard cdsCard = new CDSCard(uuid, cardSummary, cardDetail, CDSIndicator.valueOf(cardIndicator), new CDSSource(source, sourceLink), null, null);
                    Collection<Coding> medication1Codings = tsClient.expandValueSet(SnomedValueSetUtil.getSNOMEDValueSetURI(medication1SnomedCode));
                    Collection<Coding> medication2Codings = tsClient.expandValueSet(SnomedValueSetUtil.getSNOMEDValueSetURI(medication2SnomedCode));
                    logger.info("Created trigger {} / {}", medication1Label, medication2Label);
                    triggers.add(new CDSTrigger(medication1Label, medication1Codings, medication2Label, medication2Codings, cdsCard, CDSTriggerType.DRUG_DRUG));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return triggers;
    }
}
