package org.snomed.cdsservice.service;

import jakarta.annotation.PostConstruct;
import org.hl7.fhir.r4.model.*;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.cdsservice.model.CDSCard;
import org.snomed.cdsservice.service.model.ManyToOneMapEntry;
import org.snomed.cdsservice.service.model.SubstanceDefinedDailyDose;
import org.snomed.cdsservice.service.tsclient.ConceptParameters;
import org.snomed.cdsservice.service.tsclient.FHIRTerminologyServerClient;
import org.snomed.cdsservice.service.tsclient.SnomedConceptNormalForm;
import org.snomed.cdsservice.service.units.TimeConversionUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.FileReader;
import java.math.BigDecimal;
import java.util.*;

import static java.lang.String.format;

@Service
public class SnomedMedicationDefinedDailyDoseService {

    public static final String SNOMEDCT_SYSTEM = "http://snomed.info/sct";
    public static final String ATTRIBUTE_HAS_MANUFACTURED_DOSE_FORM = "411116001";
    public static final String ATTRIBUTE_HAS_BASIS_OF_STRENGTH_SUBSTANCE = "732943007";

    public static final String ATTRIBUTE_HAS_PRESENTATION_STRENGTH_NUMERATOR_VALUE = "1142135004";
    public static final String ATTRIBUTE_HAS_PRESENTATION_STRENGTH_NUMERATOR_UNIT = "732945000";
    public static final String ATTRIBUTE_HAS_PRESENTATION_STRENGTH_DENOMINATOR_VALUE = "1142136003";
    public static final String ATTRIBUTE_HAS_PRESENTATION_STRENGTH_DENOMINATOR_UNIT = "732947008";

    public static final String ATTRIBUTE_HAS_CONCENTRATION_STRENGTH_NUMERATOR_VALUE = "1142138002";
    public static final String ATTRIBUTE_HAS_CONCENTRATION_STRENGTH_NUMERATOR_UNIT = "733725009";
    public static final String ATTRIBUTE_HAS_CONCENTRATION_STRENGTH_DENOMINATOR_VALUE = "1142137007";
    public static final String ATTRIBUTE_HAS_CONCENTRATION_STRENGTH_DENOMINATOR_UNIT = "733722007";

    @Value("${rules.medication-substance-daily-doses.tsv}")
    private String tsvPath;

    @Autowired
    private FHIRTerminologyServerClient tsClient;

    @Autowired
    private MedicationDoseFormsLoaderService doseFormsLoaderService;

    private List<ManyToOneMapEntry> doseFormsManySnomedToOneAtcCodeMap;
    private final Map<String, List<SubstanceDefinedDailyDose>> substanceDDD = new HashMap<>();

    private final Logger logger = LoggerFactory.getLogger(getClass());

    public List<CDSCard> checkMedications(List<MedicationRequest> medicationRequests) {
        List<CDSCard> cards = new ArrayList<>();

        for (MedicationRequest medicationRequest : medicationRequests) {
            BigDecimal medicationQuantityPerDay = getMedicationQuantityPerDay(medicationRequest);
            logger.debug("Medication quantity per day {}", medicationQuantityPerDay);

            Optional<Coding> snomedMedication = medicationRequest.getMedicationCodeableConcept().getCoding().stream()
                    .filter(coding -> SNOMEDCT_SYSTEM.equals(coding.getSystem())).findFirst();
            if (snomedMedication.isPresent()) {
                String snomedMedicationCode = snomedMedication.get().getCode();

                ConceptParameters conceptParameters = tsClient.lookup(SNOMEDCT_SYSTEM, snomedMedicationCode);
                if (conceptParameters == null) {
                    logger.debug("No SNOMED concept found for code {}, ignoring.", snomedMedicationCode);
                    continue;
                }
                SnomedConceptNormalForm normalForm = conceptParameters.getNormalForm();
                if (normalForm == null) {
                    logger.info("No normal form found for snomed code {}, skipping.", snomedMedicationCode);
                    continue;
                }

                String manufacturedDoseForm = normalForm.getAttributes().get(ATTRIBUTE_HAS_MANUFACTURED_DOSE_FORM);
                if (manufacturedDoseForm == null) {
                    logger.info("SNOMED drug {} has no manufactured does form, skipping.", snomedMedicationCode);
                    continue;
                }
                String atcRouteOfAdministrationCode = null;
                for (ManyToOneMapEntry manyToOneMapEntry : doseFormsManySnomedToOneAtcCodeMap) {
                    if (manyToOneMapEntry.getSourceCodes().contains(manufacturedDoseForm)) {
                        atcRouteOfAdministrationCode = manyToOneMapEntry.getTargetCode();
                    }
                }
                if (atcRouteOfAdministrationCode == null) {
                    logger.info("SNOMED dose form {} is not covered by the route of administration dynamic map, skipping", manufacturedDoseForm);
                    continue;
                }

                // The substances within the clinical drug concepts are contained within attribute groups
                for (Map<String, String> attributeGroup : normalForm.getAttributeGroups()) {
                    String substance = attributeGroup.get(ATTRIBUTE_HAS_BASIS_OF_STRENGTH_SUBSTANCE);
                    List<SubstanceDefinedDailyDose> substanceDefinedDailyDoses = substanceDDD.get(substance);
                    if (substanceDefinedDailyDoses == null) {
                        logger.debug("No DDDs found for substance {}", substance);
                        continue;
                    }
                    SubstanceDefinedDailyDose substanceDefinedDailyDose = null;
                    for (SubstanceDefinedDailyDose substanceDDD : substanceDefinedDailyDoses) {
                        if (atcRouteOfAdministrationCode.equals(substanceDDD.getAtcRouteOfAdministration())) {
                            substanceDefinedDailyDose = substanceDDD;
                        }
                    }
                    if (substanceDefinedDailyDose == null) {
                        logger.debug("No DDD found for substance {} and route of administration {}", substance, atcRouteOfAdministrationCode);
                        continue;
                    }

                    // Substance is the chemical within the drug
                    // Strength value and unit are saying how much chemical in each denominator value and unit.
                    // Examples:
                    // - 10mg of Azathioprine per 1 tablet (presentation type, always one thing to take)
                    // - 3mg of ciprofloxacin per 1 milliliter (concentration type, need to measure out the thing to take)
                    if (attributeGroup.containsKey(ATTRIBUTE_HAS_PRESENTATION_STRENGTH_NUMERATOR_VALUE)) {
                        String strengthValue = attributeGroup.get(ATTRIBUTE_HAS_PRESENTATION_STRENGTH_NUMERATOR_VALUE);
                        String strengthUnit = attributeGroup.get(ATTRIBUTE_HAS_PRESENTATION_STRENGTH_NUMERATOR_UNIT);
                        String denominatorValue = attributeGroup.get(ATTRIBUTE_HAS_PRESENTATION_STRENGTH_DENOMINATOR_VALUE);
                        String denominatorUnit = attributeGroup.get(ATTRIBUTE_HAS_PRESENTATION_STRENGTH_DENOMINATOR_UNIT);

                        // TODO: medicationQuantityPerDay * denominatorValue * strengthValue = how much of this chemical per day
                        // TODO: Compare with substanceDefinedDailyDose
                        float averageDailyDose = substanceDefinedDailyDose.getDose();
                        String averageDailyDoseUnit = substanceDefinedDailyDose.getUnit();
                        // TODO: If order is four times or more of average daily dose add card with warning.
                        // TODO: Else if order is twice average daily dose add card with info?

                    } else if (attributeGroup.containsKey(ATTRIBUTE_HAS_CONCENTRATION_STRENGTH_NUMERATOR_VALUE)) {
                        String strengthValue = attributeGroup.get(ATTRIBUTE_HAS_CONCENTRATION_STRENGTH_NUMERATOR_VALUE);
                        String strengthUnit = attributeGroup.get(ATTRIBUTE_HAS_CONCENTRATION_STRENGTH_NUMERATOR_UNIT);
                        String denominatorValue = attributeGroup.get(ATTRIBUTE_HAS_CONCENTRATION_STRENGTH_DENOMINATOR_VALUE);
                        String denominatorUnit = attributeGroup.get(ATTRIBUTE_HAS_CONCENTRATION_STRENGTH_DENOMINATOR_UNIT);

                        // TODO: Same logic here - may need to convert units
                    }
                }
            }
        }

        return cards;
    }

    @NotNull
    private static BigDecimal getMedicationQuantityPerDay(MedicationRequest medicationRequest) {
        List<Dosage> dosageInstruction = medicationRequest.getDosageInstruction();
        ArgumentAssertionUtil.expectCount(1, dosageInstruction.size(), "Medication Request dosageInstruction");
        Dosage dosage = dosageInstruction.get(0);

        List<Dosage.DosageDoseAndRateComponent> doseAndRate = dosage.getDoseAndRate();
        ArgumentAssertionUtil.expectNotNull(doseAndRate, "Medication Request doseAndRate");
        ArgumentAssertionUtil.expectCount(1, doseAndRate.size(), "Medication Request doseAndRate");
        Dosage.DosageDoseAndRateComponent dosageDoseAndRateComponent = doseAndRate.get(0);
        Quantity doseQuantity = dosageDoseAndRateComponent.getDoseQuantity();

        BigDecimal doseQuantityValue = doseQuantity.getValue();
        // TODO: There are no units in the example I have. Do we need to do something extra here for liquids?

        Timing.TimingRepeatComponent repeat = dosage.getTiming().getRepeat();
        int frequency = repeat.getFrequency();
        BigDecimal period = repeat.getPeriod();
        Timing.UnitsOfTime periodUnit = repeat.getPeriodUnit();
        BigDecimal periodInDays = TimeConversionUtil.convertPeriodUnitToDay(period, periodUnit);
        BigDecimal timesPerDay = periodInDays.multiply(new BigDecimal(frequency));
        return doseQuantityValue.multiply(timesPerDay);
    }

    @PostConstruct
    public void init() throws ServiceException {
        doseFormsManySnomedToOneAtcCodeMap = doseFormsLoaderService.loadDoseFormMap();

        logger.info("Loading SNOMED CT Substance Defined Daily Dose information");
        try (BufferedReader reader = new BufferedReader(new FileReader(tsvPath))) {
            String expectedHeader = "atc_code\tatc_name\tsnomed_code\tsnomed_label\tddd\tuom\tadm_r\tnote";
            String header = reader.readLine();
            if (!expectedHeader.equals(header)) {
                throw new ServiceException(format("SNOMED Substance DDD file does not have the expected header. " +
                        "Expected: '%s', Actual: '%s'", expectedHeader, header));
            }

            String line;
            int row = 1;
            try {
                while ((line = reader.readLine()) != null) {
                    row++;
                    // Columns and indexes
                    // atc_code	atc_name	snomed_code	snomed_label	ddd	uom	adm_r	note
                    // 0		1			2			3				4	5	6		7
                    String[] values = line.split("\t");
                    String substanceSnomedCode = values[2];
                    float dose = Float.parseFloat(values[4]);
                    String unit = values[5];
                    String atcRouteOfAdministrationCode = values[6];
                    substanceDDD.computeIfAbsent(substanceSnomedCode, i -> new ArrayList<>())
                            .add(new SubstanceDefinedDailyDose(atcRouteOfAdministrationCode, dose, unit));
                }
            } catch (NumberFormatException e) {
                throw new ServiceException(format("Failed to read SNOMED Substance DDDs from tab separated file. " +
                        "Number format error while reading row %s", row), e);
            }
        } catch (Exception e) {
            throw new ServiceException("Failed to read SNOMED Substance DDDs from tab separated file", e);
        }
        logger.info("Doses loaded for {} substances.", substanceDDD.size());
    }
}