package org.snomed.cdsservice.util;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.snomed.cdsservice.service.ServiceException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SnomedSpreadsheetUtil {

	/**
	 * Reads a SNOMED CT concept id from the spreadsheet cell that may be formatted as either a string or number.
	 * Detects if the value has been corrupted by bad spreadsheet formatting and automatically fixes the SCTID by reconstructing the segment and check digit.
	 * @throws ServiceException if the identifier is corrupted beyond repair. This is not expected to happen for SCTIDs.
	 */
	public static String readSnomedConceptOrECL(Row cells, int column, int row) throws ServiceException {
		Cell cell = cells.getCell(column);

		if (cell == null) {
			return null;
		}
		String cellValue = null;
		CellType cellType = cell.getCellType();
		if (cellType == CellType.STRING) {
			cellValue = cell.getStringCellValue();
			if (cellValue != null && cellValue.contains("|") && !cellValue.startsWith("ECL=")) {
				cellValue = cellValue.substring(cellValue.indexOf("|")).trim();
			}
		} else if (cellType == CellType.NUMERIC) {
			String rawValue = ((XSSFCell) cell).getRawValue();
			if (rawValue.contains("E")) {
				Pattern pattern = Pattern.compile("([0-9.]+)E\\+([0-9]+)");
				Matcher matcher = pattern.matcher(rawValue);
				if (matcher.matches()) {
					// Replace segment and checksum. This method works in all tested cases up to max permitted length of 16 digits.
					String part = matcher.group(1).replace(".", "");
					part = part + "10";
					char checkSum = VerhoeffCheck.calculateChecksum(part, false);
					cellValue = part + checkSum;
				} else {
					throw new ServiceException(String.format("Unable to fix SNOMED CT concept id in column %s, row %s, the number is corrupted.", column + 1, row + 1));
				}
			} else {
				cellValue = Long.toString((long) cell.getNumericCellValue());
			}
		}

		return cellValue;
	}


}
