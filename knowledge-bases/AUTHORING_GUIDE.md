# Authoring Diagnostic CDS Rules — A Guide for Clinical Authors

This guide explains how to write **diagnostic decision-support rules** for this service, in plain terms,
without needing to read any code. If you can edit a spreadsheet and you know the clinical pathway you want
to encode, you can author a rule.

At heart this is a **simplified model for representing clinical rules**: two spreadsheet-like tables — one of
*facts* and one of *decisions* — combined with a small, fixed vocabulary and a three-valued (true / false /
unknown) logic. That deliberate simplicity is what lets a clinical author work without code and lets the
service run offline; it is also easier to use well once you know which established standards it borrows from,
so we start there before the how-to.

Rather than march through one example, the how-to then browses a **gallery of criteria** (one worked example of
every kind of clinical fact you can check), then a **gallery of rules** (how those facts become decisions and
cards), and finally assembles **one complete end-to-end example** from scratch. The supporting sections in
between are reference you dip into as needed.

## How it relates to existing CDS standards

This TSV model is not a new idea invented in isolation — it is a deliberately simplified, spreadsheet-friendly
take on two established standards. Understanding the lineage helps you see both what you gain (authorability,
offline execution, explainability) and what you give up (the full expressive power of the standards).

### Clinical Quality Language (CQL)

[CQL](https://cql.hl7.org/) is the HL7 author-facing language for clinical logic, used across quality measures
and CDS (the HL7 *Clinical Reasoning* module). Three of its design choices are mirrored here:

- **Definitions vs. logic.** CQL separates named, reusable *define* statements (facts) from the expressions
  that combine them. Our split of **criteria** (the facts) from **rules** (the logic) is the same idea, frozen
  into two files so it can be edited as a spreadsheet.
- **Three-valued logic.** CQL uses `null` for missing information and propagates it with three-valued (Kleene)
  logic, so "unknown" never silently becomes "false". Our TRUE / FALSE / **UNKNOWN** model (§2) is exactly this
  semantics, and for the same clinical safety reason.
- **Retrieve-and-filter over FHIR.** A CQL *retrieve* pulls resources of a type filtered by code and status,
  then tests their values. Each of our `criterion_type`s (`OBSERVATION_VALUE`, `CODED_RESOURCE_PRESENT`, …) is a
  fixed, table-driven version of one such retrieve-and-test.

**What we deliberately leave out:** CQL is a full expression language with libraries, temporal operators,
functions, and terminology operations. Here you get only the four criterion types and `AND` / `OR` / `NOT`
combination — no free-form expressions. That restriction is the point: a clinician can author in a spreadsheet,
the logic is trivially explainable, and (for concept lists and exact codes) it runs with no terminology server.
If your pathways outgrow the table model, CQL is the natural next step, and the concepts you learned here carry
over directly.

### WHO SMART Guidelines

The [WHO SMART Guidelines](https://www.who.int/teams/digital-health-and-innovation/smart-guidelines) turn
narrative guidance into computable form along a "knowledge ladder": L1 narrative → L2 operational (**decision
tables** and business-process models) → L3 machine-readable FHIR (`PlanDefinition`, `ActivityDefinition`,
`ValueSet`, and CQL) → L4 executable → L5 aggregate/dynamic.

Our two files sit at the **L2 decision-table** level, but made **directly executable**:

- A rules file *is* a decision table — inputs (the criteria) map to an output (outcome, certainty, action, and
  card) — the same shape WHO uses to render a guideline's decision-support logic before implementation.
- The insistence on **provenance** (`source_label` / `source_url` tracing every rule back to its guideline)
  mirrors SMART Guidelines' requirement that computable logic stay traceable to its L1 narrative source.
- Delivery is via **CDS Hooks**, the same mechanism SMART Guidelines uses to surface guidance inside an EHR.

So this service is a lightweight bridge: it lets you author L2-style decision tables and run them immediately,
without first building the full L3 FHIR Clinical Reasoning stack. It is **inspired by** these standards and
shares their semantics and separation of concerns; it is **not** a conformant CQL engine or a conformant SMART
Guidelines implementation. Treat it as an on-ramp — the mental models transfer when you move to the full tooling.

---

## 1. The mental model: *criteria* and *rules*

A clinical pathway is split into two kinds of things:

- **Criteria** = the individual clinical *facts* you look for. One criterion checks one thing, for example
  "is there a fasting glucose at or above 7.0 mmol/L?" or "does the patient already have this diagnosis?".
  Criteria are reusable building blocks.
- **Rules** = the *decisions*. A rule combines one or more criteria with plain logic ("this AND that", "this
  OR that") and says what should happen when the combination is true: what the outcome is, how certain it
  is, and what the alert card should say.

> Think of criteria as the ingredients and rules as the recipe.

Each clinical domain (diabetes, hypertension, …) has **two tab-separated files** that you edit like a
spreadsheet:

| File | Contains |
|------|----------|
| `<domain>_criteria.tsv` | the atomic criteria (the facts) |
| `<domain>_rules.tsv` | the rules (the decisions) |

For the diabetes example these are `diabetes_criteria.tsv` and `diabetes_rules.tsv`, in this
`knowledge-bases/` folder. Adding a brand-new domain is just dropping a new pair of files here — the service
picks them up automatically.

Each row is one criterion (or one rule). The first row of each file is the **header** of column names; do not
rename or reorder the header columns. Any column not mentioned for a given example is simply left blank.

---

## 2. Three-valued thinking: TRUE, FALSE, and UNKNOWN

Before the examples, one idea that governs everything below. Every criterion evaluates to **one of three**
answers, and the distinction matters clinically:

- **TRUE** — the data is present and meets the criterion.
- **FALSE** — the data is present and *contradicts* the criterion (e.g. a fasting glucose of 6.0).
- **UNKNOWN** — the data needed to decide is **missing or unusable** (no fasting glucose at all, or one in a
  unit we can't compare).

**Missing data is never treated as "no".** A card is only produced when a rule works out to TRUE. So when you
author a criterion, describe what *is* required and trust that absence yields UNKNOWN rather than a false
reassurance. This is also why "the patient does **not** have X" is hard to prove from a CDS Hooks prefetch — see
the presence/absence notes in §3.3 and the context pattern in §3.5.

> **A note on the `missing_data_behavior` column.** You will see this column in the TSV files (set to
> `UNKNOWN`). In this version that is the **only** accepted value, and the engine does not yet read it: the
> outcome on missing data is fixed (always UNKNOWN) by the evaluators, exactly as described here. The column
> exists so a future version could let a specific criterion treat missing data differently (for example as
> FALSE) without changing the file format. For now, always write `UNKNOWN` — you cannot turn "absent" into
> FALSE by changing this cell. (The example criteria below therefore omit it for brevity.)

---

## 3. A gallery of criteria

There are four **criterion types**. Each subsection below is a complete, real (or realistic) example you can
copy and adapt. The columns shown are the ones that matter for that type; **leave every other column blank**.

The full list of types:

| `criterion_type` | Checks | Typical resource |
|------------------|--------|------------------|
| `OBSERVATION_VALUE` | a numeric value on a result | `Observation` (§3.1) |
| `OBSERVATION_COMPONENT_VALUE` | a numeric value *inside* a multi-part result | `Observation` (§3.2) |
| `CODED_RESOURCE_PRESENT` | that a coded resource is present (or absent) | `Condition`, `Observation`, … (§3.3, §3.4) |
| `CONTEXT_VALUE` | a fact the EHR states explicitly in the request | *(context)* (§3.5) |

### 3.1 `OBSERVATION_VALUE` — a lab value crosses a threshold

*"A finalised fasting plasma glucose at or above 7.0 mmol/L."*

| Column | What it means | Value |
|--------|---------------|-------|
| `criterion_id` | A short unique name you invent, used to refer to this fact in rules. Letters, digits, underscores. | `DM_FBG_GE_7` |
| `label` | Human-readable description; appears in the card when the criterion matches. | `Fasting plasma glucose at or above 7.0 mmol/L` |
| `criterion_type` | The kind of fact. A lab value on an Observation. | `OBSERVATION_VALUE` |
| `resource_type` | The FHIR resource that carries the fact. | `Observation` |
| `code_system` | The code system for the test. Almost always SNOMED CT. | `http://snomed.info/sct` |
| `code_selector` | The SNOMED CT concept identifying the test (see §5). | `271062006` (Fasting blood glucose measurement) |
| `operator` | How to compare the value (see §4). "At or above" = `ge`. | `ge` |
| `value_low` | The threshold. | `7.0` |
| `lower_inclusive` | Is the threshold itself included? "at or above 7.0" = yes. | `true` |
| `unit_system` | The unit code system. Use UCUM. | `http://unitsofmeasure.org` |
| `unit_code` | The UCUM unit the value must be in. | `mmol/L` |
| `accepted_statuses` | Which Observation statuses count. Only trust finalised results. Separate with `\|`. | `final\|amended\|corrected` |
| `min_occurrences` | How many matching results are needed (see §4.3). | `1` |
| `missing_data_behavior` | Reserved; always `UNKNOWN` for now (see the note in §2). | `UNKNOWN` |
| `notes` | Free text for authors. Not shown to clinicians. | your rationale / source |

That's the whole criterion: *a final fasting-glucose Observation whose value is ≥ 7.0 mmol/L.* A result of 6.0
makes it FALSE; no fasting glucose at all makes it UNKNOWN.

### 3.2 `OBSERVATION_COMPONENT_VALUE` — a value *inside* a multi-part result

Some results bundle several values into one Observation as **components** — blood pressure carries systolic and
diastolic inside a single reading. Use this type so a systolic reading from one Observation is never mixed with
a diastolic reading from another.

> Blood pressure is the canonical case: representing it as **one Observation with systolic and diastolic
> `component[]` values** (rather than two separate Observations) is the standard pattern recommended by the
> FHIR specification and by profiles such as US Core *Blood Pressure*. That is why this criterion type exists —
> when the value lives inside a component, a plain `OBSERVATION_VALUE` on the parent code would see no value.

*"Clinic systolic between 140 and 180 mmHg (140 included, 180 excluded)."*

| Column | Value |
|--------|-------|
| `criterion_id` | `HTN_CLINIC_SYS_140_TO_LT_180` |
| `label` | `Clinic systolic blood pressure 140–179 mmHg` |
| `criterion_type` | `OBSERVATION_COMPONENT_VALUE` |
| `resource_type` | `Observation` |
| `code_selector` | `75367002` (Blood pressure) — the parent Observation |
| `component_code_system` | `http://snomed.info/sct` |
| `component_code` | `271649006` (Systolic blood pressure) |
| `operator` / `value_low` / `value_high` | `between` / `140` / `180` |
| `lower_inclusive` / `upper_inclusive` | `true` / `false` |
| `unit_system` / `unit_code` | `http://unitsofmeasure.org` / `mm[Hg]` |
| `accepted_statuses` | `final\|amended\|corrected` |

The exclusive upper end (`upper_inclusive = false`) deliberately keeps severe readings (≥ 180) out of a
"suspected" rule — a small choice that carries clinical meaning. You would pair this with a matching diastolic
criterion in the rule's logic (see §6.1).

### 3.3 `CODED_RESOURCE_PRESENT` — a diagnosis is already present

Here we don't look at a number; we check that a **coded clinical resource exists** in the record. The commonest
use is *"the patient already has this disease."*

*"An active diagnosis of diabetic hyperglycaemic crisis is recorded."*

| Column | What it means | Value |
|--------|---------------|-------|
| `criterion_id` | unique name | `DM_HYPERGLYCAEMIC_CRISIS_PRESENT` |
| `label` | shown in card | `Hyperglycaemic crisis is present` |
| `criterion_type` | presence of a coded resource | `CODED_RESOURCE_PRESENT` |
| `resource_type` | the resource that carries the diagnosis | `Condition` |
| `code_system` | code system | `http://snomed.info/sct` |
| `code_selector` | the concept(s) that count — a single code, or an `OR` list (see §5) | `420422005 \|Ketoacidosis due to diabetes mellitus\| OR 310505005 \|Hyperosmolar non-ketotic state...\| OR 441656006 \|Hyperglycemic crisis due to diabetes mellitus\|` |
| `operator` | for this type, `exists` or `not_exists` only | `exists` |
What "present" means here is deliberately strict — only **clinically real** resources count:

- A `Condition` counts only if it is **active** (clinical status `active`/`recurrence`/`relapse`, or absent) and
  **not** `entered-in-error`. An inactive or resolved condition does not match.
- The same evaluator serves other resource types too, each with its own relevance rule: `Procedure` (completed
  or in-progress), `Immunization` (completed), `MedicationRequest` (active), `AllergyIntolerance` (active,
  not entered-in-error). That makes care-gap phrasing possible — "is the procedure recorded?", "is this vaccine
  on file?" — without any engine change.

> **About `not_exists`.** You *can* set `operator = not_exists`, but read §2 first: a missing resource yields
> UNKNOWN, not FALSE, because the prefetch may simply be incomplete. So `not_exists` is TRUE only when the
> engine can positively see the resource is absent, and it will **not** fire a rule just because data wasn't
> sent. To assert a genuine "the patient does not have X", use an explicit context value (§3.5) that the EHR
> sends, rather than relying on absence.

### 3.4 `CODED_RESOURCE_PRESENT` on an Observation — a test was performed, whatever the result

The same type answers a different, purely **procedural** question: *"was this test done at all?"* — regardless
of what it showed. You check for the **presence of the Observation by its code**, and you set **no operator on
the value, no threshold, no unit**. This is the building block for care-gap and workup-completeness rules.

*"An HbA1c has been performed (any result)."*

| Column | What it means | Value |
|--------|---------------|-------|
| `criterion_id` | unique name | `DM_HBA1C_PERFORMED` |
| `label` | shown in card | `HbA1c has been performed` |
| `criterion_type` | presence, not value | `CODED_RESOURCE_PRESENT` |
| `resource_type` | we're checking a result exists | `Observation` |
| `code_system` | code system | `http://snomed.info/sct` |
| `code_selector` | the test concept | `43396009` (Haemoglobin A1c measurement) |
| `operator` | presence | `exists` |
| `accepted_statuses` | *(optional)* which statuses count as "done" | `final\|amended\|corrected` |
The difference from §3.1 is the whole point: §3.1 asks *"is the value ≥ 7.0?"* and needs `operator`/`value_low`/
`unit_code`; this asks *"is there an HbA1c result of any value?"* and leaves all of those blank. Combine it with
`not_exists` (mind the caveat in §3.3) to phrase *"HbA1c has **not** been done"* for a screening reminder — but
prefer an explicit context value when you truly need to assert absence.

### 3.5 `CONTEXT_VALUE` — a fact the EHR states explicitly

Some facts cannot be inferred from clinical data at all — they are decisions or circumstances the clinician /
EHR must **state**. The classic case: a fallback "confirm hypertension by repeat clinic readings" pathway is
only valid when ABPM/HBPM are genuinely unavailable, which is not something missing data can prove.

*"The EHR states that ABPM/HBPM are unavailable or impractical."*

| Column | Value |
|--------|-------|
| `criterion_id` | `HTN_ABPM_HBPM_UNAVAILABLE_OR_IMPRACTICAL` |
| `label` | `ABPM/HBPM unavailable or impractical (stated by EHR)` |
| `criterion_type` | `CONTEXT_VALUE` |
| `context_key` | `abpmHbpUnavailableOrImpractical` |
| `context_operator` | `eq` |
| `context_value` | `true` |
If the EHR does not send that key, the criterion is UNKNOWN and the fallback rule will not fire — exactly the
intended safeguard. This is the sanctioned way to encode "the patient does **not** have / cannot have X":
have the EHR say so, rather than inferring it from absence (§3.3).

---

## 4. Supporting reference for criteria

### 4.1 Comparing values (operators)

| Operator | Meaning | Needs |
|----------|---------|-------|
| `ge` | at or above `value_low` | `value_low` |
| `gt` | above `value_low` | `value_low` |
| `le` | at or below `value_low` | `value_low` |
| `lt` | below `value_low` | `value_low` |
| `eq` | equal to `value_low` | `value_low` |
| `between` | from `value_low` to `value_high` | `value_low`, `value_high`, `lower_inclusive`, `upper_inclusive` |
| `exists` | the coded resource is present | (for `CODED_RESOURCE_PRESENT`) |
| `not_exists` | the coded resource is absent (see §3.3 caveat) | (for `CODED_RESOURCE_PRESENT`) |

For `between`, `lower_inclusive`/`upper_inclusive` decide whether each end is included. Small choices like the
exclusive `< 180` upper bound in §3.2 carry clinical meaning, so set the flags with care.

### 4.2 Putting in SNOMED CT codes (the `code_selector`)

The `code_selector` is written as a **SNOMED CT expression** and is the single most important cell to get right.

- A **single concept** is just its identifier, optionally with the term for readability:
  `271062006 |Fasting blood glucose measurement|`. This matches exactly that concept.
- A **list of concepts** is written with `OR`:
  `28442001 |Polyuria| OR 17173007 |Excessive thirst| OR 267023007 |Excessive eating|`.
  This matches any of them. Use it to enumerate accepted symptom or diagnosis concepts (as in §3.3).

Both of the above are resolved **without any terminology server** — the service can run offline.

- If you want *a concept and all its subtypes*, prefix with `<<` (e.g. `<< 420422005` = diabetic ketoacidosis
  and all its subtypes). This is more powerful but **requires a terminology server** to expand, and the
  service will refuse to start if it cannot reach one for an enabled rule. Prefer explicit concept lists for
  offline demos; use subtype expressions when a terminology server is available and maintained.

> Tip: exact codes are matched exactly — a fasting-glucose criterion will not silently also match a subtype
> unless you ask for it with `<<`.

### 4.3 Repeated measurements (`min_occurrences` and `distinct_by`)

Some criteria require more than one qualifying result:

- `min_occurrences` — how many qualifying results are needed (e.g. `2`).
- `distinct_by` — how those results must differ:
  - `calendar_day` — they must fall on **different dates** (two readings on the same day count as one).
  - `encounter` — they must belong to **different encounters/visits**.
  - *(blank)* — no distinctness requirement.

Example: *"two-hour OGTT ≥ 11.1 mmol/L on two separate occasions"* uses `min_occurrences = 2` and
`distinct_by = calendar_day`. If the records don't carry enough date/encounter information to prove the
results are distinct, the criterion is **UNKNOWN** (not FALSE) — again, we never over-claim from incomplete data.

### 4.4 Semantic qualifiers (optional)

Some Observation criteria carry **semantic qualifiers** such as *24-hour study* (`time_aspect_code` `255250005`)
or *average* (`aggregation_code` `373098007`). These are honoured only when the EHR places the matching SNOMED
concept on the Observation; otherwise the criterion is UNKNOWN. Confirm with your integration team how your
EHR/profile represents these before relying on them.

---

## 5. A gallery of rules

A rule ties criteria together and describes the resulting card. Below are three real rules of increasing
richness; each row of `<domain>_rules.tsv` is one rule.

### 5.1 The columns of a rule

| Column | What it means | Example |
|--------|---------------|---------|
| `rule_id` | A unique name for the rule. | `DM-2024-FBG-01` |
| `card_uuid` | A unique identifier for the card. Generate a UUID (any online generator). | `0cf2aba7-faa4-5212-94ce-792d5ebd9681` |
| `version` | Your version string. | `1.0.0` |
| `enabled` | `true` to activate, `false` to keep as a draft. | `true` |
| `service_id` | The id of the CDS service this rule belongs to — it becomes the service published at the `/cds-services` discovery endpoint. **Read from the first data row and shared by every rule in the file**, so all rules in a domain must use the same value; it must be unique across domains (two files with the same `service_id` fail startup). Required for enabled rules. Use a stable, URL-safe identifier. | `diagnostic-support-diabetes` |
| `hook` | Which CDS Hooks *hook* the service responds to. **Also read from the first data row and shared per service** (not per rule). Free text in the file, but it must be a valid CDS Hooks hook name — this service's diagnostics are built around `patient-view` (see the main README on triggering). Required for enabled rules; defaults to `patient-view` if blank. | `patient-view` |
| `pathway` | A short human name for the pathway. **Optional, free text, documentation only** — not validated and not used by the engine. | `Fasting blood glucose pathway` |
| `trigger_event` | Descriptive only — documentation of the pathway. **Optional free text, not an enum.** | `Diabetes workup` |
| `logic_expression` | The logic that must be true (see §6). | `DM_FBG_GE_7` |
| `outcome_status` | How certain: `suspected`, `likely`, or `diagnostic`. | `diagnostic` |
| `action_type` | What to recommend (see §5.3). | `create_condition` |
| `outcome_code_system` | Code system of the outcome diagnosis. | `http://snomed.info/sct` |
| `outcome_code` | SNOMED concept for the outcome. | `44054006` (Type 2 diabetes mellitus) |
| `outcome_display` | Human-readable outcome name; fills `{{OutcomeDisplay}}`. | `Type 2 diabetes mellitus` |
| `suppress_if_outcome_present` | `true` = don't alert if the patient already has this diagnosis. | `true` |
| `card_indicator` | Card colour/severity: `info`, `warning`, or `critical`. | `info` |
| `card_summary` | The short card headline (keep under ~140 characters). | `Diagnostic criterion met: {{MatchedCriteria}} → {{OutcomeDisplay}}.` |
| `card_detail` | The longer card body (Markdown allowed). | see below |
| `source_label` | The guideline this rule comes from. | `Guidelines for the Clinical Management of Type 2 Diabetes in Jamaica` |
| `source_url` | A link to the guideline. | `https://ncdip.moh.gov.jm/resources/` |
| `implementation_notes` | Free text for authors. | your notes |

### 5.2 Three rules, from simple to combined

**A. Single criterion, diagnostic.** One fact is enough to record the diagnosis.

| Column | Value |
|--------|-------|
| `logic_expression` | `DM_FBG_GE_7` |
| `outcome_status` | `diagnostic` |
| `action_type` | `create_condition` |
| `card_indicator` | `info` |

**B. Value plus supporting evidence (AND / OR).** A random glucose only counts as diagnostic alongside classic
symptoms *or* a hyperglycaemic crisis:

| Column | Value |
|--------|-------|
| `logic_expression` | `DM_RANDOM_GLUCOSE_GE_11_1 AND (DM_CLASSIC_HYPERGLYCAEMIC_SYMPTOMS_PRESENT OR DM_HYPERGLYCAEMIC_CRISIS_PRESENT)` |
| `outcome_status` | `diagnostic` |
| `action_type` | `create_condition` |
| `card_indicator` | `info` |

**C. Lower certainty → recommend confirmation.** A point-of-care (glucometer) result should not diagnose on its
own, so the outcome is `likely` and we recommend confirmation with a lab test, drawing attention with `warning`:

| Column | Value |
|--------|-------|
| `logic_expression` | `DM_POC_FASTING_GLUCOSE_GE_7` |
| `outcome_status` | `likely` |
| `action_type` | `recommend_confirmation` |
| `card_indicator` | `warning` |
| `card_summary` | `Point-of-care criterion met: {{MatchedCriteria}} — diabetes is likely.` |

### 5.3 Outcomes, certainty, and what to recommend

Three columns express the clinical meaning of a firing rule; keep them consistent with one another:

- `outcome_status` — **certainty**: `suspected`, `likely`, or `diagnostic`.
- `action_type` — **what to do**:
  - `create_condition` — suggest recording the diagnosis (use with `diagnostic`).
  - `recommend_confirmation` — recommend confirming before diagnosing (use with `likely`).
  - `recommend_confirmatory_test` — recommend a specific confirmatory test (use with `suspected`).
- `suppress_if_outcome_present` — set `true` so the alert does not repeat once the patient already has the
  outcome recorded as an active condition. For SNOMED outcomes this is subtype-aware when a terminology server
  is available (a recorded *subtype* of the diagnosis also suppresses); otherwise it matches the exact
  `outcome_code_system` + `outcome_code`.

A `likely` or `suspected` rule should **not** use `create_condition`; recommend confirmation instead. Choose
`card_indicator` to match: `info` for informational, `warning` to draw attention, `critical` for urgent.

### 5.4 Card wording placeholders

In `card_summary` and `card_detail` you can use:

- `{{MatchedCriteria}}` — the labels of *only* the criteria that actually contributed to the rule becoming
  TRUE (not every criterion mentioned in the logic).
- `{{OutcomeDisplay}}` — the value of `outcome_display`.

---

## 6. Writing the logic expression

The `logic_expression` column combines criteria by their `criterion_id` using four words and parentheses:

```
AND    OR    NOT    (  )
```

Examples:

```
DM_FBG_GE_7
DM_RANDOM_GLUCOSE_GE_11_1 AND (DM_CLASSIC_HYPERGLYCAEMIC_SYMPTOMS_PRESENT OR DM_HYPERGLYCAEMIC_CRISIS_PRESENT)
```

### 6.1 Combining a value with a presence check

Because presence (§3.3, §3.4) and value criteria are just building blocks, you mix them freely. For a
care-gap-style rule — *"suspected diabetes but no HbA1c on file, so recommend one"* — you would write:

```
DM_RANDOM_GLUCOSE_GE_11_1 AND NOT DM_HBA1C_PERFORMED
```

Remember the §3.3 caveat: `NOT DM_HBA1C_PERFORMED` becomes TRUE only when the engine can positively confirm the
HbA1c is absent; a merely incomplete prefetch leaves it UNKNOWN and the rule does not fire.

Rules:
- **AND** binds tighter than **OR**. So `A OR B AND C` means `A OR (B AND C)`. When in doubt, add parentheses.
- Keywords are case-insensitive (`and` = `AND`).
- Every name you use must be a `criterion_id` that exists in the criteria file (spelling is checked at startup).

---

## 7. A complete worked example — fasting glucose, end to end

Putting the pieces together into one pathway: **"Fasting plasma glucose ≥ 7.0 mmol/L suggests Type 2 diabetes."**

**Step 1 — the criterion.** Add the §3.1 row to `diabetes_criteria.tsv`: a `OBSERVATION_VALUE` on Observation
`271062006`, `operator = ge`, `value_low = 7.0`, `lower_inclusive = true`, unit `mmol/L`,
`accepted_statuses = final|amended|corrected`, `min_occurrences = 1`, `missing_data_behavior = UNKNOWN`, with
`criterion_id = DM_FBG_GE_7`.

**Step 2 — the rule.** Add a row to `diabetes_rules.tsv`:

| Column | Value |
|--------|-------|
| `rule_id` | `DM-2024-FBG-01` |
| `card_uuid` | `0cf2aba7-faa4-5212-94ce-792d5ebd9681` |
| `version` / `enabled` | `1.0.0` / `true` |
| `service_id` / `hook` | `diagnostic-support-diabetes` / `patient-view` |
| `pathway` / `trigger_event` | `Fasting blood glucose pathway` / `Diabetes workup` |
| `logic_expression` | `DM_FBG_GE_7` |
| `outcome_status` / `action_type` | `diagnostic` / `create_condition` |
| `outcome_code_system` / `outcome_code` / `outcome_display` | `http://snomed.info/sct` / `44054006` / `Type 2 diabetes mellitus` |
| `suppress_if_outcome_present` | `true` |
| `card_indicator` | `info` |
| `card_summary` | `Diagnostic criterion met: {{MatchedCriteria}} → {{OutcomeDisplay}}.` |
| `card_detail` | `The fasting plasma glucose criterion for {{OutcomeDisplay}} is met. Recommend documenting the diagnosis and initiating management according to the national protocol.` |
| `source_label` / `source_url` | `Guidelines for the Clinical Management of Type 2 Diabetes in Jamaica` / `https://ncdip.moh.gov.jm/resources/` |

**Step 3 — run it.** Save both files and restart the service. Opening a patient chart whose record contains a
qualifying final fasting glucose now produces the card; if the patient already has an active Type 2 diabetes
diagnosis, `suppress_if_outcome_present = true` keeps the card from repeating. **That is a complete rule.**

*(A second worked example — combining a value with a presence check, or a care-gap pathway — will be added
here.)*

---

## 8. What the service checks when it starts

If something is wrong, the service refuses to start and tells you the **file and row**. It checks, among
other things:

- required columns are present, and `criterion_id` / `rule_id` / `card_uuid` are unique;
- every criterion named in a `logic_expression` exists, and parentheses are balanced;
- `criterion_type`, `operator`, `outcome_status`, `action_type`, `card_indicator` are valid values;
- `CODED_RESOURCE_PRESENT` criteria use `exists`/`not_exists`; `CONTEXT_VALUE` criteria use `eq`;
- numbers are valid, `between` has both bounds with `value_low < value_high` and both inclusive flags;
- enabled rules have a summary, detail, source, outcome display, and an outcome code where needed.

Nothing is silently skipped — a malformed *enabled* rule stops startup rather than quietly doing nothing.

---

## 9. Adding and testing a rule — checklist

1. Add the criteria to `<domain>_criteria.tsv` (one row each), with a SNOMED `code_selector`.
2. Add the rule to `<domain>_rules.tsv`, referencing those criteria in `logic_expression`.
3. Set `enabled = true`, choose the outcome, action, indicator, and write the card text with placeholders.
4. Cite the guideline in `source_label` / `source_url`.
5. Restart the service; fix any file/row error it reports.
6. Send a test `patient-view` request (see the main README for an example) with the relevant FHIR resources
   in `prefetch`, and confirm the card appears.

---

## Column quick reference

**Criteria file** — key columns: `criterion_id`, `label`, `criterion_type`
(`OBSERVATION_VALUE` \| `OBSERVATION_COMPONENT_VALUE` \| `CODED_RESOURCE_PRESENT` \| `CONTEXT_VALUE`),
`resource_type`, `code_system`, `code_selector` (SNOMED expression), `component_code_system`,
`component_code`, `operator` (`ge`\|`gt`\|`le`\|`lt`\|`eq`\|`between`\|`exists`\|`not_exists`), `value_low`,
`value_high`, `lower_inclusive`, `upper_inclusive`, `unit_system`, `unit_code`, `accepted_statuses`
(`\|`-separated), `time_aspect_code`, `aggregation_code`, `min_occurrences`, `distinct_by`
(`calendar_day` \| `encounter` \| blank), `context_key`, `context_operator`, `context_value`,
`missing_data_behavior` (`UNKNOWN` — reserved, not yet used; see §2), `notes`.

**Rules file** — key columns: `rule_id`, `card_uuid`, `version`, `enabled` (`true`\|`false`), `service_id`,
`hook` (`patient-view`), `pathway`, `trigger_event`, `logic_expression`, `outcome_status`
(`suspected`\|`likely`\|`diagnostic`), `action_type`
(`create_condition`\|`recommend_confirmation`\|`recommend_confirmatory_test`), `outcome_code_system`,
`outcome_code`, `outcome_display`, `suppress_if_outcome_present` (`true`\|`false`), `card_indicator`
(`info`\|`warning`\|`critical`), `card_summary`, `card_detail`, `source_label`, `source_url`,
`implementation_notes`.

---

*This is a demonstration rule model — a clear, editable, explainable way to encode diagnostic pathways. It is
not a substitute for clinical judgement, and every threshold and combination should remain traceable to its
source guideline.*
