package org.snomed.cdsservice.service.medication;

import com.google.common.base.Strings;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.hl7.fhir.r4.model.Coding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.cdsservice.model.CDSCard;
import org.snomed.cdsservice.model.CDSIndicator;
import org.snomed.cdsservice.model.CDSSource;
import org.snomed.cdsservice.model.CDSTrigger;
import org.snomed.cdsservice.model.MedicationConditionCDSTrigger;
import org.snomed.cdsservice.service.ServiceException;
import org.snomed.cdsservice.service.tsclient.FHIRTerminologyServerClient;
import org.snomed.cdsservice.util.SnomedSpreadsheetUtil;
import org.snomed.cdsservice.util.SnomedValueSetUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static java.lang.String.format;

@Service
public class MedicationConditionRuleLoaderService {

	@Value("${rules.medication-condition.spreadsheet}")
	private String spreadsheetPath;

	@Autowired
	private FHIRTerminologyServerClient tsClient;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public List<CDSTrigger> loadTriggers() throws ServiceException {
		List<CDSTrigger> triggers = new ArrayList<>();

		try (FileInputStream file = new FileInputStream(spreadsheetPath)) {
			Workbook workbook = new XSSFWorkbook(file);

			String[] expectedHeadings = new String[] {
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

			Sheet sheet = workbook.getSheetAt(0);
			int rowNumber = 0;
			for (Row row : sheet) {
				rowNumber++;

				String uuid = null;
				String medicationLabel = null;
				String medicationSnomedCode = null;
				String conditionLabel = null;
				String conditionSnomedCode = null;
				String cardIndicator = null;
				String cardSummary = null;
				String cardDetail = null;
				String source = null;
				String sourceLink = null;

				int cellNumber = 0;
				for (Cell cell : row) {
					cellNumber++;
					String value = switch (cell.getCellType()) {
						case STRING -> cell.getStringCellValue();
						case NUMERIC -> String.valueOf(cell.getNumericCellValue());
						case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
						default -> null;
					};

					if (rowNumber == 0) {
						if (cellNumber <= expectedHeadings.length && (value == null || value.startsWith(expectedHeadings[cellNumber]))) {
							throw new RuntimeException(format("Expected header '%s' at row 1, column %s.", expectedHeadings[cellNumber], cellNumber));
						}
					} else {
						if (cellNumber == 1) {
							if (Strings.isNullOrEmpty(value)) {
								// UUID is logged at debug because whole row may be blank so info would not be useful.
								logger.debug("Ignoring row {}, missing UUID.", rowNumber);
								break;
							}
							uuid = value;
						} else if (cellNumber == 2) {
							if (Strings.isNullOrEmpty(value)) {
								logger.info("Ignoring row {}, missing Medication label.", rowNumber);
								break;
							}
							medicationLabel = value;
						} else if (cellNumber == 3) {
							if (Strings.isNullOrEmpty(value)) {
								logger.info("Ignoring row {}, missing Medication code.", rowNumber);
								break;
							}
							medicationSnomedCode = SnomedSpreadsheetUtil.readSnomedConceptOrECL(row, cellNumber - 1, rowNumber);
						} else if (cellNumber == 4) {
							if (Strings.isNullOrEmpty(value)) {
								logger.info("Ignoring row {}, missing Condition label.", rowNumber);
								break;
							}
							conditionLabel = value;
						} else if (cellNumber == 5) {
							if (Strings.isNullOrEmpty(value)) {
								logger.info("Ignoring row {}, missing Condition code.", rowNumber);
								break;
							}
							conditionSnomedCode = SnomedSpreadsheetUtil.readSnomedConceptOrECL(row, cellNumber - 1, rowNumber);
						} else if (cellNumber == 6) {
							if (Strings.isNullOrEmpty(value)) {
								logger.info("Ignoring row {}, missing Card Indicator.", rowNumber);
								break;
							}
							cardIndicator = value;
						} else if (cellNumber == 7) {
							if (Strings.isNullOrEmpty(value)) {
								logger.info("Ignoring row {}, missing Card Summary.", rowNumber);
								break;
							}
							cardSummary = value;
						} else if (cellNumber == 8) {
							// Not mandatory
							cardDetail = value;
						} else if (cellNumber == 9) {
							if (Strings.isNullOrEmpty(value)) {
								logger.info("Ignoring row {}, missing Source.", rowNumber);
								break;
							}
							source = value;
						} else if (cellNumber == 10) {
							// Not mandatory
							sourceLink = value;
						}
					}
				}
				if (rowNumber > 1 && !Strings.isNullOrEmpty(source) && medicationSnomedCode != null && conditionSnomedCode != null) {
					CDSCard cdsCard = new CDSCard(uuid, cardSummary, cardDetail, CDSIndicator.valueOf(cardIndicator), new CDSSource(source, sourceLink), null, null);
					Collection<Coding> medicationCodings = tsClient.expandValueSet(SnomedValueSetUtil.getSNOMEDValueSetURI(medicationSnomedCode));
					Collection<Coding> conditionCodings = tsClient.expandValueSet(SnomedValueSetUtil.getSNOMEDValueSetURI(conditionSnomedCode));
					logger.info("Created trigger {} / {}", medicationLabel, conditionLabel);
					triggers.add(new MedicationConditionCDSTrigger(medicationLabel, medicationCodings, conditionLabel, conditionCodings, cdsCard));
				}
			}
		} catch (IOException e) {
			throw new ServiceException("Failed to read CDS rules from spreadsheet", e);
		}

		return triggers;
	}

}
