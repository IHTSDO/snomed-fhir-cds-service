# SNOMED-CT FHIR CDS Service Demonstrator

This is a Clinical Decision Support service for demonstration. CDS rules are driven by SNOMED CT value sets
and the [HL7 CDS-Hooks v2.0](https://cds-hooks.hl7.org/2.0/) standard.

## Introduction to CDS Alerts for Medication Contraindications
Clinical decision support (CDS) alerts for medication contraindications can be essential tools for improving
patient safety and medication management in healthcare settings. Medication contraindications are situations 
in which the use of a particular drug is not advisable due to a patient's medical history, current conditions,
or concurrent medication use. When medication contraindications are overlooked, patients may experience adverse
drug reactions, worsening of their medical conditions, or other harmful outcomes.

CDS alerts can help clinicians avoid prescribing medications that are contraindicated for their patients by
providing real-time notifications and warnings during the medication ordering process. These alerts can help clinicians
make more informed and safer medication decisions. By using these alerts, healthcare organizations can reduce the risk of 
medication errors, adverse drug events, and other harmful outcomes, ultimately improving patient safety 
and quality of care.

## This Demonstration Service
This demonstration service can create alerts for medication contraindications based on a patient's existing conditions.
The business rules are maintained in a spreadsheet to enable easy access and maintenance. 

The example rules are defined using SNOMED CT concepts at the highest relevant level using medicinal products and findings.
The SNOMED CT hierarchy is used so that rules are triggered when subtype medications are being ordered or subtype 
findings are in the patient record.

This service is fast. Rules and SNOMED CT subtypes are cached during startup allowing typical API response times 
of a tenth of a second.

## CDS Services API
### Service Discovery
This service implements the [discovery endpoint](https://cds-hooks.hl7.org/2.0/#discovery) that describes the available CDS Services.

### "Medication Order Select" Service
This service can be called when creating a medication order. The service will return user alerts if there are any know contraindications 
between the drug being ordered and the existing conditions on the patient record.
#### Spreadsheet Driven Rules
The business logic for the medication order select CDS service are driven by rules authored in a spreadsheet, see 'CDS_Medication-Condition_Cards.xlsx'. 

This spreadsheet uses a simple template format for the user messages where: 
- `{{RuleCondition}}` is the condition label from the rule sheet
- `{{RuleMedication}}` is the medication label from the rule sheet
- `{{ActualCondition}}` is the condition from the patient record that triggered the rule
- `{{ActualMedication}}` is the medication from the medication order that triggered the rule

### "Hello Test" Service
There is a simple hello-test service that takes no inputs, does not require a terminology server, and responds with a simple CDS Card with a greeting. 
This is intended to provide an early test point while integrating the service. 

## Running the Service
### Prerequisites
- Java 17
- Access to a FHIR Terminology Server capable of expanding [SNOMED CT Implicit Value Sets using ECL](https://www.hl7.org/fhir/snomedct.html#implicit).

### Starting the Service
Download the latest jar file from the releases page.  
_(Alternatively the jar file can be compiled from the source code using
[Apache Maven](https://maven.apache.org/guides/getting-started/maven-in-five-minutes.html) `mvn install`)._

Start the service on the command line:
```
java -Xms2g -jar snomed-fhir-cds-service.jar \
  --fhir.terminology-server.url=FHIR_TS
```
Where:
- `FHIR_TR` is a Terminology Server FHIR API URL.

### Testing the Service
Download and import the [Postman Collection](Postman_collection.json) for API examples.
