# SNOMED-CT FHIR CDS Service Demonstrator
# - Work in progress -

This is demo Clinical Decision Support service using FHIR (R4) 
and SNOMED CT value sets to drive clinical decision support rules.

It implements the [HL7 CDS-Hooks v2.0](https://cds-hooks.hl7.org/2.0/) standard.

## CDS Services API
### Service Discovery
This service implements the [Discovery endpoint](https://cds-hooks.hl7.org/2.0/#discovery) that describes what CDS Services are available.

### "Medication Order Select" Service
This service can be called when creating a medication order. The service will alert the user if there are any know contraindications 
between the drug being ordered and the existing conditions on the patient record. 

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
