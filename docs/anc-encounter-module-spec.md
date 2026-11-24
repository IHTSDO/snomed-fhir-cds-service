# Spec: antenatal care contact module (EHR) driven by WHO SMART ANC Questionnaires

## Goal

An EHR module for one **antenatal care (ANC) contact**. It takes the clinician through the WHO SMART ANC
Questionnaires in the order of the WHO contact workflow. It records the answers as FHIR resources, and calls this
CDS service at the start and at the end of the contact to show WHO's decisions as cards.

The CDS service only evaluates data: CDS Hooks has no place for forms. The module owns the forms, the extraction
of answers into Observations, the encounter and the pregnancy episode.

The part of the contact that feeds the CDS services:

```
 Quick check ─▶ ANCContactStart ─ Profile ─ Physical exam ─ Lab tests ─▶ ANCContactDischarge
   (ANCB5)      encounter-start   (ANCB6)     (ANCB8)       (ANCB9)     encounter-discharge
```

Around it, Registration (ANCA) gives the contact number before the start. Confirm pregnancy (ANCB4) is answered
only at the first contact of a pregnancy; see "Pregnancy and contact". Symptoms (ANCB7) and the steps after
ANCContactDischarge (ANCB10 Counselling & treatment, ANCB11 Referral, ANCB12 Scheduling) are not read by the
services. ANCB10 is where the discharge cards are acted on.

## Sources

The Questionnaires come from the WHO SMART ANC implementation guide, version 0.3.0. This is a continuous-integration
build with no published release. Their canonical URLs (`http://smart.who.int/anc/Questionnaire/<id>`) do not
resolve (404). Download them from the IG build instead:

- **Whole package (recommended):** `https://build.fhir.org/ig/WorldHealthOrganization/smart-anc/package.tgz`. It
  holds the Questionnaires, their answer ValueSets (the `…-choices-grouper` sets and the sets they include), the
  data-element profiles and the extensions, as `package/<Type>-<id>.json`.
- **One resource:** `https://build.fhir.org/ig/WorldHealthOrganization/smart-anc/<Type>-<id>.json`, with its page at
  `….html`.

Pin the version you download: the CI build changes without notice.

## The Questionnaires

The Questionnaires are flat. They have no groups, no `enableWhen`, no units, and no extraction extensions. Every item
except one in ANCB12 has a `definition`, pointing at the element of a FHIR profile that the answer fills.

| Order | Id | Title | WHO workflow step | Items | Items the CDS service uses | JSON (relative to `https://build.fhir.org/ig/WorldHealthOrganization/smart-anc/`) |
|---|---|---|---|---|---|---|
| 1 | `ANCA` | ANC.A. Registration | Registration (patient, contact) | 12 | contact number, contact date | `Questionnaire-ANCA.json` |
| 2 | `ANCB4` | ANC.B4 Confirm pregnancy | Confirm pregnancy (first contact only) | 1 | — | `Questionnaire-ANCB4.json` |
| 3 | `ANCB5` | ANC.B5 Quick check | Triage | 3 | danger signs | `Questionnaire-ANCB5.json` |
| 4 | `ANCB6` | ANC.B6 Profile | History | 33 | gestational age, chronic conditions | `Questionnaire-ANCB6.json` |
| 5 | `ANCB7` | ANC.B7 Symptoms & Follow-up | Symptoms | 7 | — | `Questionnaire-ANCB7.json` |
| 6 | `ANCB8` | ANC.B8 Physical exam | Examination | 43 | blood pressure (×2), pre-eclampsia symptoms, pallor | `Questionnaire-ANCB8.json` |
| 7 | `ANCB9` | ANC.B9 Lab tests & imaging | Laboratory tests | 91 | HIV, syphilis, urine protein, glucose, haemoglobin | `Questionnaire-ANCB9.json` |
| 8 | `ANCB10` | ANC.B10 Counselling & treatment | Diagnosis, counselling, treatment | 127 | — (informed by the cards) | `Questionnaire-ANCB10.json` |
| 9 | `ANCB11` | ANC.B11 Referral needed? | Referral decision | 2 | — | `Questionnaire-ANCB11.json` |
| 10 | `ANCB12` | ANC.B12 Scheduling | Next contact | 1 | — | `Questionnaire-ANCB12.json` |
| — | `ANCC` | ANC.C. Referral | Referral (when a card or the clinician refers) | 11 | — | `Questionnaire-ANCC.json` |
| — | `ANCEnd` | ANC.End End | Closing the ANC record (end of pregnancy, not of a contact) | 11 | — | `Questionnaire-ANCEnd.json` |

## Pregnancy and contact

The IG separates the data of the **pregnancy**, recorded once, from the data of each **contact**:

| Level | FHIR | Recorded | Example |
|---|---|---|---|
| Pregnancy | **EpisodeOfCare** with `type` = `ANC.Case` *Antenatal care case* (`http://smart.who.int/anc/CodeSystem/anc-care-codes`, value set `antenatal-care-case`, profile `anc-episodeofcare`). Its `diagnosis.condition` (role chief complaint) is a **Condition** with code `ANC.Pregnancy` (value set `pregnancy-condition`). `period` spans the pregnancy. | Once, at the first contact | *Pregnancy confirmed* (ANCB4, `ANC.B4.DE1`): the WHO CQL reads it with no encounter filter |
| Contact | **Encounter** with `episodeOfCare` = the pregnancy's EpisodeOfCare and the contact-number extension. Its **Observations** have `encounter` = this Encounter. | Every contact | everything the CDS services read |

- **First contact of a pregnancy.** Create the EpisodeOfCare (`status = active`) and the pregnancy Condition when
  ANCB4 confirms the pregnancy. The contact number is 1.
- **Later contacts.** Do not show ANCB4. Find the active `ANC.Case` EpisodeOfCare of the patient, link the new
  Encounter to it, and set the contact number to the previous one plus 1. The module can propose this number;
  ANCA lets the clinician confirm it.
- **End of the pregnancy.** ANC.End closes the **episode**, not a contact (delivery, miscarriage or abortion,
  death): set `EpisodeOfCare.status = finished` and `period.end`.

The pilot's decision tables do not read the pregnancy confirmation or the episode. They assume the contact is
antenatal. The episode matters for the module (one record per pregnancy, the contact count, the stage of
pregnancy) and for other WHO ANC logic: `ANCBaseDataElements` defines *Pregnancy* as the most recent ANC case and
*Third trimester* from its period.

### Data to carry forward into each contact

The pilot tables read every data element **of the current contact only** (`Observation.encounter` = this
Encounter), including some that do not change between contacts. A value recorded at an earlier contact is not
seen:

| Data element | Form | Effect if not recorded again |
|---|---|---|
| Existing chronic health conditions (e.g. hypertension) | ANCB6 | DT17 does not see pre-existing hypertension, so no hypertension counselling from it |
| HIV status (positive) | ANCB9 | DT08 does not say *HIV test is not required* |
| Syphilis diagnosis (positive) | ANCB9 | DT11 does not say *No syphilis test required* (with its treatment) |

For these, the module pre-fills the item with the last known value from the pregnancy (or the patient's record).
The clinician confirms it, and the module records it as an Observation of the current contact. Values that change
at every contact (gestational age, blood pressure, laboratory results) are entered fresh. This is how the WHO logic
is written, not a limitation of the CDS service.

## Contact flow

1. **Start the contact.** Create an `Encounter` with `status = in-progress`, `subject = Patient/<id>`,
   `class = AMB` (`http://terminology.hl7.org/CodeSystem/v3-ActCode`) and `episodeOfCare` = the pregnancy's
   EpisodeOfCare (see above).
2. **ANCA Registration.** Item 4 *Contact date* fills `Encounter.period.start`. Item 11 *ANC contact number* (an
   integer, 1 for the first contact of the pregnancy) fills the contact-number extension on the Encounter; see
   "Known issues" for its URL. The patient items fill the Patient.
3. **ANCB4 Confirm pregnancy**, at the first contact only (it creates the pregnancy episode), then **ANCB5 Quick
   check.**
4. **Call `ANCContactStart`** (hook `encounter-start`). This is the danger-sign triage, and it says whether to
   order the HIV and syphilis tests.
   - A card *Proceed with ANC contact OR Referral…* means the clinician decides whether to continue or to refer.
     Referring goes to **ANCC**.
5. **ANCB6 Profile.** Optionally call `ANCContactStart` again. With the gestational age known, the
   third-trimester testing decisions (≥ 29 weeks) can apply.
6. **ANCB7**, **ANCB8 Physical exam**, **ANCB9 Lab tests & imaging.**
7. **Call `ANCContactDischarge`** (hook `encounter-discharge`) **before ANCB10**. WHO evaluates these decisions in
   ANC.B10.4 *Diagnosis and treatment*, so the cards (urgent referral, hypertension, GDM/DM and anaemia
   counselling, iron and folic acid doses) should guide the counselling and treatment form. Call it again when
   the contact is actually closed, as a final check.
8. **ANCB10**, **ANCB11** (→ **ANCC** if referred) and **ANCB12**. Then set `Encounter.status = finished` and
   `Encounter.period.end`.

The CDS calls are idempotent: the services only read the data sent. Calling one again after more forms is safe.

## Extracting answers into resources

Answers of items whose `definition` is `…/StructureDefinition/anc-<x>-de<n>#Observation.value[x]` become one
**Observation** each. This is definition-based extraction, implemented by the module:

| Observation element | Value |
|---|---|
| `status` | `final` (or `amended` / `corrected` when edited). The CDS logic ignores other statuses. |
| `code` | The data element's ANC code: `http://smart.who.int/anc/CodeSystem/anc-custom-codes`, code `ANC.<X>.DE<n>` (from the profile id, e.g. `anc-b5-de48` → `ANC.B5.DE48`). The profile binds `code` to a value set holding just that code. |
| `subject` | `Patient/<id>` |
| `encounter` | `Encounter/<id of this contact>`. **Required**: the CDS logic only reads Observations of the current contact. |
| `effectiveDateTime` | When the answer was recorded. |
| `value[x]` | The answer: `choice` → `valueCodeableConcept` (the answer Coding); `quantity` → `valueQuantity` (UCUM, see below); `boolean` → `valueBoolean`; `dateTime` → `valueDateTime`. |

Items whose `definition` points at `anc-encounter#…` (contact date, contact number, reason for coming) fill the
Encounter. Items pointing at `anc-patient#…` fill the Patient, and ANCB11's items fill a ServiceRequest. The CDS
service does not read these, apart from the Encounter.

**Units.** The quantity items declare none. Use these UCUM units (`system = http://unitsofmeasure.org`); the
thresholds of the WHO logic are in them:

| Data element | Unit |
|---|---|
| Gestational age | `wk` |
| Blood pressure | `mm[Hg]` |
| Plasma glucose (fasting, OGTT, random) | `mg/dL` |
| Haemoglobin | `g/L` (not g/dL: the thresholds are 110 and 105 g/L) |

**Multiple answers.** *Danger signs* and *Symptoms of severe pre-eclampsia* are single-answer `choice` items, but
more than one sign can be present. The WHO logic reads every Observation of the element in the contact, so record
one Observation per sign.

## Items the CDS service uses

| Questionnaire | linkId | Item | Type | `definition` (profile) | Answer value set | Service |
|---|---|---|---|---|---|---|
| ANCA | 11 | ANC contact number | integer | `anc-encounter#Encounter.contactNumber` | — | start |
| ANCB5 | 4 | Danger signs | choice | `anc-b5-de48` | `anc-b5-de48-choices-grouper` | start |
| ANCB6 | 6 | Gestational age | quantity (`wk`) | `anc-b6-de17` | — | start, discharge |
| ANCB6 | 20 | Existing chronic health conditions | choice | `anc-b6-de83` | `anc-b6-de83-choices-grouper` | discharge |
| ANCB8 | 9 | Systolic blood pressure | quantity (`mm[Hg]`) | `anc-b8-de17` | — | discharge |
| ANCB8 | 10 | Diastolic blood pressure | quantity (`mm[Hg]`) | `anc-b8-de19` | — | discharge |
| ANCB8 | 11 | Repeat systolic blood pressure | quantity (`mm[Hg]`) | `anc-b8-de20` | — | discharge |
| ANCB8 | 12 | Repeat diastolic blood pressure | quantity (`mm[Hg]`) | `anc-b8-de21` | — | discharge |
| ANCB8 | 15 | Symptoms of severe pre-eclampsia | choice | `anc-b8-de27` | `anc-b8-de27-choices-grouper` | discharge |
| ANCB8 | 20 | Pallor present | boolean | `anc-b8-de38` | — | discharge |
| ANCB9 | 19 | HIV status | choice | `anc-b9-de45` | `anc-b9-de45-choices-grouper` | start |
| ANCB9 | 47 | Syphilis diagnosis | choice | `anc-b9-de110` | `anc-b9-de110-choices-grouper` | start |
| ANCB9 | 58 | Urine dipstick result - protein | choice | `anc-b9-de144` | `anc-b9-de144-choices-grouper` | discharge |
| ANCB9 | 64 | Fasting plasma glucose | quantity (`mg/dL`) | `anc-b9-de163` | — | discharge |
| ANCB9 | 65 | 75 g OGTT - fasting glucose | quantity (`mg/dL`) | `anc-b9-de164` | — | discharge |
| ANCB9 | 66 | 75 g OGTT - 1 hour | quantity (`mg/dL`) | `anc-b9-de165` | — | discharge |
| ANCB9 | 67 | 75 g OGTT - 2 hours | quantity (`mg/dL`) | `anc-b9-de166` | — | discharge |
| ANCB9 | 68 | Random plasma glucose | quantity (`mg/dL`) | `anc-b9-de167` | — | discharge |
| ANCB9 | 77 | Blood haemoglobin test result | quantity (`g/L`) | `anc-b9-de186` | — | discharge |

Profile pages are at `https://build.fhir.org/ig/WorldHealthOrganization/smart-anc/StructureDefinition-<profile>.html`
and answer value sets at `…/ValueSet-<id>.json`.

The HIV and syphilis status (ANCB9) are inputs of the start service. On a first contact they are usually unknown at
the start, and the decision only needs the contact number. On later contacts, a status recorded in this contact
changes the decision; statuses from earlier contacts are not read.

## Calling the services

Base URL: the CDS service (e.g. `http://localhost:8080`). Discovery (`GET /cds-services`) lists both services with
their prefetch templates and `usageRequirements`.

| Service | Hook | When | Decides |
|---|---|---|---|
| `POST /cds-services/ANCContactStart` | `encounter-start` | after ANCB5 (and optionally after ANCB6) | proceed or refer; HIV and syphilis test orders |
| `POST /cds-services/ANCContactDischarge` | `encounter-discharge` | after ANCB9, before ANCB10; again on closing | urgent referral; hypertension, GDM/DM and anaemia counselling |

```json
{
  "hook": "encounter-start",
  "hookInstance": "<uuid>",
  "context": { "userId": "Practitioner/<id>", "patientId": "<patient id>", "encounterId": "<encounter id>" },
  "prefetch": {
    "patient": { "resourceType": "Patient", "id": "<patient id>", "gender": "female" },
    "encounter": {
      "resourceType": "Encounter", "id": "<encounter id>", "status": "in-progress",
      "class": { "system": "http://terminology.hl7.org/CodeSystem/v3-ActCode", "code": "AMB" },
      "subject": { "reference": "Patient/<patient id>" },
      "extension": [
        { "url": "http://smart.who.int/anc/StructureDefinition/contactnumber", "valueInteger": 1 },
        { "url": "http://smart.who.int/anc/StructureDefinition/contactNumber", "valueInteger": 1 }
      ]
    },
    "observation": { "resourceType": "Bundle", "type": "searchset", "entry": [ { "resource": { "resourceType": "Observation", "…": "…" } } ] }
  }
}
```

- **Prefetch keys:**
  - `patient`: the Patient;
  - `encounter`: this contact's Encounter, as a single resource (`Encounter/{{context.encounterId}}`);
  - `observation`: a searchset Bundle. Sending only this contact's Observations is enough; others are ignored.
- **Response:** `cards`, one per WHO decision that applies:
  - `summary`: the decision;
  - `detail`: the guidance, the decision's other outcomes, the WHO table logic that was met (**Why:**) and the WHO
    recommendation (**Reference:**);
  - `source`: the decision table's page in the IG;
  - `indicator`: from a curated overlay (`knowledge-bases/smart/anc-who/priorities/`), since the WHO tables give
    no priority:

    | Indicator | Decisions |
    |---|---|
    | `critical` | Danger signs (DT01 *Proceed with ANC contact OR Referral*, also *…for Central cyanosis*); DT17 *Refer urgently to a hospital* |
    | `warning` | DT17 *Conduct hypertension counselling*; DT23 *Conduct REQUIRED GDM / DM counselling*; DT25 *Conduct REQUIRED anaemia counselling* |
    | `info` | DT08 and DT11 test decisions (conduct, optional, not required, syphilis treatment); DT01 *Proceed with ANC contact*; DT25 *…IS OPTIONAL* |

    Cards come most severe first.

  A decision whose data is missing gives no card. Its absence does not mean the condition is ruled out.
- **Full examples:** see `Postman_collection.json`, the requests *ANCContactStart* and *ANCContactDischarge*.

## Terminology

- **Forms produce ANC codes.** The `answerValueSet`s contain only ANC codes. Send them as they are.
- **SNOMED CT data from the EHR is also accepted,** through the service's curated binding
  (`knowledge-bases/smart/anc-who/bindings/`):
  - many data elements can be coded with SNOMED CT instead (e.g. `167096006` for fasting plasma glucose);
  - many answers can be SNOMED CT codes (e.g. `95837007` central cyanosis).

  This is useful for data the EHR already holds, not needed for the forms.
- **Some data elements stay ANC-only.** The form questions (*Danger signs*, *Symptoms of severe pre-eclampsia*,
  *Syphilis diagnosis*, *Existing chronic health conditions*) and the repeat blood pressure readings must keep the
  ANC code as `Observation.code`. See `bindings/README.md`.

## Known issues in the WHO content (0.3.0)

- **Contact number extension URL.** The profile and the extension definition use
  `http://smart.who.int/anc/StructureDefinition/contactnumber`, all lower case. The 0.3.0 CQL that the pilot runs
  looks for `…/contactNumber`. WHO's newest branch (`br-changes-2026-07-28`) already uses `contactnumber`. Until
  the pilot moves to it, send **both** extensions with the same value, as in the example. With only the
  conformant one, the contact number is unknown and the decisions based on it (HIV and syphilis tests at the first
  contact) do not apply. This was checked against the running service.
- **No extraction metadata.** The Questionnaires have `definition` on each item but no SDC
  `itemExtractionContext`. Off-the-shelf SDC `$extract` implementations may not extract them; the module maps
  items to resources itself, as above.
- **No units** on quantity items: see the unit table.
- **No priorities.** No action of the decision tables sets `priority`, so the service supplies the indicators
  (see *Calling the services*).
- **DT25 "IS OPTIONAL" never applies.** Its conditions name `Should \"Anaemia counselling conducted\" IS OPTIONAL`
  with the backslashes, while the CQL define is the unescaped name. The engine cannot resolve them, so the
  routine supplementation card (no anaemia) is never returned; only *Conduct REQUIRED anaemia counselling* is.
- **OGTT 2-hour threshold.** For GDM, the WHO decision table says 153–199 mg/dL; the CQL implements 180–199 mg/dL.
  The service follows the CQL.

## Acceptance

- **Start, first contact.** A first contact (contact number 1) with *central cyanosis* among the danger signs.
  Calling `ANCContactStart` returns three cards: *Proceed with ANC contact OR Referral for Central cyanosis*
  (`critical`, first), *Conduct HIV test* and *Conduct syphilis test* (`info`).
- **Discharge.** At 30 weeks, BP 150/85 twice with urine protein *None*, fasting plasma glucose 100 mg/dL and
  haemoglobin 100 g/L. Calling `ANCContactDischarge` returns three `warning` cards: hypertension counselling, GDM
  counselling and anaemia counselling. It does not return *Conduct HIV test*.
- **Other contacts are ignored.** Observations whose `encounter` is another contact do not change the cards.
- **One episode per pregnancy.** A second contact of the same pregnancy links to the existing EpisodeOfCare,
  skips ANCB4, gets contact number 2, and has the chronic conditions and HIV and syphilis status pre-filled from
  the first contact.
- **Every card shows** its summary, its detail (rendered as Markdown) and a link to its source.
