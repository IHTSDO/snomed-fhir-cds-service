# Jamaica 2024 type 2 diabetes — SMART-style translation (pilot)

Rule `DM-2024-OGTT-01` (oral glucose tolerance test pathway) from `knowledge-bases/diabetes_*.tsv`, re-expressed
as FHIR Clinical Reasoning content in the style of WHO SMART Guidelines. The embedded CQL engine runs it and
publishes it as the CDS Hooks service `DM2024OGTT01`.

| File | Role |
|---|---|
| `JMDiabetesElements.cql` | Criteria (facts) with three-valued semantics: `true`, `false`, `null` = unknown |
| `JMDiabetesLogic.cql` | Rule expression, suppression, matched criteria and card text |
| `Library-*.json` | The same CQL embedded in FHIR Libraries. `SmartContentConsistencyTest` keeps them identical to the `.cql` files |
| `ValueSet-dm-classic-hyperglycaemic-symptoms.json` | Intensional SNOMED CT value set using the ECL filter (`constraint`) |
| `ActivityDefinition-JMDiabetesGuidance.json` | Guidance for the clinician as a `CommunicationRequest` (becomes the card) |
| `PlanDefinition-DM2024OGTT01.json` | ECA rule (`cpg-recommendationdefinition`): trigger, applicability condition, dynamic values |

Two SNOMED CT ECL forms are used:

- an intensional ValueSet resource (classic hyperglycaemic symptoms);
- implicit ECL value set URLs in CQL (hyperglycaemic crisis, type 2 diabetes mellitus and subtypes).

Their expansions are stored in `expansions/`, as FHIR ValueSets with a populated `expansion`. Each one records:

- the ECL it was expanded from;
- the SNOMED CT version (international edition 20260701);
- the concepts, with displays, sorted by code.

The service loads them from disk, pinned to that release, so it needs no terminology server for this content. To
re-expand them, for example after a new SNOMED CT release or an ECL change, start the service once with
`--terminology.expansions.refresh=true`. Then review the diff and commit it.

If an ECL in the content no longer matches its stored expansion, loading fails until the expansions are
refreshed. `SmartContentConsistencyTest` checks that every ECL has a stored expansion that matches the reviewed
terminology fixture.

`SmartDiabetesDifferentialTest` checks the translation: it runs 26 scenarios through both the TSV engine and this
content. The two must agree on every criterion's truth value, the rule's truth value, the card text and
suppression.

After editing a `.cql` file, re-embed it in its Library (`content[0].data` is the base64 of the file).

The canonical base `http://snomed.org/fhir/cds-demo` is a placeholder for the demonstrator.
