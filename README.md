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

### Standard CDS Hooks Services
The discovery endpoint publishes multiple standard CDS Hooks services so the demo can be exercised against different
workflow events using standard hook names only.

#### "Medication Order Select" Service
This service is intended for the CDS Hooks `order-select` hook during medication prescribing. It returns user alerts if
there are known contraindications, interactions, allergy conflicts, or excessive dosage concerns related to the draft
medication orders being selected. The request should provide draft medication orders in `context.draftOrders`,
selected draft order references in `context.selections`, and prefetch data for the patient, conditions, active
medications, and optional allergies.

#### "Medication Order Sign" Service
This service is intended for the CDS Hooks `order-sign` hook just before draft medication orders are finalized. It
evaluates the full draft order bundle in `context.draftOrders` and returns final prescribing alerts for contraindications,
interactions, allergy conflicts, and excessive dosage concerns.

#### "Problem List Item Create Medication Check" Service
This service is intended for the CDS Hooks `problem-list-item-create` hook. It evaluates newly created Condition
resources supplied in `context.conditions` against the patient's existing medication list provided in prefetch.

#### "Patient View Medication Summary Check" Service
This service is intended for the CDS Hooks `patient-view` hook. It provides summary medication safety alerts for a
patient chart using prefetch data for conditions, medications, and allergies.

#### Spreadsheet Driven Rules
The business logic for the medication order select CDS service are driven by rules authored in a spreadsheet, see 'CDS_Medication-Condition_Cards.xlsx'. 

This spreadsheet uses a simple template format for the user messages where: 
- `{{RuleCondition}}` is the condition label from the rule sheet
- `{{RuleMedication}}` is the medication label from the rule sheet
- `{{ActualCondition}}` is the condition from the patient record that triggered the rule
- `{{ActualMedication}}` is the medication from the medication order that triggered the rule

## Diagnostic Decision Support Demo
In addition to the medication safety services, the demonstrator includes a **diagnostic** decision-support
engine. Where the medication rules match a single medication against a single condition, the diagnostic
engine evaluates more complex clinical pathways — numeric Observation values, UCUM units, Observation
components, repeated measurements across dates or encounters, coded findings, and explicit CDS Hooks
context — combined with nested `AND`/`OR`/`NOT` logic and three-valued (true/false/unknown) evaluation.

The first two diagnostic domains are **Type 2 diabetes** and **hypertension**, based on national guideline
pathways. Each fires CDS Hooks cards on the `patient-view` hook.

> This is a demonstration, not a production clinical rules engine. See *First-version limitations* below.

### Triggering and the `patient-view` hook
All diagnostic services are invoked on the standard CDS Hooks `patient-view` hook. This is a deliberate
choice, not only a simplification:

- **There is no standard "observation created / new result" hook** in the CDS Hooks 2.0 catalogue. A custom
  hook could be defined, but it would only work with the EHR that implements it.
- **`patient-view` provides the full observation history** through prefetch (`Observation?patient={{context.patientId}}`).
  Several rules need that history rather than a single new value — for example *two-hour OGTT ≥ 11.1 mmol/L on
  two separate occasions* (`distinct_by = calendar_day`) or *clinic systolic ≥ 140 mmHg on two visits*
  (`distinct_by = encounter`). An event carrying only the newly entered observation could not satisfy these.
- **When the hook fires is an EHR concern.** `patient-view` means "the patient chart was opened"; exactly
  when it is (re)invoked — on open, or on refresh after a new observation is saved — is configured in the EHR,
  not in this service. Handle the "re-run after a new result" behaviour on the EHR side.

The engine is **hook-agnostic**: if a specific EHR offers a results/observation hook (custom or via a
national profile), switching a service to it is a one-column change (`hook`) plus its prefetch template — no
engine code changes, because services are registered from the TSV content. The `trigger_event` column is
descriptive documentation of the pathway only; it is not evaluated and does not trigger anything.

### The two-file rule model
Each clinical domain is defined by two tab-separated files in the rules directory (`rules.diagnostic.directory`,
default `knowledge-bases/`):

- **`<domain>_criteria.tsv`** — reusable *atomic criteria*. Each row defines how to find and evaluate one
  clinical fact (e.g. *fasting plasma glucose ≥ 7.0 mmol/L*, *24-hour ABPM average systolic ≥ 130 mmHg*).
- **`<domain>_rules.tsv`** — clinical *rules*. Each row references criteria through a small Boolean
  expression and defines the outcome, action, card templates, source guideline and card indicator.

Discovery is data-driven: dropping a matching `<name>_criteria.tsv` + `<name>_rules.tsv` pair into the
directory registers a new CDS service automatically (keyed by the `service_id` column) — **no code change**.

### Supported criterion types
- `OBSERVATION_VALUE` — a numeric value on `Observation.valueQuantity`.
- `OBSERVATION_COMPONENT_VALUE` — a numeric value inside an `Observation.component` (e.g. systolic/diastolic).
  All constraints of one criterion apply to the **same** Observation.
- `CODED_RESOURCE_PRESENT` — presence (or, with `not_exists`, absence) of a coded resource. Driven by
  `resource_type`, so besides `Condition` it also supports `Procedure`, `Immunization`, `MedicationRequest`
  and `AllergyIntolerance`. Only clinically relevant resources count (entered-in-error/inactive are ignored).
- `CONTEXT_VALUE` — an explicit value in the CDS Hooks request `context` (e.g.
  `abpmHbpUnavailableOrImpractical = true`). A missing context key is **unknown**, never false.

### Supported operators
`ge`, `gt`, `le`, `lt`, `eq`, `between` (with `lower_inclusive`/`upper_inclusive` flags), `exists`,
`not_exists`. Numeric thresholds are compared with `BigDecimal`.

### Terminology selectors (ECL)
The `code_selector` column is **SNOMED CT ECL**, resolved uniformly:
- A **single concept** (`271062006 |Fasting blood glucose measurement|`) or a **disjunction of concepts**
  (`A |..| OR B |..|`) is resolved **locally, without a terminology server** — exact `system + code` matching.
- Any ECL that uses **operators** (`<`, `<<`, refinements `:`, reference sets `^`, wildcard `*`) is expanded
  once against the configured FHIR terminology server at startup and cached.

Because the supplied content uses only enumerated concepts, **the demo runs without a terminology server**.
An enabled rule that requires an expansion which cannot be resolved fails the application fast at startup
(it is never silently ignored). Exact Observation codes are **not** expanded to their descendants.

### Boolean expression syntax and three-valued logic
Rules combine criteria with a constrained, CQL-inspired language: criterion identifiers, `AND`, `OR`,
`NOT`, and parentheses. Precedence (highest first): parentheses, `NOT`, `AND`, `OR`. Keywords are
case-insensitive. Example:

```
RANDOM_GLUCOSE_HIGH AND ( CLASSIC_SYMPTOMS OR HYPERGLYCAEMIC_CRISIS )
```

Every criterion evaluates to `TRUE`, `FALSE` or `UNKNOWN`. `UNKNOWN` is clinically distinct from `FALSE`:
a missing or uninterpretable measurement is *unknown*, not *negative*. Cards are produced only for rules
that evaluate to `TRUE`; `{{MatchedCriteria}}` lists only the criteria that contributed to the successful path.

### Card templates, outcomes and actions
Card `summary`/`detail` support `{{MatchedCriteria}}` and `{{OutcomeDisplay}}` placeholders. Each rule carries
an `outcome_status` (`suspected`/`likely`/`diagnostic`) and an `action_type` (`create_condition`,
`recommend_confirmation`, `recommend_confirmatory_test`); a `likely` or `suspected` result recommends
confirmation rather than asserting a diagnosis. When `suppress_if_outcome_present = true`, the card is
withheld if the patient already has an active `Condition` for the outcome. Matching is subtype-aware for
SNOMED outcomes when a terminology server is available (`<< outcome_code`, so a recorded subtype of the
diagnosis also suppresses); it falls back to exact `outcome_code_system + outcome_code` matching otherwise.

### Expected FHIR representation
- Observation numeric values in `valueQuantity` (or `component.valueQuantity`) with a UCUM `system`/`code`.
  Units are compared exactly; a value whose unit cannot be safely compared yields `UNKNOWN`.
- Only Observations with an accepted `status` (`accepted_statuses`, e.g. `final|amended|corrected`) qualify.
- `distinct_by = calendar_day` uses `effectiveDateTime`/`effectivePeriod.start`/`issued`;
  `distinct_by = encounter` uses `Observation.encounter`. When distinctness cannot be determined the result
  is `UNKNOWN`, not `FALSE`.
- **Semantic qualifiers** (`time_aspect`, `aggregation` — e.g. two-hour OGTT, 24-hour ABPM, average) are
  matched when their SNOMED concept appears among the Observation's own codings (`Observation.code`,
  `.category`, `.method`, or `.component.code`). A national FHIR profile must place them there; when a
  declared qualifier is absent the criterion is `UNKNOWN`.

### Example request and response
`POST /cds-services/diagnostic-support-diabetes`
```json
{
  "hook": "patient-view",
  "hookInstance": "example",
  "context": { "patientId": "patient-1" },
  "prefetch": {
    "patient": { "resourceType": "Patient", "id": "patient-1" },
    "observations": { "resourceType": "Bundle", "type": "searchset", "entry": [
      { "resource": { "resourceType": "Observation", "status": "final",
        "code": { "coding": [ { "system": "http://snomed.info/sct", "code": "271062006" } ] },
        "valueQuantity": { "value": 7.4, "system": "http://unitsofmeasure.org", "code": "mmol/L" } } }
    ] }
  }
}
```
Response (abbreviated):
```json
{ "cards": [ {
  "summary": "Diagnostic criterion met: Fasting plasma glucose at or above 7.0 mmol/L → Type 2 diabetes mellitus.",
  "indicator": "info",
  "source": { "label": "Guidelines for the Clinical Management of Type 2 Diabetes in Jamaica ..." }
} ] }
```

### Adding another diagnostic rule
1. Add the atomic criteria to `<domain>_criteria.tsv` (one row each), using ECL in `code_selector`.
2. Add a rule row to `<domain>_rules.tsv` referencing those criteria in `logic_expression`, and set the
   outcome, action, card indicator and templates, and source.
3. Restart the service. Structural problems (unknown criterion references, unbalanced parentheses, invalid
   operators/enums/numbers) fail startup with the offending file and row.

To add a whole new domain, drop a new `<name>_criteria.tsv` + `<name>_rules.tsv` pair into the directory.

### First-version limitations
1. This is a demonstration, not a production clinical rules engine.
2. The Boolean language is CQL-inspired but is **not** CQL.
3. Only the criterion types above are supported.
4. UCUM comparison requires matching units (no unit conversion).
5. HBPM/ABPM averages are expected to be pre-computed by the EHR.
6. National FHIR profile conventions are required for time aspects, aggregation and fasting/random context.
7. The engine evaluates only the data provided in the CDS Hooks request; missing data is `UNKNOWN`, not negative.
8. Supplied thresholds and combinations remain traceable to their source guideline.

## Running the Service
### Prerequisites
- Java 17
- Access to a FHIR Terminology Server capable of expanding [SNOMED CT Implicit Value Sets using ECL](https://www.hl7.org/fhir/snomedct.html#implicit).
  The diagnostic demo content resolves locally and runs without one; the medication services and any ECL
  operator selectors require it.

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

### Local Development CORS
For local frontend testing, a permissive CORS configuration is available through the `local` Spring profile.

Run with:
```
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

or:
```
java -jar target/snomed-fhir-cds-service-1.1.1.jar --spring.profiles.active=local
```

With the `local` profile active, the service allows cross-origin requests from any origin. By default, CORS remains disabled.

### Testing the Service
Download and import the [Postman Collection](Postman_collection.json) for API examples.
