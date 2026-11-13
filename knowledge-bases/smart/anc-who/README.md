# Antenatal care contact decision support — WHO SMART ANC (pilot)

FHIR Clinical Reasoning content served by the embedded CQL engine as two CDS Hooks services, one for each end of
an antenatal contact (`context.encounterId`). Each returns one card per decision that applies.

| Service | Hook | Tables | Decides |
|---|---|---|---|
| `ANCContactStart` | `encounter-start` | `ANCDT01`, `ANCDT08`, `ANCDT11` | Proceed with the contact or refer; which tests to order |
| `ANCContactDischarge` | `encounter-discharge` | `ANCDT17`, `ANCDT23`, `ANCDT25` | Refer urgently; which counselling, with the iron and folic acid doses |

## Content

`PlanDefinition-ANCContactStart.json` and `PlanDefinition-ANCContactDischarge.json` are CPG StrategyDefinitions
composing six unmodified WHO decision tables from the WHO SMART Guidelines ANC implementation guide:

| Table | Topic | Decisions | WHO workflow step | Service |
|---|---|---|---|---|
| `ANCDT01` | Danger signs | Proceed with the contact; or refer (central cyanosis, other danger signs) | ANC.B5 Quick check | start |
| `ANCDT08` | HIV testing | Conduct HIV test; not required if HIV positive; optional in low-prevalence settings | ANC.B9 Laboratory tests | start |
| `ANCDT11` | Syphilis testing | Conduct syphilis test; not required (but treat) if syphilis positive | ANC.B9 Laboratory tests | start |
| `ANCDT17` | Pre-eclampsia, hypertension | Refer urgently to a hospital; hypertension counselling | ANC.B10.4 Diagnosis and treatment | discharge |
| `ANCDT23` | Gestational diabetes, diabetes | GDM counselling; DM counselling | ANC.B10.4 Diagnosis and treatment | discharge |
| `ANCDT25` | Anaemia | Anaemia counselling, with the iron and folic acid doses | ANC.B9 Laboratory tests | discharge |

The split follows the step each table declares as its trigger in the WHO workflow. The danger-sign triage
decides whether the contact goes on at all, and the tests are orders, useful before the laboratory work: both
belong at the start. Diagnosis and counselling need the examination and laboratory results of the contact: they
belong at its end. The WHO testing tables do not check whether the test was done, so at the end of the contact
they would repeat "Conduct HIV test". The WHO steps are finer than the two encounter hooks; a client driving the
Questionnaires can also call `ANCContactStart` again after a form (e.g. after ANC.B6 Profile, once the gestational
age is known).

The tables and their dependency closure (617 resources: 6 PlanDefinitions, 15 Libraries, 595 ValueSets,
2 CodeSystems) come from `smart.who.int.anc#0.3.0`, the IG's CI build (CC0-1.0). The ANC IG has no
release yet, and its pages are only on `build.fhir.org`, so cards link there (`cards.ig-page-bases`).

To keep the content small (3.6 MB instead of 23 MB), the Libraries keep only their CQL (the engine compiles it
at startup) and all resources drop their generated narrative. Nothing else is changed.

## Data model

- **The current contact.** The logic only uses data recorded in the current Encounter, passed as the CQL
  parameter `encounter`. Both hooks give it as `context.encounterId`, and the Encounter is prefetched by id
  (`Encounter/{{context.encounterId}}`). The contact number is the Encounter extension
  `http://smart.who.int/anc/StructureDefinition/contactNumber` (integer).
- **Data elements.** Every element is an Observation coded with an ANC code
  (`http://smart.who.int/anc/CodeSystem/anc-custom-codes`, e.g. `ANC.B5.DE48` *Danger signs*, `ANC.B9.DE186`
  *Blood haemoglobin test result*), with status `final`, `amended` or `corrected`, referencing the Encounter.
  The value is a CodeableConcept (an ANC code, e.g. `ANC.B5.DE51` *Central cyanosis*), a Quantity (UCUM, e.g.
  `mm[Hg]`, `mg/dL`, `g/L`, `wk`) or a boolean.
- **Questionnaires.** This shape (the data element's code, its answer as the value) is what the IG's Questionnaires
  produce through definition-based extraction: each item points at a profile of the element (e.g.
  `anc-b5-de48#Observation.value[x]`). The pilot's data come from ANC.B5 Quick check, ANC.B6 Profile, ANC.B8
  Physical exam and ANC.B9 Lab tests & imaging; the contact number from ANC.A Registration. CDS Hooks has no
  place for forms, so the client captures the data and sends the Observations; the services' `usageRequirements`
  name the Questionnaires.
- **Settings.** `ANCConfig` holds the national settings. It sets the HIV prevalence in pregnant women to 6 %,
  a high-prevalence setting, so women are tested at the first contact and again in the third trimester.

## SNOMED CT binding

The WHO value sets only contain ANC codes. `bindings/ConceptMap-ANCPilotSNOMEDBinding.json` is a curated binding of
the ANC codes the pilot uses to SNOMED CT (International Edition, 20260701). At startup the service adds the bound
SNOMED CT codes to each ANC value set (`ConceptMapBindings`), so the unmodified WHO logic also accepts SNOMED
CT-coded data, e.g. an Observation coded `167096006` |Plasma fasting glucose measurement| or a danger sign answered
with `95837007` |Central cyanosis|. Cards then report the SNOMED CT release in `terminologyVersions`.

The binding starts from the IG's ANC → SNOMED CT ConceptMap and corrects it. `bindings/README.md` reviews each of
its entries for the pilot: 19 accepted, 10 corrected, 4 inactive targets, 1 nonexistent code, 8 too wide to bind,
2 added. Two limits remain:

- ANC form questions ("Danger signs", "Syphilis diagnosis", …) have no SNOMED CT code. SNOMED CT answers to them are
  accepted, but a finding recorded on its own (Observation.code = the finding) is not.
- Repeat blood pressure readings stay ANC-only: bound to the same concept as the first reading, one reading would
  meet DT17's two-reading criteria alone.

## How it is served

As with the immunization schedule, the service applies each table separately and merges the cards. The tables'
actions create no resources, so cards come from the actions themselves:

- the summary is the decision (the action title);
- the detail gives the guidance, the other outcomes of the decision (e.g. *Provide counselling and treatment*,
  the iron dose), the table logic that was met (**Why:**) and the WHO recommendation (**Reference:**).

When data is missing, a decision's condition evaluates to null (unknown). The engine keeps such actions, flagged
with the CPG extension `cpg-action-condition-result`. The service gives them no card: a decision applies only when
its condition is true.

## Tests

WHO publishes no executable tests for this content (the IG's test data is stale). `SmartAncPilotScenariosTest`
uses the L2 logic each action states (the decision table, in the condition's description) as the oracle:
25 scenarios across the six tables, including data from another contact being ignored, plus 9 with SNOMED
CT-coded data, including codes WHO maps but the binding rejects (e.g. *Headache* for "Severe headache").

Known deviation of the WHO content: for a 2-hour 75 g OGTT, the L2 table gives GDM from 153 to 199 mg/dL, while
the CQL implements 180 to 199 mg/dL. A test pins the CQL behaviour, so a WHO fix will be noticed.
