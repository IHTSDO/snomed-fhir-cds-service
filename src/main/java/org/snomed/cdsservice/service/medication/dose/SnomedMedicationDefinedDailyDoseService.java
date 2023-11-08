package org.snomed.cdsservice.service.medication.dose;

import jakarta.annotation.PostConstruct;
import net.steppschuh.markdowngenerator.list.UnorderedList;
import net.steppschuh.markdowngenerator.text.Text;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Dosage;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Timing;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.cdsservice.model.AggregatedMedicationsBySubstance;
import org.snomed.cdsservice.model.CDSCard;
import org.snomed.cdsservice.model.CDSCoding;
import org.snomed.cdsservice.model.CDSIndicator;
import org.snomed.cdsservice.model.CDSReference;
import org.snomed.cdsservice.model.CDSSource;
import org.snomed.cdsservice.model.DosageComparisonByRoute;
import org.snomed.cdsservice.model.PrescribedDailyDose;
import org.snomed.cdsservice.service.ArgumentAssertionUtil;
import org.snomed.cdsservice.service.ServiceException;
import org.snomed.cdsservice.service.model.ManyToOneMapEntry;
import org.snomed.cdsservice.service.tsclient.ConceptParameters;
import org.snomed.cdsservice.service.tsclient.FHIRTerminologyServerClient;
import org.snomed.cdsservice.service.tsclient.SnomedConceptNormalForm;
import org.snomed.cdsservice.util.Frequency;
import org.snomed.cdsservice.util.UnitConversion;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.BufferedReader;
import java.io.FileReader;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

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

    public static final String WARNING = "warning";
    public static final String INFO = "info";
    private static final String NEW_LINE = "\n";
    private static final String HIGH_DOSAGE_ALERT_TYPE = "High Dosage";

    @Autowired
    private FHIRTerminologyServerClient tsClient;

    @Autowired
    private MedicationDoseFormsLoaderService doseFormsLoaderService;

    @Value("${rules.medication-substance-daily-doses.tsv}")
    private String tsvPath;
    @Value("${acceptable-daily-dose-threshold-factor}")
    private String acceptableDailyDoseThresholdFactor;
    @Value("${maximum-daily-dose-threshold-factor}")
    private String maximumDailyDoseThresholdFactor;
    @Value("${card-summary-template-content}")
    private String cardSummaryTemplate;
    @Value("${who.atc.url-template}")
    private String atcUrlTemplate;

    private List<ManyToOneMapEntry> doseFormsManySnomedToOneAtcCodeMap;
    private final Map<String, List<SubstanceDefinedDailyDose>> substanceDDD = new HashMap<>();
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @PostConstruct
    public void init() throws ServiceException {
        doseFormsManySnomedToOneAtcCodeMap = doseFormsLoaderService.loadDoseFormMap();

        logger.info("Loading SNOMED CT Substance Defined Daily Dose information");
        try (BufferedReader reader = new BufferedReader(new FileReader(tsvPath))) {
            String expectedHeader = "atc_code\tatc_name\tsnomed_code\tsnomed_label\tddd\tuom\tadm_r\tnote";
            String header = reader.readLine();
            if (!expectedHeader.equals(header)) {
                throw new ServiceException(format("SNOMED Substance DDD file does not have the expected header. " + "Expected: '%s', Actual: '%s'", expectedHeader, header));
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
                    String atcCode = values[0];
                    substanceDDD.computeIfAbsent(substanceSnomedCode, i -> new ArrayList<>()).add(new SubstanceDefinedDailyDose(atcRouteOfAdministrationCode, dose, unit, atcCode));
                }
            } catch (NumberFormatException e) {
                throw new ServiceException(format("Failed to read SNOMED Substance DDDs from tab separated file. " + "Number format error while reading row %s", row), e);
            }
        } catch (Exception e) {
            throw new ServiceException("Failed to read SNOMED Substance DDDs from tab separated file", e);
        }
        logger.info("Doses loaded for {} substances.", substanceDDD.size());
    }

    public List<CDSCard> checkMedications(List<MedicationRequest> medicationRequests) {
        List<CDSCard> cards = new ArrayList<>();
        Map<String, AggregatedMedicationsBySubstance> aggregatedMedicationsBySubstanceMap = new HashMap<>();
        for (MedicationRequest medicationRequest : medicationRequests) {
            Dosage dosage = getDosagefromMedicationRequest(medicationRequest);
            Quantity doseQuantity = getDoseQuantityFromDosage(dosage);
            Frequency doseFrequency = getFrequencyFromDosage(dosage);
            PrescribedDailyDose prescribedDailyDose = getMedicationQuantityPerDay(doseQuantity, doseFrequency);

            logger.debug("Medication quantity per day {}", prescribedDailyDose);

            List<Coding> codingList = medicationRequest.getMedicationCodeableConcept().getCoding();
            Optional<Coding> snomedMedication = codingList.stream().filter(coding -> SNOMEDCT_SYSTEM.equals(coding.getSystem())).findFirst();
            if (snomedMedication.isPresent()) {
                String snomedMedicationCode = snomedMedication.get().getCode();
                String snomedMedicationLabel = snomedMedication.get().getDisplay();

                ConceptParameters conceptParameters = tsClient.lookup(SNOMEDCT_SYSTEM, snomedMedicationCode);
                if (conceptParameters == null) {
                    logger.debug("No SNOMED concept found for code {}, ignoring.", snomedMedicationCode);
                    continue;
                }
                SnomedConceptNormalForm normalForm = conceptParameters.getNormalForm();
                if (normalForm == null) {
                    logger.info("No normal form found for SNOMED code {}, skipping.", snomedMedicationCode);
                    continue;
                }

                String manufacturedDoseForm = normalForm.getAttributes().get(ATTRIBUTE_HAS_MANUFACTURED_DOSE_FORM);
                if (manufacturedDoseForm == null) {
                    logger.info("SNOMED drug {} has no manufactured does form, skipping.", snomedMedicationCode);
                    continue;
                }
                String atcRouteOfAdministrationCode = null;
                String routeOfAdministrationLabel = null;
                for (ManyToOneMapEntry manyToOneMapEntry : doseFormsManySnomedToOneAtcCodeMap) {
                    if (manyToOneMapEntry.sourceCodes().contains(manufacturedDoseForm)) {
                        atcRouteOfAdministrationCode = manyToOneMapEntry.targetCode();
                        routeOfAdministrationLabel = manyToOneMapEntry.label() != null ? manyToOneMapEntry.label() : atcRouteOfAdministrationCode;
                    }
                }
                if (atcRouteOfAdministrationCode == null) {
                    logger.info("SNOMED dose form {} is not covered by the route of administration dynamic map, skipping", manufacturedDoseForm);
                    continue;
                }
                aggregateMedicationsBySubstance(aggregatedMedicationsBySubstanceMap, prescribedDailyDose, codingList, snomedMedicationLabel, atcRouteOfAdministrationCode, routeOfAdministrationLabel, normalForm);
            }
        }
        composeDosageAlerts(aggregatedMedicationsBySubstanceMap, cards);
        return cards;
    }

    private Quantity getDoseQuantityFromDosage(Dosage dosage) {
        List<Dosage.DosageDoseAndRateComponent> doseAndRate = dosage.getDoseAndRate();
        ArgumentAssertionUtil.expectNotNull(doseAndRate, "Medication Request doseAndRate");
        ArgumentAssertionUtil.expectCount(1, doseAndRate.size(), "Medication Request doseAndRate");
        Dosage.DosageDoseAndRateComponent dosageDoseAndRateComponent = doseAndRate.get(0);
        Quantity doseQuantity = dosageDoseAndRateComponent.getDoseQuantity();
        return doseQuantity;
    }

    @NotNull
    private PrescribedDailyDose getMedicationQuantityPerDay(Quantity doseQuantity, Frequency doseFrequency) {
        BigDecimal doseQuantityValue = doseQuantity.getValue();
        int frequencyCount = doseFrequency.getFrequencyCount();
        int periodCount = doseFrequency.getPeriodCount();
        String periodUnit = doseFrequency.getPeriodUnit();
        Double dailyDoseQuantity = calculateTotalDosePerDay(doseQuantityValue, frequencyCount, periodCount, periodUnit);
        return new PrescribedDailyDose(dailyDoseQuantity, doseQuantity.getUnit());
    }

    private Frequency getFrequencyFromDosage(Dosage dosage) {
        Timing.TimingRepeatComponent repeat = dosage.getTiming().getRepeat();
        Frequency doseFrequency = new Frequency(repeat.getFrequency(), repeat.getPeriod().intValue(), repeat.getPeriodUnit().getDisplay());
        return doseFrequency;
    }

    private Dosage getDosagefromMedicationRequest(MedicationRequest medicationRequest) {
        List<Dosage> dosageInstruction = medicationRequest.getDosageInstruction();
        ArgumentAssertionUtil.expectCount(1, dosageInstruction.size(), "Medication Request dosageInstruction");
        Dosage dosage = dosageInstruction.get(0);
        return dosage;
    }

    private Double calculateTotalDosePerDay(BigDecimal doseQuantityValue, int frequencyCount, int periodCount, String periodUnit) {
        //calculate number of times per day
        BigDecimal timesPerDay = new BigDecimal(0);
        if (periodUnit.equals(Timing.UnitsOfTime.H.getDisplay())) {
            timesPerDay = new BigDecimal(24).divide(new BigDecimal(periodCount)).multiply(new BigDecimal(frequencyCount));
        } else if (periodUnit.equals(Timing.UnitsOfTime.D.getDisplay()) && periodCount == 1) {
            timesPerDay = (new BigDecimal(periodCount)).multiply(new BigDecimal(frequencyCount));
        } else {
            timesPerDay = new BigDecimal(1);
        }
        //calculate total dose per day
        BigDecimal totalDosePerDay = timesPerDay.multiply(doseQuantityValue);
        return totalDosePerDay.doubleValue();
    }

    private void updateTotalPrescribedDailyDose(AggregatedMedicationsBySubstance aggregatedMedicationsBySubstance, PrescribedDailyDose prescribedDailyDoseInUnitOfDDD, String routeOfAdministrationCode, SubstanceDefinedDailyDose substanceDefinedDailyDose, String routeOfAdministration) {
        Map<String, DosageComparisonByRoute> dosageComparisonByRouteMap = aggregatedMedicationsBySubstance.getDosageComparisonByRouteMap();
        DosageComparisonByRoute dosageComparisonByRoute = dosageComparisonByRouteMap.get(routeOfAdministrationCode);
        if (dosageComparisonByRoute == null) {
            dosageComparisonByRouteMap.put(routeOfAdministrationCode, new DosageComparisonByRoute(prescribedDailyDoseInUnitOfDDD, substanceDefinedDailyDose, StringUtils.capitalize(routeOfAdministration.trim())));
        } else {
            dosageComparisonByRoute.getTotalPrescribedDailyDose().addQuantity((prescribedDailyDoseInUnitOfDDD.getQuantity()));
            dosageComparisonByRouteMap.put(routeOfAdministrationCode, dosageComparisonByRoute);
        }
    }

    private void aggregateMedicationsBySubstance(Map<String, AggregatedMedicationsBySubstance> aggregatedMedicationsBySubstanceMap, PrescribedDailyDose prescribedDailyDose, List<Coding> codingList, String snomedMedicationLabel, String atcRouteOfAdministrationCode, String routeOfAdministrationLabel, SnomedConceptNormalForm normalForm) {
        // The substances within the clinical drug concepts are contained within attribute groups
        for (Map<String, String> attributeGroup : normalForm.getAttributeGroups()) {
            String substance = attributeGroup.get(ATTRIBUTE_HAS_BASIS_OF_STRENGTH_SUBSTANCE);
            List<SubstanceDefinedDailyDose> substanceDefinedDailyDoses = substanceDDD.get(substance);
            if (substanceDefinedDailyDoses == null) {
                logger.debug("No DDDs found for substance {}", substance);
                return;
            }
            SubstanceDefinedDailyDose substanceDefinedDailyDose = null;
            for (SubstanceDefinedDailyDose substanceDDD : substanceDefinedDailyDoses) {
                if (atcRouteOfAdministrationCode.equals(substanceDDD.atcRouteOfAdministration())) {
                    substanceDefinedDailyDose = substanceDDD;
                }
            }
            if (substanceDefinedDailyDose == null) {
                logger.debug("No DDD found for substance {} and route of administration {}", substance, atcRouteOfAdministrationCode);
                return;
            }

            // Substance is the chemical within the drug
            // Strength value and unit are saying how much chemical in each denominator value and unit.
            // Examples:
            // - 10mg of Azathioprine per 1 tablet (presentation type, always one thing to take)
            // - 3mg of ciprofloxacin per 1 milliliter (concentration type, need to measure out the thing to take)

            String strengthValue = null;
            String strengthUnit = null;
            String denominatorValue = null;
            String denominatorUnit = null;

            if (attributeGroup.containsKey(ATTRIBUTE_HAS_PRESENTATION_STRENGTH_NUMERATOR_VALUE)) {
                strengthValue = attributeGroup.get(ATTRIBUTE_HAS_PRESENTATION_STRENGTH_NUMERATOR_VALUE);
                strengthUnit = attributeGroup.get(ATTRIBUTE_HAS_PRESENTATION_STRENGTH_NUMERATOR_UNIT);
                denominatorValue = attributeGroup.get(ATTRIBUTE_HAS_PRESENTATION_STRENGTH_DENOMINATOR_VALUE);
                denominatorUnit = attributeGroup.get(ATTRIBUTE_HAS_PRESENTATION_STRENGTH_DENOMINATOR_UNIT);

            } else if (attributeGroup.containsKey(ATTRIBUTE_HAS_CONCENTRATION_STRENGTH_NUMERATOR_VALUE)) {
                strengthValue = attributeGroup.get(ATTRIBUTE_HAS_CONCENTRATION_STRENGTH_NUMERATOR_VALUE);
                strengthUnit = attributeGroup.get(ATTRIBUTE_HAS_CONCENTRATION_STRENGTH_NUMERATOR_UNIT);
                denominatorValue = attributeGroup.get(ATTRIBUTE_HAS_CONCENTRATION_STRENGTH_DENOMINATOR_VALUE);
                denominatorUnit = attributeGroup.get(ATTRIBUTE_HAS_CONCENTRATION_STRENGTH_DENOMINATOR_UNIT);
            }
            PrescribedDailyDose prescribedDailyDoseInUnitOfSubstanceStrength;
            PrescribedDailyDose prescribedDailyDoseInUnitOfDDD;
            try {
                prescribedDailyDoseInUnitOfSubstanceStrength = getPrescribedDailyDoseInUnitOfSubstanceStrength(prescribedDailyDose, strengthValue, strengthUnit, denominatorValue, denominatorUnit);
                prescribedDailyDoseInUnitOfDDD = getPrescribedDailyDoseInUnitOfDDD(prescribedDailyDoseInUnitOfSubstanceStrength.getQuantity(), prescribedDailyDoseInUnitOfSubstanceStrength.getUnit(), substanceDefinedDailyDose.unit());
            } catch (Exception e) {
                throw new ResponseStatusException(HttpStatus.PRECONDITION_FAILED, String.format("Prescribed dosage could not be validated for %s. Reason: Invalid dose unit.", snomedMedicationLabel), null);
            }
            AggregatedMedicationsBySubstance aggregatedMedicationsBySubstance = aggregatedMedicationsBySubstanceMap.get(substance);
            if (aggregatedMedicationsBySubstance == null) {
                String substanceShortName = getSnomedParameterValue(substance, "display");
                aggregatedMedicationsBySubstance = new AggregatedMedicationsBySubstance(substanceShortName, new ArrayList<>(Collections.singletonList(snomedMedicationLabel)), new ArrayList<>(Collections.singletonList(new CDSReference(getCodings(codingList)))));
                aggregatedMedicationsBySubstanceMap.put(substance, aggregatedMedicationsBySubstance);
                aggregatedMedicationsBySubstance.getDosageComparisonByRouteMap().put(atcRouteOfAdministrationCode, new DosageComparisonByRoute(prescribedDailyDoseInUnitOfDDD, substanceDefinedDailyDose, StringUtils.capitalize(routeOfAdministrationLabel.trim())));
            } else {
                updateTotalPrescribedDailyDose(aggregatedMedicationsBySubstance, prescribedDailyDoseInUnitOfDDD, atcRouteOfAdministrationCode, substanceDefinedDailyDose, routeOfAdministrationLabel);
                addMedications(aggregatedMedicationsBySubstance, snomedMedicationLabel, codingList);
            }
        }
    }

    void composeDosageAlerts(Map<String, AggregatedMedicationsBySubstance> aggregatedMedicationsBySubstanceMap, List<CDSCard> cards) {
        for (var aggregatedMedicationsBySubstanceEntry : aggregatedMedicationsBySubstanceMap.entrySet()) {
            String substanceName = aggregatedMedicationsBySubstanceEntry.getValue().getSubstanceShortName();
            Double prescribedDosageFactor = getPrescribedDosageFactor(aggregatedMedicationsBySubstanceEntry.getValue().getDosageComparisonByRouteMap());
            String alertLevelIndicator = getAlertIndicator(prescribedDosageFactor);
            if (StringUtils.isNotBlank(alertLevelIndicator)) {
                UUID randomUuid = UUID.fromString(UUID.nameUUIDFromBytes(substanceName.getBytes()).toString());
                String cardSummaryMsg = String.format(getCardSummaryTemplate(), substanceName, getDynamicDecimalPlace(prescribedDosageFactor));
                String cardDetailMsg = getCardDetailsInMarkDown(aggregatedMedicationsBySubstanceEntry.getValue(), prescribedDosageFactor);
                String atcUrl = getAtcUrl(aggregatedMedicationsBySubstanceEntry);
                CDSCard cdsCard = new CDSCard(randomUuid.toString(), cardSummaryMsg, cardDetailMsg, CDSIndicator.valueOf(alertLevelIndicator), new CDSSource("WHO ATC DDD", atcUrl), aggregatedMedicationsBySubstanceEntry.getValue().getReferenceList(), null, HIGH_DOSAGE_ALERT_TYPE);
                cards.add(cdsCard);
            }
        }
    }

    private String getAtcUrl(Map.Entry<String, AggregatedMedicationsBySubstance> substanceEntry) {
        Optional<Map.Entry<String, DosageComparisonByRoute>> dosageInfoEntry = substanceEntry.getValue().getDosageComparisonByRouteMap().entrySet().stream().findFirst();
        if (dosageInfoEntry.isEmpty()) {
            return null;
        }
        DosageComparisonByRoute dosageComparisonByRoute = dosageInfoEntry.get().getValue();
        return MessageFormat.format(atcUrlTemplate, dosageComparisonByRoute.getSubstanceDefinedDailyDose().atcCode());
    }

    private String getAlertIndicator(double prescribedDosageFactor) {
        String alertLevel = null;
        if (prescribedDosageFactor >= Double.parseDouble(maximumDailyDoseThresholdFactor)) {
            alertLevel = WARNING;
        } else if (prescribedDosageFactor >= Double.parseDouble(acceptableDailyDoseThresholdFactor)) {
            alertLevel = INFO;
        }
        return alertLevel;
    }

    private Double getPrescribedDosageFactor(Map<String, DosageComparisonByRoute> dosageComparisonByRouteMap) {
        Double prescribedDosageFactor = Double.valueOf(0);
        for (var eachRoute : dosageComparisonByRouteMap.entrySet()) {
            prescribedDosageFactor = prescribedDosageFactor + (eachRoute.getValue().getTotalPrescribedDailyDose().getQuantity() / eachRoute.getValue().getSubstanceDefinedDailyDose().dose());
        }
        return prescribedDosageFactor;
    }

    private void addMedications(AggregatedMedicationsBySubstance aggregatedMedicationsBySubstance, String snomedMedicationLabel, List<Coding> codingList) {
        List<String> medicationsList = aggregatedMedicationsBySubstance.getMedicationsList();
        List<CDSReference> cdsReferenceList = aggregatedMedicationsBySubstance.getReferenceList();
        if (!medicationsList.contains(snomedMedicationLabel)) {
            medicationsList.add(snomedMedicationLabel);
            cdsReferenceList.add(new CDSReference(getCodings(codingList)));
            aggregatedMedicationsBySubstance.setMedicationsList(medicationsList);
            aggregatedMedicationsBySubstance.setReferenceList(cdsReferenceList);
        }
    }

    private PrescribedDailyDose getPrescribedDailyDoseInUnitOfSubstanceStrength(PrescribedDailyDose prescribedDailyDose, String strengthValue, String strengthUnit, String denominatorValue, String denominatorUnit) {
        Double prescribedDoseQuantity = prescribedDailyDose.getQuantity();
        String prescribedDisplayUnit = prescribedDailyDose.getUnit();

        String strengthDisplayUnit = getSnomedParameterValue(strengthUnit, "display");
        String denominatorDisplayUnit = getSnomedParameterValue(denominatorUnit, "display");
        double prescribedDoseQuantityInUnitOfSubstanceStrength = prescribedDoseQuantity * Double.parseDouble(strengthValue) / Double.parseDouble(denominatorValue) * UnitConversion.factorOfConversion(prescribedDisplayUnit, denominatorDisplayUnit);
        return new PrescribedDailyDose(prescribedDoseQuantityInUnitOfSubstanceStrength, strengthDisplayUnit);
    }

    private String getSnomedParameterValue(String snomedCode, String parameterName) {
        ConceptParameters conceptParameters = tsClient.lookup(SNOMEDCT_SYSTEM, snomedCode);
        return conceptParameters.getParameter(parameterName).getValue().toString();
    }

    private PrescribedDailyDose getPrescribedDailyDoseInUnitOfDDD(Double inputStrengthValue, String inputStrengthUnit, String targetStrengthUnit) {
        Double prescribedDailyDoseQuantity = inputStrengthValue * UnitConversion.factorOfConversion(inputStrengthUnit, targetStrengthUnit);
        return new PrescribedDailyDose(prescribedDailyDoseQuantity, targetStrengthUnit);
    }

    private String getCardSummaryTemplate() {
        return cardSummaryTemplate;
    }

    private String getCardDetailsInMarkDown(AggregatedMedicationsBySubstance aggregatedMedicationsBySubstance, Double dosageWeight) {
        StringBuilder sb = new StringBuilder();
        sb.append(new Text("Substance : " + aggregatedMedicationsBySubstance.getSubstanceShortName())).append(NEW_LINE).append(NEW_LINE);
        sb.append(new Text("Present in this patient’s medication :")).append(NEW_LINE);
        sb.append(new UnorderedList<>(aggregatedMedicationsBySubstance.getMedicationsList())).append(NEW_LINE);
        sb.append(NEW_LINE).append(new Text("Route of administration :")).append(NEW_LINE);
        Map<String, DosageComparisonByRoute> dosageMapByRoute = aggregatedMedicationsBySubstance.getDosageComparisonByRouteMap();
        composeDosageByRoute(dosageMapByRoute, sb);
        sb.append(new Text("Conclusion : Combined prescribed amount is " + getDecimalPlace(dosageWeight) + " times the average daily dose."));
        return sb.toString();
    }

    private void composeDosageByRoute(Map<String, DosageComparisonByRoute> dosageMapByRoute, StringBuilder sb) {
        for (var dosageInfo : dosageMapByRoute.entrySet()) {
            DosageComparisonByRoute dosageComparisonByRouteValue = dosageInfo.getValue();
            PrescribedDailyDose aggregatedDailyDosage = dosageComparisonByRouteValue.getTotalPrescribedDailyDose();
            SubstanceDefinedDailyDose substanceDefinedDailyDose = dosageComparisonByRouteValue.getSubstanceDefinedDailyDose();
            sb.append(new UnorderedList<>(List.of(dosageComparisonByRouteValue.getRouteOfAdministration(), new UnorderedList<>(List.of("Prescribed daily dose : " + getDecimalPlace(aggregatedDailyDosage.getQuantity()) + aggregatedDailyDosage.getUnit(), "Recommended average daily dose : " +  substanceDefinedDailyDose.dose() + substanceDefinedDailyDose.unit(), "Prescribed amount is " + getDecimalPlace(aggregatedDailyDosage.getQuantity() / substanceDefinedDailyDose.dose()) + " times over the average daily dose"))))).append(NEW_LINE).append(NEW_LINE);
        }
    }

    private List<CDSCoding> getCodings(List<Coding> codings) {
        return codings.stream().map(coding -> new CDSCoding(coding.getSystem(), coding.getCode(), coding.getDisplay())).collect(Collectors.toList());
    }

    private String getDecimalPlace(Double value) {
        return String.format("%.2f", value);
    }

    private String getDynamicDecimalPlace(Double value) {
        DecimalFormat decimalFormat = new DecimalFormat("#.##");
        return decimalFormat.format(value);
    }

    public void setDoseFormsManySnomedToOneAtcCodeMap(List<ManyToOneMapEntry> mapEntries) {
        this.doseFormsManySnomedToOneAtcCodeMap = mapEntries;
    }
}