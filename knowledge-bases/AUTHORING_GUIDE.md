# Authoring Diagnostic CDS Rules — A Guide for Clinical Authors

This guide explains how to write **diagnostic decision-support rules** for this service, in plain terms,
without needing to read any code. If you can edit a spreadsheet and you know the clinical pathway you want
to encode, you can author a rule.

We will build one complete, real example from scratch — **"Fasting plasma glucose ≥ 7.0 mmol/L suggests
Type 2 diabetes"** — and then look at a slightly richer blood-pressure example.

---

## 1. The mental model: *criteria* and *rules*

A clinical pathway is split into two kinds of things:

- **Criteria** = the individual clinical *facts* you look for. One criterion checks one thing, for example
  "is there a fasting glucose at or above 7.0 mmol/L?". Criteria are reusable building blocks.
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
rename or reorder the header columns.

---

## 2. Worked example, step 1 — write the criterion

We want to detect a **fasting plasma glucose at or above 7.0 mmol/L**. Open `diabetes_criteria.tsv` and add a
row. Filling the columns is like filling spreadsheet cells; here is what each cell means and what we put in it:

| Column | What it means | Our value |
|--------|---------------|-----------|
| `criterion_id` | A short unique name you invent, used to refer to this fact in rules. Letters, digits, underscores. | `DM_FBG_GE_7` |
| `label` | A human-readable description. This is what appears in the card when the criterion matches. | `Fasting plasma glucose at or above 7.0 mmol/L` |
| `criterion_type` | The kind of fact. For a lab value on an Observation, use `OBSERVATION_VALUE`. | `OBSERVATION_VALUE` |
| `resource_type` | The FHIR resource that carries the fact. | `Observation` |
| `code_system` | The code system for the test. Almost always SNOMED CT. | `http://snomed.info/sct` |
| `code_selector` | The SNOMED CT concept identifying the test (see §7). | `271062006` (Fasting blood glucose measurement) |
| `operator` | How to compare the value (see §6). "At or above" = `ge`. | `ge` |
| `value_low` | The threshold. | `7.0` |
| `lower_inclusive` | Is the threshold itself included? "at or above 7.0" = yes. | `true` |
| `unit_system` | The unit code system. Use UCUM. | `http://unitsofmeasure.org` |
| `unit_code` | The UCUM unit the value must be in. | `mmol/L` |
| `accepted_statuses` | Which Observation statuses count. Only trust finalised results. Separate with `\|`. | `final\|amended\|corrected` |
| `min_occurrences` | How many matching results are needed (see §8). | `1` |
| `missing_data_behavior` | What to do when the data is missing. Currently always `UNKNOWN`. | `UNKNOWN` |
| `notes` | Free text for authors. Not shown to clinicians. | your rationale / source |

Leave every other column blank. That's the whole criterion: *a final fasting-glucose Observation whose value
is ≥ 7.0 mmol/L.*

---

## 3. Worked example, step 2 — write the rule

Now open `diabetes_rules.tsv` and add a rule that fires when that criterion is met:

| Column | What it means | Our value |
|--------|---------------|-----------|
| `rule_id` | A unique name for the rule. | `DM-2024-FBG-01` |
| `card_uuid` | A unique identifier for the card. Generate a UUID (any online UUID generator). | `0cf2aba7-faa4-5212-94ce-792d5ebd9681` |
| `version` | Your version string. | `1.0.0` |
| `enabled` | `true` to activate the rule, `false` to keep it as a draft. | `true` |
| `service_id` | Which CDS service this rule belongs to. All rules for a domain share one. | `diagnostic-support-diabetes` |
| `hook` | When the EHR runs it. Use `patient-view` (see the main README on triggering). | `patient-view` |
| `pathway` | A short human name for the pathway. | `Fasting blood glucose pathway` |
| `trigger_event` | Descriptive only — documentation of the pathway. | `Diabetes workup` |
| `logic_expression` | The logic that must be true. Here just the one criterion (see §5). | `DM_FBG_GE_7` |
| `outcome_status` | How certain: `suspected`, `likely`, or `diagnostic`. | `diagnostic` |
| `action_type` | What to recommend (see §9). | `create_condition` |
| `outcome_code_system` | Code system of the outcome diagnosis. | `http://snomed.info/sct` |
| `outcome_code` | SNOMED concept for the outcome. | `44054006` (Type 2 diabetes mellitus) |
| `outcome_display` | Human-readable outcome name; fills `{{OutcomeDisplay}}`. | `Type 2 diabetes mellitus` |
| `suppress_if_outcome_present` | `true` = don't alert if the patient already has this diagnosis. | `true` |
| `card_indicator` | Card colour/severity: `info`, `warning`, or `critical`. | `info` |
| `card_summary` | The short card headline (keep it under ~140 characters). | `Diagnostic criterion met: {{MatchedCriteria}} → {{OutcomeDisplay}}.` |
| `card_detail` | The longer card body (Markdown allowed). | `The fasting plasma glucose criterion for {{OutcomeDisplay}} is met. Recommend documenting the diagnosis and initiating management according to the national protocol.` |
| `source_label` | The guideline this rule comes from. | `Guidelines for the Clinical Management of Type 2 Diabetes in Jamaica, ...` |
| `source_url` | A link to the guideline. | `https://ncdip.moh.gov.jm/resources/` |
| `implementation_notes` | Free text for authors. | your notes |

Save both files and restart the service. Opening a patient chart whose record contains a qualifying fasting
glucose will now produce a card. **That is a complete rule.** Everything below is detail you only need when
your pathway is more complex.

---

## 4. Three-valued thinking: TRUE, FALSE, and UNKNOWN

Every criterion evaluates to one of three answers, and the distinction matters clinically:

- **TRUE** — the data is present and meets the criterion.
- **FALSE** — the data is present and *contradicts* the criterion (e.g. a fasting glucose of 6.0).
- **UNKNOWN** — the data needed to decide is **missing or unusable** (no fasting glucose at all, or one in a
  unit we can't compare).

**Missing data is never treated as "no".** A card is only produced when a rule works out to TRUE. This is why
you should describe what *is* required, and trust that absence yields UNKNOWN rather than a false reassurance.

---

## 5. Writing the logic expression

The `logic_expression` column combines criteria by their `criterion_id` using four words and parentheses:

```
AND    OR    NOT    (  )
```

Examples:

```
DM_FBG_GE_7
DM_RANDOM_GLUCOSE_GE_11_1 AND (DM_CLASSIC_HYPERGLYCAEMIC_SYMPTOMS_PRESENT OR DM_HYPERGLYCAEMIC_CRISIS_PRESENT)
```

Rules:
- **AND** binds tighter than **OR**. So `A OR B AND C` means `A OR (B AND C)`. When in doubt, add parentheses.
- Keywords are case-insensitive (`and` = `AND`).
- Every name you use must be a `criterion_id` that exists in the criteria file (spelling is checked).

The alert's `{{MatchedCriteria}}` placeholder lists only the criteria that actually contributed to the rule
becoming TRUE — not every criterion mentioned.

---

## 6. Comparing values (operators)

| Operator | Meaning | Needs |
|----------|---------|-------|
| `ge` | at or above `value_low` | `value_low` |
| `gt` | above `value_low` | `value_low` |
| `le` | at or below `value_low` | `value_low` |
| `lt` | below `value_low` | `value_low` |
| `eq` | equal to `value_low` | `value_low` |
| `between` | from `value_low` to `value_high` | `value_low`, `value_high`, `lower_inclusive`, `upper_inclusive` |
| `exists` | the coded resource is present | (for `CODED_RESOURCE_PRESENT`) |
| `not_exists` | the coded resource is absent | (for `CODED_RESOURCE_PRESENT`) |

For `between`, `lower_inclusive`/`upper_inclusive` decide whether each end is included. For example the
suspected-hypertension range is `140 ≤ systolic < 180`: `lower_inclusive = true`, `upper_inclusive = false`.
The exclusive upper end deliberately keeps severe readings out of the "suspected" rule — small choices like
this carry clinical meaning, so set the flags with care.

---

## 7. Putting in SNOMED CT codes (the `code_selector`)

The `code_selector` is written as **SNOMED CT expression** and is the single most important cell to get right.

- A **single concept** is just its identifier, optionally with the term for readability:
  `271062006 |Fasting blood glucose measurement|`. This matches exactly that concept.
- A **list of concepts** is written with `OR`:
  `28442001 |Polyuria| OR 17173007 |Excessive thirst| OR 267023007 |Excessive eating|`.
  This matches any of them. Use this to enumerate, for example, the accepted symptom concepts.

Both of the above are resolved **without any terminology server** — the service can run offline.

- If you want *a concept and all its subtypes*, prefix with `<<` (e.g. `<< 420422005` = diabetic ketoacidosis
  and all its subtypes). This is more powerful but **requires a terminology server** to expand, and the
  service will refuse to start if it cannot reach one for an enabled rule. Prefer explicit concept lists for
  offline demos; use subtype expressions when a terminology server is available and maintained.

> Tip: exact codes are matched exactly — a fasting-glucose criterion will not silently also match a subtype
> unless you ask for it with `<<`.

---

## 8. Repeated measurements (`min_occurrences` and `distinct_by`)

Some criteria require more than one qualifying result:

- `min_occurrences` — how many qualifying results are needed (e.g. `2`).
- `distinct_by` — how those results must differ:
  - `calendar_day` — they must fall on **different dates** (two readings on the same day count as one).
  - `encounter` — they must belong to **different encounters/visits**.
  - *(blank)* — no distinctness requirement.

Example: *"two-hour OGTT ≥ 11.1 mmol/L on two separate occasions"* uses `min_occurrences = 2` and
`distinct_by = calendar_day`. If the records don't carry enough date/encounter information to prove the
results are distinct, the criterion is **UNKNOWN** (not FALSE) — again, we never over-claim from incomplete data.

---

## 9. Outcomes, certainty, and what to recommend

Three columns express the clinical meaning of a firing rule:

- `outcome_status` — **certainty**: `suspected`, `likely`, or `diagnostic`.
- `action_type` — **what to do**:
  - `create_condition` — suggest recording the diagnosis (use with `diagnostic`).
  - `recommend_confirmation` — recommend confirming before diagnosing (use with `likely`).
  - `recommend_confirmatory_test` — recommend a specific confirmatory test (use with `suspected`).
- `suppress_if_outcome_present` — set `true` so the alert does not repeat once the patient already has the
  outcome recorded as an active condition (matched on `outcome_code_system` + `outcome_code`).

A `likely` or `suspected` rule should **not** use `create_condition`; recommend confirmation instead. Choose
`card_indicator` to match: `info` for informational, `warning` to draw attention, `critical` for urgent.

### Card wording placeholders
In `card_summary` and `card_detail` you can use:
- `{{MatchedCriteria}}` — the labels of the criteria that made the rule fire.
- `{{OutcomeDisplay}}` — the value of `outcome_display`.

---

## 10. A richer example — hypertension (components and context)

Blood pressure adds two ideas. First, systolic and diastolic values live **inside** one Observation as
*components*, so the criterion type is `OBSERVATION_COMPONENT_VALUE` and you also fill:

| Column | Value (systolic) |
|--------|------------------|
| `criterion_type` | `OBSERVATION_COMPONENT_VALUE` |
| `code_selector` | `75367002` (Blood pressure) — the parent Observation |
| `component_code_system` | `http://snomed.info/sct` |
| `component_code` | `271649006` (Systolic blood pressure) |
| `operator` / `value_low` / `value_high` | `between` / `140` / `180` |
| `lower_inclusive` / `upper_inclusive` | `true` / `false` |
| `unit_code` | `mm[Hg]` |

The systolic and diastolic checks each stay tied to the *same* Observation, so a systolic reading from one
Observation is never mixed with a diastolic reading from another.

Second, the "confirm by repeat clinic readings" pathway depends on the clinician stating that ABPM/HBPM are
unavailable. That is not something you can infer from missing data, so it is an explicit **context** value the
EHR must send. Its criterion type is `CONTEXT_VALUE`:

| Column | Value |
|--------|-------|
| `criterion_type` | `CONTEXT_VALUE` |
| `context_key` | `abpmHbpUnavailableOrImpractical` |
| `context_operator` | `eq` |
| `context_value` | `true` |

If the EHR does not send that key, the criterion is UNKNOWN and the fallback rule will not fire — exactly the
intended safeguard.

Some blood-pressure criteria also carry **semantic qualifiers** such as *24-hour study* (`time_aspect_code`
`255250005`) or *average* (`aggregation_code` `373098007`). These are honoured only when the EHR places the
matching SNOMED concept on the Observation; otherwise the criterion is UNKNOWN. Confirm with your integration
team how your EHR/profile represents these before relying on them.

---

## 11. What the service checks when it starts

If something is wrong, the service refuses to start and tells you the **file and row**. It checks, among
other things:

- required columns are present, and `criterion_id` / `rule_id` / `card_uuid` are unique;
- every criterion named in a `logic_expression` exists, and parentheses are balanced;
- `criterion_type`, `operator`, `outcome_status`, `action_type`, `card_indicator` are valid values;
- numbers are valid, `between` has both bounds with `value_low < value_high` and both inclusive flags;
- enabled rules have a summary, detail, source, outcome display, and an outcome code where needed.

Nothing is silently skipped — a malformed *enabled* rule stops startup rather than quietly doing nothing.

---

## 12. Adding and testing a rule — checklist

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
`missing_data_behavior` (`UNKNOWN`), `notes`.

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
