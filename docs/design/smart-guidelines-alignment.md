# Design: Aligning the Rule Model with CQL and WHO SMART Guidelines

| | |
|---|---|
| **Status** | Draft for team review |
| **Date** | 2026-10-12 |
| **Scope** | Rule representation, execution engine, rule packaging, storage and sharing |
| **Decision requested** | Converge on the SMART Guidelines / FHIR Clinical Reasoning stack (validated by a time-boxed spike) instead of evolving our own TSV rule model |

---

## 1. Summary

The service currently executes clinical rules from project-specific TSV tables (`knowledge-bases/`). We explored
making those rules shareable through a two-way mapping with standard artifacts (CQL, `PlanDefinition`,
`ValueSet`) as used by [WHO SMART Guidelines](https://www.who.int/teams/digital-health-and-innovation/smart-guidelines)
and the CQF reference examples.

The analysis shows that:

1. Our criteria/rules model is already a simplified form of the SMART layering (L2 decision tables + data
   elements), and maps cleanly **out** to CQL.
2. Mapping **in** from real SMART content is only partial: a single WHO decision table (BCG) already needs
   at least four criterion types we do not support, plus a different missing-data semantics.
3. What makes this demonstrator valuable — SNOMED CT and ECL inside CDS — does not depend on having our own
   rule model. Every differentiator we have can be expressed within the standards.
4. The CQL engine and the Clinical Reasoning operations can be embedded **without a HAPI server**, using
   only HAPI's model libraries.

**Recommendation:** since the service is a demonstrator with no production users yet, freeze the TSV model
now and converge on the SMART / Clinical Reasoning stack, subject to a time-boxed spike (Section 9). Our
contribution then moves to where it is most valuable: SNOMED CT bindings for SMART data dictionaries, ECL
value sets, explicit data-binding decisions, and AI-assisted L2 → L3 authoring.

---

## 2. Current state

### 2.1 Rule families

| Family | Files | Logic | Notes |
|---|---|---|---|
| Diagnostic (diabetes, hypertension) | `<domain>_criteria.tsv` + `<domain>_rules.tsv` | Generic engine in `service/rules` | One CDS service per `service_id`, auto-registered at startup |
| Medication–condition, medication–medication | `CDS_Medication-*_Cards.tsv` | Generic Java (value set intersection) | Older, simpler model |
| Defined daily dose | `Snomed_Substance_DDDs.tsv` + dose-form map | ~470 lines of procedural Java | Reference data plus an algorithm, not a rule table |

### 2.2 The diagnostic rule model

- **Criteria** are named facts. There are four types: `OBSERVATION_VALUE`, `OBSERVATION_COMPONENT_VALUE`,
  `CODED_RESOURCE_PRESENT`, `CONTEXT_VALUE`. They carry explicit binding columns: `accepted_statuses`,
  units, semantic qualifiers, `min_occurrences`/`distinct_by`, and `missing_data_behavior`.
- **Rules** combine criteria with `AND`/`OR`/`NOT` under three-valued (Kleene) logic and declare an outcome,
  an action, a card and provenance.
- **Codes** are exact codes or ECL, expanded through a FHIR terminology server at startup.

[`AUTHORING_GUIDE.md`](../../knowledge-bases/AUTHORING_GUIDE.md) already presents the model as an L2-style
decision table inspired by CQL and SMART Guidelines, without being a conformant implementation of either.

### 2.3 Implicit logic not visible in the tables

Some decisions live only in Java:

- In `RuleBasedCDSService.isActive()`, a condition counts as active when its clinical status is `active`,
  `recurrence` or `relapse`, or when it has no clinical status. `entered-in-error` never counts.
- Outcome suppression is subtype-aware (`<< outcome_code`).
- Semantic qualifiers are looked up in `Observation.code`, `category`, `method` and `component[].code`.
- Units must match exactly. There is no UCUM conversion.
- `missing_data_behavior` is reserved: missing data is always `UNKNOWN`.

Whatever the target format, these decisions must be made explicit.

### 2.4 Registration and storage

Rules are read from a configurable directory (`rules.diagnostic.directory`), not compiled into the jar.
However, `DiagnosticServiceRegistrar` registers one Spring bean per domain at startup. Changing rules
therefore requires a restart.

---

## 3. Standards landscape

### 3.1 Layers (WHO SMART Guidelines)

| Level | Content | Typical format |
|---|---|---|
| L1 | Narrative guideline | Publication |
| L2 | Digital Adaptation Kit (DAK): data dictionary, decision tables, scheduling logic, indicators | Spreadsheets |
| L3 | Machine-readable: CQL `Library`, `PlanDefinition` (CPG profiles), `ActivityDefinition`, `ValueSet`, `ConceptMap`, tests | FHIR IG (often FSH) → **FHIR NPM package** |
| L4 | Executable in a system | Engine plus integration |

### 3.2 Reference repositories

**[cqframework/cqf-ccc](https://github.com/cqframework/cqf-ccc)** (Colorectal Cancer Concepts)

- A mature CQF example that runs on HAPI. It goes from L1 (USPSTF recommendation, CMS130/HEDIS) straight to
  L3. **There is no L2**: the specification page is a placeholder.
- Layering: `ColorectalCancerConcepts.cql` (value sets), `ColorectalCancerElements.cql` (facts),
  `ColorectalCancerScreeningCDS.cql` (logic) and `PlanDefinition` (`eca-rule`, `cpg-recommendationdefinition`)
  with `dynamicValue` for the card fields.

**[WorldHealthOrganization/smart-immunizations](https://github.com/WorldHealthOrganization/smart-immunizations)** (official WHO SMART IG)

- **L2 and L3 in the same repository:**
  - `input/L2/IMMZ DAK_core data dictionary.xlsx`
  - `input/decision-logic/IMMZ DAK_decision-support logic.xlsx` (29 sheets, one per vaccine)
  - `dak.json`
- L3 CQL in `input/cql/` (279 libraries). For each decision table there is an `IMMZD2DT<Vaccine>Elements`
  library and an `IMMZD2DT<Vaccine>Logic` library. `IMMZD18S*` libraries hold the schedules.
- `Elements` defines carry traceability annotations that link each define to its L2 cell: `@input`,
  `@pseudocode`, `@decision`, `@code`.
- Resources are written in FSH: 138 `PlanDefinition`s, 192 `ValueSet`s, plus CodeSystems and ConceptMaps.
- Tests are Karate `.feature` files plus FHIR bundles, **one scenario per L2 decision-table row**
  (e.g. `BCG07.4`). They call `PlanDefinition/$apply` and assert the expected guidance text.
- Terminology uses WHO DAK CodeSystems (`IMMZ.C`, `IMMZ.D`, `IMMZ.Z`, …). The ConceptMaps we inspected map
  to ICD-11 and FHIR codes, not to SNOMED CT. However, the published **ValueSets do include SNOMED CT codes**
  alongside the DAK, ICD-11 and ATC codes. For example, `IMMZ.Z.DE1` "BCG vaccines" includes
  `418268006 |Bacillus Calmette-Guerin antigen-containing product|`.

### 3.3 Exchange format

The standard unit of exchange is the **FHIR NPM package**: a `.tgz` containing `package.json` and the
resources as JSON. Packages are published to a registry (`packages.fhir.org`) and referenced as
`id#version`. CQL travels inside `Library.content` as `text/cql`, usually together with compiled
`application/elm+json`. Source repositories are not a stable input: they may need SUSHI, IG Publisher or
CQF tooling to build.

---

## 4. Findings

### 4.1 Mapping our model to the SMART layers

| Our model | cqf-ccc / CPG | SMART DAK |
|---|---|---|
| `code_selector` (code / ECL) | `*Concepts.cql`, `ValueSet` | Data dictionary, ValueSets |
| `*_criteria.tsv` (one named fact per row) | `*Elements.cql` (one `define` per element) | Data elements and decision-table inputs |
| `*_rules.tsv` (`logic_expression`) | `*Logic.cql` and `PlanDefinition.action.condition` | Decision-table rows |
| `outcome_status` / `action_type` | `ActivityDefinition` via `action.definitionCanonical` | CPG activity profiles |
| `card_summary` / `card_detail` | `dynamicValue` (title, description) | "Guidance displayed to health worker" |
| `source_label` / `source_url` | `relatedArtifact` / `documentation` | References, L1 traceability |
| `service_id` | One `PlanDefinition` | Decision table ID (e.g. `IMMZ.D2.DT.BCG`) |

**Mapping out** (tables → CQL) is total and deterministic for the current model. Three points need care:

- **Three-valued criteria.** CQL `exists` over an empty list returns `false`, not `null`. Generated criteria
  must return `null` explicitly when data is missing or cannot be interpreted.
- **`{{MatchedCriteria}}`.** The contributing criteria must be generated as an extra `define` derived from
  the rule's AST.
- **Units.** CQL converts UCUM units (`umol/L` vs `mmol/L`), whereas we require an exact match.

### 4.2 Mapping in: BCG as a test case

L2 decision tables have one column per input variable plus Output, Guidance, Annotations and References.
In a row, `–` means "any value", and blank cells inherit the value of the cell above (visual grouping).
Each row is a conjunction of its non-`–` cells, which corresponds to one of our rules.

| BCG input | L3 implementation | Our support |
|---|---|---|
| No BCG primary series dose administered | `Count(...) = 0` over `[Immunization]`, `status = 'completed'`, `isSubpotent is not true` | Partial: closed-world absence is not supported |
| Age ≤ 28 days / 28 days–5 years / ≥ 5 years | `Current Patient Age In Days/Years` | **Missing:** patient age |
| HIV status positive / negative-or-unknown | Coded `Observation.value` | **Missing:** coded observation value |
| Immunologically stable, on ART, clinically well | Most recent Observation, boolean value | **Missing:** boolean value, "most recent" selection |
| Live vaccine in the last 4 weeks | Date of latest live vaccine `< 4 weeks` | **Missing:** temporal lookback window |
| TB infection test positive / negative / unknown | Coded value; unknown = `IS NULL` | Partial |
| Contraindications (pregnancy, immunodeficiency…) | Coded Condition/Observation | Supported (`CODED_RESOURCE_PRESENT`) |

The outputs also go beyond alerts. They are recommendation states (Due, Not due, Further evaluation,
Complete, Contraindicated), with a `MedicationRequest` proposal and a `CommunicationRequest` carrying the
guidance.

### 4.3 Semantic differences

- **Absence of data.** SMART CQL is often closed-world:
  - `"No live vaccine was administered"` is `not exists(...)`;
  - "HIV negative or unknown" holds when there is no record.

  Our model is open-world (missing data → `UNKNOWN`). Both are defensible: closed-world suits an
  immunization registry, open-world suits diagnosis. Converging on CQL means writing this choice explicitly
  for each element.
- **L3 adds decisions absent from L2 and L1.** Examples:
  - cqf-ccc: `preliminary` results count as screening done; windows are measured from `issued`; the default
    measurement period is `Now() - 1 year`; absence of data triggers the recommendation; "history of"
    conditions are ignored by the CDS.
  - smart-immunizations: `isSubpotent`; only the most recent Observation is used; `Now()` is the time
    anchor.

  These are legitimate data-binding decisions, but they are hidden from clinical reviewers.
- **Clinical vs binding decisions.** Clinical decisions (thresholds, windows, missing-data policy) belong in
  L2 and must be reviewed. Binding decisions (FHIR element, status, profile) are legitimate in L3, but must
  be documented and traceable. Our criteria columns already expose many binding decisions at the table
  level. This is worth keeping and proposing upstream.

### 4.4 Terminology

SMART content is coded primarily in WHO DAK CodeSystems. Its ValueSets already enumerate some SNOMED CT codes,
but as explicit concept lists, without subsumption and with no SNOMED CT ConceptMaps. Running SMART logic over
SNOMED-coded records robustly requires SNOMED CT bindings for every data element, ideally as ECL-based
ValueSets (e.g. `<< 418268006` instead of two enumerated codes). This is both a prerequisite and a concrete
contribution SNOMED International could offer to WHO.

### 4.5 Running the engine without a HAPI server

| Component | Artifact | HAPI server needed? |
|---|---|---|
| CQL → ELM translator | `org.cqframework:cql-to-elm-jvm` | No |
| CQL engine | `org.cqframework:engine-jvm`, `engine-fhir-jvm` | No |
| Clinical Reasoning (`PlanDefinition/$apply`, `Library/$evaluate`) | `org.opencds.cqf.fhir:cqf-fhir-cr` (4.13.0) | **No.** Depends on `cqf-fhir-cql` and `cqf-fhir-utility`, which use HAPI model libraries (`hapi-fhir-base`, `structures-*`, `validation`, `client`) |
| HAPI JPA server integration | `cqf-fhir-cr-hapi` | Yes (`jpaserver-base`, `storage`, `server-cds-hooks`). **Not used** |
| Standalone CDS Hooks module | `cqf-fhir-cdshooks` | Abandoned (last pre-release 2023). **We write our own adapter** |

The engine works against a `Repository` abstraction, so we supply:

- an **in-memory data repository** built from the CDS Hooks prefetch;
- a **content repository** loaded from the FHIR package or IG directory. `cqf-fhir-utility` ships in-memory
  and IG-layout implementations; the exact classes are to be confirmed in the spike;
- a **terminology provider** that resolves ECL value sets through our FHIR terminology server, with
  start-up pre-expansion and caching (wrapping the existing `EclCodeResolver`).

**Version impact.** `cqf-fhir-cr` 4.13.0 is aligned with the HAPI 8.12.0 BOM and CQL BOM 5.4.0. We were on
HAPI 6.8.0 and Spring Boot 3.1.3. The spike (Section 9.1) settled the stack:

- HAPI 8.12.0, imported as a BOM.
- **Spring Boot 3.5.16.** The HAPI BOM pins Spring Framework 6.2.x, Jackson and JUnit; Boot 3.5 manages the
  same Spring generation, so nothing is mixed.
- **`kotlin.version` = 2.3.10.** The CQL engine is Kotlin multiplatform and needs this stdlib. Spring Boot
  would otherwise downgrade it to 1.8/1.9.
- **`hapi-fhir-caching-caffeine`** added explicitly. HAPI validation support (used for FHIRPath) needs a
  cache provider at runtime. A HAPI server brings it; an embedded engine does not (`HAPI-2200`).
- The executable jar grows from ~70 MB to ~250 MB (multi-version structures, validation resources, Kotlin).

---

## 5. Options considered

### Option A — Keep our model, add map in / map out

Tables remain the source of truth. A generator exports FHIR packages (map out). Foreign content is imported
through an AI-assisted, human-reviewed process (map in).

- ✅ No engine change, fast, explainable, works offline for code lists.
- ❌ Two semantics to keep provably equivalent, and two converters.
- ❌ The model must keep growing to chase SMART content: four or more new criterion types after a single
  decision table. This amounts to re-implementing CQL piecemeal.
- ❌ Map in is partial by nature. Map out still needs a CQL engine in CI to verify equivalence.

### Option B — Converge on SMART / Clinical Reasoning (recommended)

- **Authoring:** L2 decision tables plus a data dictionary extended with SNOMED CT / ECL binding columns.
- **Executable content:** L3 CQL, `PlanDefinition`, `ValueSet`, packaged as FHIR NPM packages.
- **Runtime:** `cqf-fhir-cr` embedded in this service, with our CDS Hooks adapter and an ECL terminology
  provider.

- ✅ Native interoperability with WHO / HL7 content and engines; the "map out" disappears.
- ✅ Effort goes into SNOMED CT value: bindings, ECL, terminology services.
- ✅ Existing fixtures, plus the SMART test bundles, serve as regression oracles.
- ❌ HAPI upgrade, heavier runtime, performance to be measured.
- ❌ L3 CQL is harder to author than our tables. This is mitigated by AI-assisted L2 → L3 generation
  validated against tests.

### Option C — Fallback: SMART L2 authoring, our executor

If the spike fails on performance or dependencies, keep our engine as executor but adopt the SMART L2 table
layout for authoring. The clinical authoring surface converges now, and the engine can be switched later.

### Why the differentiators do not require our own model

| Differentiator | Within the standards? |
|---|---|
| ECL and SNOMED subsumption | Yes: intensional `ValueSet` with ECL filter or implicit ECL value set URL |
| Three-valued logic (`UNKNOWN`) | Yes: CQL `null` with Kleene logic; it is a matter of authoring conventions |
| Visible binding decisions | Yes: data dictionary columns, proposed upstream to DAK authors |
| Explainability (matched criteria, evidence) | Yes, with generated defines |
| CDS Hooks delivery | Yes: part of the SMART stack |
| Spreadsheet authoring | Yes: L2 is spreadsheets |
| Runs without a CQL engine or terminology server | **No.** Not a goal for a SNOMED CT demonstrator |

---

## 6. Target architecture

```text
            Authoring                                   Runtime (this service)
┌──────────────────────────────────┐        ┌──────────────────────────────────────────┐
│ L2: decision tables + data       │        │ CDS Hooks endpoint                       │
│     dictionary (+ SNOMED/ECL     │        │   │ prefetch → in-memory data Repository │
│     binding columns)             │        │   ▼                                      │
│        │ AI-assisted, reviewed   │        │ cqf-fhir-cr  PlanDefinition/$apply       │
│        ▼                         │        │   │  content Repository (packages)       │
│ L3: CQL Libraries, PlanDefinition│ ─────▶ │   │  ECL TerminologyProvider (cached)    │
│     ValueSets (ECL), tests       │ .tgz   │   ▼                                      │
│        │ build (SUSHI/IG/CQF)    │        │ RequestGroup/CarePlan → CDS cards        │
│        ▼                         │        │ (+ package id#version, rule id)          │
│ FHIR NPM package                 │        └──────────────────────────────────────────┘
└──────────────────────────────────┘
```

### 6.1 Content conventions

Follow the smart-immunizations style:

- one `Elements` and one `Logic` library per decision table, with `@input`, `@pseudocode`, `@decision`
  annotations;
- `cpg-recommendationdefinition` plan definitions;
- ECL-based value sets;
- tests per decision-table row.

cqf-ccc is the minimal reference. The missing-data policy and binding decisions for each element are
documented in the data dictionary, not only in CQL.

### 6.2 Rule store

The unit of storage is the **versioned, immutable FHIR package**. A disk-based store is enough at this
stage:

```text
rules-store/
  packages/<id>-<version>.tgz
  active.json        # active id#version per service
```

- Activation is a pointer change, so rollback is trivial.
- On containers, the store lives on a persistent volume.
- The image ships the current content as seed.
- A database is only justified later (multi-tenant, UI editing, concurrency); with packages as the unit,
  moving to a database only changes where blobs are stored.
- A Git repository is a good companion for authoring: history, PR review and audit.

`DiagnosticServiceRegistrar` must be replaced by a **runtime service registry** so that services can be
added, updated and removed without a restart.

### 6.3 API (sketch)

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/rules/packages` | List installed and active packages |
| `GET` | `/rules/packages/{id}/{version}` | Download a package (sharing) |
| `POST` | `/rules/packages` | Upload a package; validated and installed **inactive** |
| `PUT` | `/rules/active/{service}` | Activate a version / roll back |
| — | sync | Pull `id#version` from another instance or a registry. Pull, not push: each instance decides what it accepts |

### 6.4 Clinical safety requirements

1. **Atomic, fail-safe loading.** A new version is rejected if it fails validation (compilation, ECL
   expansion, tests). The previous version keeps running.
2. **Install ≠ activate.** Activation is an explicit, auditable step (`PlanDefinition.status`).
3. **Traceability.** Every card records the package `id#version` and the rule or action that produced it.
4. **Terminology version.** Packages declare the SNOMED CT edition/version they were validated against. An
   instance warns or blocks on mismatch, because ECL expansions differ between editions.
5. **Authentication on write endpoints.** There is no authentication today. Packages exchanged between
   organisations should carry a checksum or signature.

---

## 7. Importing external SMART content

Importing SMART content is **not automatic**:

1. **Extract** (deterministic): unpack the package (`id#version`, `.tgz` or URL) and inventory plan
   definitions, libraries (decoded CQL, define dependency graph), value sets, ConceptMaps and tests. Read the
   L2 spreadsheets when available.
2. **Bind**: map the DAK data elements to SNOMED CT (ConceptMaps / ECL value sets).
3. **Review**: report divergences between L2 and L3, and every closed-world or binding decision, for
   clinical review.
4. **Validate**: run the package's own test bundles (e.g. the Karate scenarios) through our runtime with
   SNOMED-coded data.

---

## 8. Impact on existing content

| Content | Plan |
|---|---|
| Diabetes, hypertension (10 rules) | Re-express as L2 table + data dictionary with SNOMED/ECL bindings and L3 CQL. Verify against current fixtures, then retire the TSV engine |
| Medication–condition, medication–medication | Re-express as CQL over `MedicationRequest` and `Condition` with ECL value sets. Clarify whether medication–medication requires two distinct orders (today a single order can match both value sets) |
| Defined daily dose | Stays in Java, as a separate service or exposed to CQL as an external function |
| `AUTHORING_GUIDE.md` | Rewrite for the L2/L3 authoring flow once the spike succeeds |

---

## 9. Plan

### Phase 0 — Freeze

No new criterion types in the TSV model. Existing content keeps running.

### Phase 1 — Spike (1–2 weeks)

Embed `cqf-fhir-cr` in the service and run the smart-immunizations BCG decision table (`IMMZ.D2.DT.BCG`)
from a CDS Hooks request.

Success criteria:

- [x] HAPI model libraries upgraded to the version required by `cqf-fhir-cr`; existing test suite green.
- [x] BCG `PlanDefinition` executed via an in-memory data repository; the `IMMZD2DTBCG.feature` scenarios
      reproduced as JUnit tests with their bundles.
- [ ] Engine invoked from a CDS Hooks request (prefetch → data repository, `CarePlan` → cards).
- [ ] At least one ECL value set resolved through our terminology server via a custom terminology provider.
- [ ] One diabetes rule (e.g. `DM-2024-OGTT-01`) hand-written in SMART style; it produces the same results as
      the current engine on existing fixtures, including the `UNKNOWN` cases.
- [x] Warm-request latency measured for BCG: p95 well below the 300 ms target (see 9.1). To be re-measured
      with the diabetes content.
- [x] Jar size and dependency conflicts documented (Section 4.5).

Outcome: go to Option B, or fall back to Option C.

### 9.1 Spike results so far (2026-10-12, branch `spike/smart-cql-engine`)

**Setup**

- `PlanDefinitionEngine` (`service/cql`) runs `PlanDefinition/$apply` in-process with `cqf-fhir-cr` 4.13.0.
  It uses an in-memory content repository for the knowledge artifacts and a fresh in-memory data repository
  per call. No HAPI server, database or terminology server is involved.
- Fixtures (`src/test/resources/smart-immunizations/`): the `IMMZ.D2.DT.BCG` PlanDefinition and its dependency
  closure (49 resources: 11 Libraries with CQL + ELM, 32 ValueSets, 3 CodeSystems, 2 ActivityDefinitions),
  taken unchanged from the published package `smart.who.int.immunizations#1.0.0`, plus the IG's own test
  bundles and expected results.

**Results**

- **All 25 official BCG scenarios pass**: same guidance text, same medication proposal, same response
  shape. This covers Due, Not due, Further evaluation (TB test unknown) and Complete outcomes.
- **Latency:** warm `$apply` over 125 evaluations gives p50 ≈ 11–13 ms and p95 ≈ 20–23 ms (developer laptop).
  The first evaluation pays library compilation and value set expansion; caches are shared across calls.
- **Regression:** the full existing suite passes on the new stack (165 tests, including the 26 new ones).
  The packaged service boots and serves discovery as before.

**Findings to carry forward**

- `PlanDefinitionProcessor.apply()` reassigns its internal repository on every call, so it is **not
  thread-safe**. Create one processor per call and share `CrSettings`/`EvaluationSettings` (library and value
  set caches) instead.
- The in-memory `CarePlan` may contain the same request resource twice, with the same id. Serialization
  collapses them, which is what HTTP clients and the IG tests see. The CDS Hooks adapter must deduplicate
  contained resources by id.
- `EvaluationSettings.valueSetCache` (`url → codes`) is shared and can be pre-populated. It is the natural
  hook for ECL value sets expanded through our terminology server at load time.

### Phase 2 — Migrate content

Migrate diabetes, hypertension and medication rules to the SMART style. Write the CDS Hooks adapter and the
card mapping. Retire the TSV engine.

### Phase 3 — Packages, store, API, sync

Implement Sections 6.2–6.4.

### Phase 4 — SMART import and SNOMED contribution

Run the import pipeline (Section 7) on more DAK tables. Build AI-assisted L2 → L3 generation validated
against tests. Offer SNOMED CT bindings back to the DAK authors.

---

## 10. Open decisions

1. Approve convergence (Option B) subject to the spike, or keep Option A.
2. Missing-data policy: per-element explicit choice (closed- vs open-world), and how it is shown in L2.
3. Units: accept CQL UCUM conversion, or enforce exact units by convention.
4. `CONTEXT_VALUE` (e.g. `abpmHbpUnavailableOrImpractical`): keep as a CQL parameter fed from hook context,
   or remodel as a recorded Observation for portability.
5. Authoring source of truth: L2 spreadsheets in Git, or L3 with L2 as documentation.
6. Store: disk only, or Git-backed from the start.

---

## 11. References

- CQL specification — https://cql.hl7.org/
- FHIR Clinical Practice Guidelines IG (CPG) — https://hl7.org/fhir/uv/cpg/
- CDS Hooks 2.0 — https://cds-hooks.hl7.org/2.0/
- WHO SMART Guidelines — https://www.who.int/teams/digital-health-and-innovation/smart-guidelines
- smart-immunizations — https://github.com/WorldHealthOrganization/smart-immunizations
- cqf-ccc — https://github.com/cqframework/cqf-ccc
- Clinical Reasoning (cqf-fhir-cr) — https://github.com/cqframework/clinical-reasoning
- CQL translator and engine — https://github.com/cqframework/clinical_quality_language
- SNOMED CT in FHIR (implicit ECL value sets) — https://www.hl7.org/fhir/snomedct.html#implicit
