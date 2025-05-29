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
import org.snomed.cdsservice.model.MedicationConditionCDSTrigger;
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

import static org.snomed.cdsservice.service.medication.MedicationCombinationRuleLoaderService.TAB_DELIMITER;

@Service
public class MedicationConditionRuleLoaderService {
	private static final String CONTRAINDICATION_ALERT_TYPE = "Contraindication";

	@Value("${rules.medication-condition.tsv}")
	private String tsvPath;

	@Autowired
	private FHIRTerminologyServerClient tsClient;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public List<CDSTrigger> loadTriggers() throws ServiceException {
		List<CDSTrigger> triggers = new ArrayList<>();
		try (FileInputStream file = new FileInputStream(tsvPath)) {
			CSVReader csvReader = new CSVReader(file);
			csvReader.setDelimiter(TAB_DELIMITER);
			String[] expectedHeadings = new String[]{
					"UUID",
					"Medication",
					"Medication SNOMED Code",
					"Contraindication - Finding",
					"Finding SNOMED Code",
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
				String medicationLabel = csvReader.cell(expectedHeadings[1]);
				String medicationSnomedCode = csvReader.cell(expectedHeadings[2]);
				String conditionLabel = csvReader.cell(expectedHeadings[3]);
				String conditionSnomedCode = csvReader.cell(expectedHeadings[4]);
				String cardIndicator = csvReader.cell(expectedHeadings[5]);
				String cardSummary = csvReader.cell(expectedHeadings[6]);
				String cardDetail = csvReader.cell(expectedHeadings[7]);
				String source = csvReader.cell(expectedHeadings[8]);
				String sourceLink = csvReader.cell(expectedHeadings[9]);

				if (Strings.isNullOrEmpty(medicationSnomedCode) || Strings.isNullOrEmpty(conditionSnomedCode) || Strings.isNullOrEmpty(source)) {
					logger.info("Ignoring row {}, medicationSnomedCode {} conditionSnomedCode {} source {} ", rowNumber, medicationSnomedCode, conditionSnomedCode, source);
					continue;
				}

				if (medicationSnomedCode.contains("|") && !medicationSnomedCode.startsWith("ECL=")) {
					medicationSnomedCode = medicationSnomedCode.substring(medicationSnomedCode.indexOf("|")).trim();
				}

				if (conditionSnomedCode.contains("|") && !conditionSnomedCode.startsWith("ECL=")) {
					conditionSnomedCode = conditionSnomedCode.substring(conditionSnomedCode.indexOf("|")).trim();
				}

				CDSCard cdsCard = new CDSCard(uuid, cardSummary, cardDetail, CDSIndicator.valueOf(cardIndicator), new CDSSource(source, sourceLink), null, null, CONTRAINDICATION_ALERT_TYPE);
				Collection<Coding> medicationCodings = tsClient.expandValueSet(SnomedValueSetUtil.getSNOMEDValueSetURI(medicationSnomedCode));
				Collection<Coding> conditionCodings = tsClient.expandValueSet(SnomedValueSetUtil.getSNOMEDValueSetURI(conditionSnomedCode));
				logger.info("Created trigger {} / {}", medicationLabel, conditionLabel);
				triggers.add(new MedicationConditionCDSTrigger(medicationLabel, medicationCodings, conditionLabel, conditionCodings, cdsCard));

				rowNumber++;
			}
		} catch (Exception e) {
			throw new ServiceException("Failed to read CDS rules from tab separated file", e);
		}
		return triggers;
	}
}
