# Antenatal care contact decision support — WHO SMART ANC (pilot)

FHIR Clinical Reasoning content served by the embedded CQL engine as the CDS Hooks service
`ANCContactDecisionSupport` (`patient-view`). For the current antenatal contact it returns one card per decision
that applies: proceed or refer, which tests to conduct, which counselling is required.

## Content

`PlanDefinition-ANCContactDecisionSupport.json` is a CPG StrategyDefinition composing six unmodified WHO decision
tables from the WHO SMART Guidelines ANC implementation guide:

| Table | Topic | Decisions |
|---|---|---|
| `ANCDT01` | Danger signs | Proceed with the contact; or refer (central cyanosis, other danger signs) |
| `ANCDT08` | HIV testing | Conduct HIV test; not required if HIV positive; optional in low-prevalence settings |
| `ANCDT11` | Syphilis testing | Conduct syphilis test; not required (but treat) if syphilis positive |
| `ANCDT17` | Pre-eclampsia, hypertension | Refer urgently to a hospital; hypertension counselling |
| `ANCDT23` | Gestational diabetes, diabetes | GDM counselling; DM counselling |
| `ANCDT25` | Anaemia | Anaemia counselling, with the iron and folic acid doses |

The tables and their dependency closure (617 resources: 6 PlanDefinitions, 15 Libraries, 595 ValueSets,
2 CodeSystems) come from `smart.who.int.anc#0.3.0`, the IG's CI build (CC0-1.0). The ANC IG has no
release yet, and its pages are only on `build.fhir.org`, so cards link there (`cards.ig-page-bases`).

To keep the content small (3.6 MB instead of 23 MB), the Libraries keep only their CQL (the engine compiles it
at startup) and all resources drop their generated narrative. Nothing else is changed.

## Data model

- **The current contact.** The logic only uses data recorded in the current Encounter, passed as the CQL
  parameter `encounter`. The service takes it from `context.encounterId`, or else from the only in-progress
  Encounter in the prefetch. The contact number is the Encounter extension
  `http://smart.who.int/anc/StructureDefinition/contactNumber` (integer).
- **Data elements.** Every element is an Observation coded with an ANC code
  (`http://smart.who.int/anc/CodeSystem/anc-custom-codes`, e.g. `ANC.B5.DE48` *Danger signs*, `ANC.B9.DE186`
  *Blood haemoglobin test result*), with status `final`, `amended` or `corrected`, referencing the Encounter.
  The value is a CodeableConcept (an ANC code, e.g. `ANC.B5.DE51` *Central cyanosis*), a Quantity (UCUM, e.g.
  `mm[Hg]`, `mg/dL`, `g/L`, `wk`) or a boolean.
- **Settings.** `ANCConfig` holds the national settings. It sets the HIV prevalence in pregnant women to 6 %,
  a high-prevalence setting, so women are tested at the first contact and again in the third trimester.

The value sets only contain ANC codes. Binding the data elements to SNOMED CT (through the IG's ANC → SNOMED CT
ConceptMap) is the next step of the pilot.

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
25 scenarios across the six tables, including data from another contact being ignored.

Known deviation of the WHO content: for a 2-hour 75 g OGTT, the L2 table gives GDM from 153 to 199 mg/dL, while
the CQL implements 180 to 199 mg/dL. A test pins the CQL behaviour, so a WHO fix will be noticed.
