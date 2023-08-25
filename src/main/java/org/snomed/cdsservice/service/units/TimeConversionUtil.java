package org.snomed.cdsservice.service.units;

import org.hl7.fhir.r4.model.Timing;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class TimeConversionUtil {

	public static BigDecimal convertPeriodUnitToDay(BigDecimal period, Timing.UnitsOfTime unitOfTime) {
		switch (unitOfTime) {
			case A -> {
				return period.divide(new BigDecimal(365), RoundingMode.HALF_EVEN);
			}
			case MO -> {
				return period.divide(new BigDecimal(28), RoundingMode.HALF_EVEN);
			}
			case WK -> {
				return period.divide(new BigDecimal(7), RoundingMode.HALF_EVEN);
			}
			case H -> {
				return period.multiply(new BigDecimal(24));
			}
			case MIN -> {
				return period.multiply(new BigDecimal(24 * 60));
			}
			case S -> {
				return period.multiply(new BigDecimal(24 * 60 * 60));
			}
			default -> {
				return period;
			}
		}
	}

}
