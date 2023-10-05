package org.snomed.cdsservice.service.medication;

import com.google.common.base.Strings;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.utilities.CSVReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.cdsservice.model.CDSCard;
import org.snomed.cdsservice.model.CDSIndicator;
import org.snomed.cdsservice.model.CDSSource;
import org.snomed.cdsservice.model.CDSTrigger;
import org.snomed.cdsservice.model.MedicationInterationCDSTrigger;
import org.snomed.cdsservice.service.ServiceException;
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
public class MedicationCombinationRuleLoaderService {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Value("${rules.medication-medication.tsv}")
    private String tsvPath;

    @Autowired
    private FHIRTerminologyServerClient tsClient;


    public List<CDSTrigger> loadTriggers() throws ServiceException {
        List<CDSTrigger> triggers = new ArrayList<>();
        try (FileInputStream file = new FileInputStream(tsvPath)) {
            CSVReader csvReader = new CSVReader(file);
            char TAB_DELIMITER = '\t';
            csvReader.setDelimiter(TAB_DELIMITER);
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
            int rowNumber = 1;
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

                if (Strings.isNullOrEmpty(medication1SnomedCode) || Strings.isNullOrEmpty(medication2SnomedCode) || Strings.isNullOrEmpty(source)) {
                    logger.info("Ignoring row {}, medication1SnomedCode {} medication2SnomedCode {} source {} ", rowNumber, medication1SnomedCode, medication2SnomedCode, source);
                    continue;
                }

                if (medication1SnomedCode.contains("|") && !medication1SnomedCode.startsWith("ECL=")) {
                    medication1SnomedCode = medication1SnomedCode.substring(medication1SnomedCode.indexOf("|")).trim();
                }

                if (medication2SnomedCode.contains("|") && !medication2SnomedCode.startsWith("ECL=")) {
                    medication2SnomedCode = medication2SnomedCode.substring(medication2SnomedCode.indexOf("|")).trim();
                }

                CDSCard cdsCard = new CDSCard(uuid, cardSummary, cardDetail, CDSIndicator.valueOf(cardIndicator), new CDSSource(source, sourceLink), null, null);
                Collection<Coding> medication1Codings = tsClient.expandValueSet(SnomedValueSetUtil.getSNOMEDValueSetURI(medication1SnomedCode));
                Collection<Coding> medication2Codings = tsClient.expandValueSet(SnomedValueSetUtil.getSNOMEDValueSetURI(medication2SnomedCode));
                logger.info("Created trigger {} / {}", medication1Label, medication2Label);
                triggers.add(new MedicationInterationCDSTrigger(medication1Label, medication1Codings, medication2Label, medication2Codings, cdsCard));

                rowNumber++;
            }
        } catch (Exception e) {
            throw new ServiceException("Failed to read CDS drug drug interaction rules from tab separated file", e);
        }
        return triggers;
    }
}
