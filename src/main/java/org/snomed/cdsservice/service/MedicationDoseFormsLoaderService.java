package org.snomed.cdsservice.service;

import org.hl7.fhir.r4.model.Coding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.cdsservice.service.model.ManyToOneMapEntry;
import org.snomed.cdsservice.service.tsclient.FHIRTerminologyServerClient;
import org.snomed.cdsservice.util.SnomedValueSetUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static java.lang.String.format;

@Service
public class MedicationDoseFormsLoaderService {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Value("${rules.medication-dose-forms.tsv}")
    private String tsvPath;

    @Autowired
    private FHIRTerminologyServerClient tsClient;

    public List<ManyToOneMapEntry> loadDoseFormMap() throws ServiceException {
        logger.info("Loading SNOMED CT Manufactured dose form to ATC route of administration - dynamic map.");
        try (BufferedReader reader = new BufferedReader(new FileReader(tsvPath))) {
            String expectedHeader = "adm_r\tadm_label\tsnomed_basic_dose_form\tsnomed_administration_method\tsnomed_intended_site\tmanufactured_dose_form_snomed_query\tpriority";

            // File header and column indexes:
            // adm_r	adm_label	snomed_basic_dose_form	snomed_administration_method	snomed_intended_site	manufactured_dose_form_snomed_query priority
            // 0        1           2                       3                               4                       5                                   6

            String header = reader.readLine();
            if (!expectedHeader.equals(header)) {
                throw new ServiceException(format("Medication dose form file does not have the expected header. " +
                        "Expected: '%s', Actual: '%s'", expectedHeader, header));
            }

            List<ManyToOneMapEntry> mapEntries = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                String[] columns = line.split("\t");
                String atcAdministrationCode = columns[0];
                String snomedDoseFormQuery = columns[5];
                int mapPriority = Integer.parseInt(columns[6]);
                String valueSetURI = SnomedValueSetUtil.getSnomedECLValueSetURI(snomedDoseFormQuery);
                try {
                    Set<String> manufacturedDoseFormSnomedCodes = tsClient.expandValueSet(valueSetURI).stream().map(Coding::getCode).collect(Collectors.toSet());
                    logger.info("Mapping {} SNOMED CT Manufactured dose forms to ATC route of administration '{}'.", manufacturedDoseFormSnomedCodes.size(), atcAdministrationCode);
                    mapEntries.add(new ManyToOneMapEntry(manufacturedDoseFormSnomedCodes, atcAdministrationCode, mapPriority));
                } catch (RestClientException e) {
                    throw new ServiceException(format("Failed to expand value set '%s'", valueSetURI), e);
                }
            }
            mapEntries.sort(Comparator.comparing(ManyToOneMapEntry::getMapPriority));

            return mapEntries;
        } catch (Exception e) {
            throw new ServiceException(format("Failed to load SNOMED CT Manufactured dose form to ATC route of administration - dynamic map %s.", tsvPath), e);
        }
    }
}