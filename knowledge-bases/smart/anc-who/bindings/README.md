# SNOMED CT binding of the ANC pilot, and QA of the WHO map

The WHO SMART ANC value sets contain only ANC codes (`http://smart.who.int/anc/CodeSystem/anc-custom-codes`). To let the pilot's unmodified WHO logic accept SNOMED CT-coded data, `ConceptMap-ANCPilotSNOMEDBinding.json` binds the ANC codes the six pilot tables use to SNOMED CT (International Edition, 20260701). The CDS service adds the bound codes to each ANC value set that contains the ANC code.

The binding starts from WHO's own map (`http://smart.who.int/anc/ConceptMap/SNOMED-CT`, 0.3.0: 723 entries, 662 *equivalent*, 63 *relatedto*). Each of its entries for the pilot's 49 ANC codes was reviewed against the 20260701 release: is the target active, does it mean the same thing, and is it the right kind of concept for its place in the data (an observable or a measurement procedure for Observation.code, a finding or a qualifier for Observation.value). Inactive targets were followed through their historical associations (`ConceptMap/$translate` with `targetsystem=http://snomed.info/sct`).

## Summary

| Verdict | ANC codes | Meaning |
|---|---|---|
| Accepted | 19 | WHO's target is kept. |
| Corrected | 10 | WHO's target means something else; replaced, or left unbound. |
| Inactive target | 4 | WHO's target is inactive in 20260701; resolved through its historical associations where they fit. |
| Nonexistent code | 1 | WHO's target is not a SNOMED CT concept. |
| Not bound | 8 | WHO's target is wider than the ANC code (e.g. drops "severe"); binding it would make the logic fire on data that does not meet the guideline. |
| Added (no WHO map) | 2 | WHO has no map; a SNOMED CT concept fits. |
| No SNOMED CT code | 5 | WHO has no map, and no SNOMED CT concept fits (a form question rather than a clinical idea), or none is needed. |

## Findings for WHO

- **A nonexistent code.** `67096006` ("Plasma fasting glucose measurement") is not a SNOMED CT concept; `167096006` is.
- **Four inactive targets.** Hb (`118246004`, a navigational concept), pallor (`267029006`), no danger signs (`161915001`) and imminent delivery (`409049004`). Only pallor resolves cleanly through its historical associations.
- **Values mapped to qualifiers.** The urine protein grades +, ++, +++, ++++ and none are mapped to *Mild*, *Moderate*, *Severe*, *Deep* and *None*. SNOMED CT has a finding for each grade (`167275009` *Urine protein test = +*, …).
- **Parts of a test mapped to the test, a time or a regime.** OGTT 1-hour value → *Glucose tolerance test*; 2-hour value → *2 hours (qualifier value)*; fasting value → *Fasting (regime/therapy)*.
- **"Severe" dropped but marked *equivalent*.** Severe headache → *Headache*, severe vomiting → *Vomiting*, severe abdominal pain → *Abdominal pain*, severe pain → *Pain*, looks very ill → *Illness*. As danger signs they trigger referral, so a wider code is not harmless.
- **Gaps.** HIV status and urine dipstick protein have suitable SNOMED CT concepts but no map.

## Limits of a code-level binding

- **Form questions.** "Danger signs", "Symptoms of severe pre-eclampsia", "Syphilis diagnosis" and "Existing chronic health conditions" are questions whose answers are findings. A SNOMED CT-native record states the finding (Observation.code = *Central cyanosis*), not the question with an answer. The pilot accepts SNOMED CT answers to the ANC question codes; accepting the findings themselves needs the CQL data elements to change.
- **Repeat blood pressure.** The repeat readings map to the same concept as the first. Bound, one reading would count twice and meet DT17's two-reading criteria alone, so the repeat elements stay ANC-only. DT17's referral decisions therefore still need ANC-coded repeat readings.

## Data elements (Observation.code)

| ANC code | ANC display | WHO map | Verdict | Binding | Note |
|---|---|---|---|---|---|
| `ANC.B5.DE48` | Danger signs | — | No SNOMED CT code | — | A form question ("Danger signs") whose answers are findings. SNOMED CT has no observable for it, and a SNOMED CT-native record would state the finding itself (e.g. Observation.code = central cyanosis). Supporting that needs the CQL data element to change, not a code. |
| `ANC.B6.DE17` | Gestational age | `57036006` Fetal gestational age (observable entity) (equivalent) | Accepted | `57036006` Fetal gestational age (observable entity) |  |
| `ANC.B6.DE83` | Existing chronic health conditions | — | No SNOMED CT code | — | A form question ("Existing chronic health conditions"); in FHIR these are usually Conditions, not Observations. |
| `ANC.B7.DE1` | Medication follow-up | — | No SNOMED CT code | — | Not used by the pilot's decisions. |
| `ANC.B8.DE17` | Systolic blood pressure | `271649006` Systolic blood pressure (observable entity) (equivalent) | Accepted | `271649006` Systolic blood pressure (observable entity) |  |
| `ANC.B8.DE19` | Diastolic blood pressure | `271650006` Diastolic blood pressure (observable entity) (equivalent) | Accepted | `271650006` Diastolic blood pressure (observable entity) |  |
| `ANC.B8.DE20` | Repeat systolic blood pressure | `271649006` Systolic blood pressure (observable entity) (equivalent) | Not bound | — | Same concept as the first systolic reading. SNOMED CT has no concept for a repeat reading. If bound, one reading would count as both the first and the repeat measurement and meet the two-reading criteria of DT17 alone. Needs a data-model decision (e.g. the CQL taking the two earliest readings of one code), not a code. |
| `ANC.B8.DE21` | Repeat diastolic blood pressure | `271650006` Diastolic blood pressure (observable entity) (equivalent) | Not bound | — | As for the repeat systolic reading. |
| `ANC.B8.DE27` | Symptoms of severe pre-eclampsia | — | No SNOMED CT code | — | A form question, as "Danger signs". |
| `ANC.B8.DE38` | Pallor present | `267029006` Pale – symptom (finding) (equivalent) **inactive** | Inactive target | `398979000` Pale complexion (finding)<br>`1237486008` Pale discoloration of entire skin of body (finding) | WHO maps it to 267029006 \|Pale - symptom\|, inactive since 20221031 (ambiguous). Both targets of its POSSIBLY EQUIVALENT TO association are bound. |
| `ANC.B9.DE110` | Syphilis diagnosis | — | No SNOMED CT code | — | A form question ("Syphilis diagnosis"), as "Danger signs". |
| `ANC.B9.DE144` | Urine dipstick result - protein | — | Added (no WHO map) | `271346009` Urine dipstick for protein (procedure) | No WHO map. |
| `ANC.B9.DE163` | Fasting plasma glucose | `67096006` Plasma fasting glucose measurement (procedure) (equivalent) **does not exist** | Nonexistent code | `167096006` Plasma fasting glucose measurement (procedure) | The WHO target 67096006 does not exist in SNOMED CT. WHO gives it the display "Plasma fasting glucose measurement", which is 167096006, so the code looks mistyped. |
| `ANC.B9.DE164` | 75 g oral glucose tolerance test (OGTT) - fasting glucose | `792805006` Fasting (regime/therapy) (equivalent) | Corrected | — | WHO maps it to 792805006 \|Fasting (regime/therapy)\|, which is not a measurement. Not bound: the fasting sample of an OGTT is a fasting plasma glucose (167096006), already bound to "Fasting plasma glucose", which leads to the same DT23 decisions. |
| `ANC.B9.DE165` | 75 g oral glucose tolerance test (OGTT) - 1 hour | `113076002` Glucose tolerance test (procedure) (equivalent) | Corrected | `442260000` Measurement of glucose 1 hour after glucose challenge for glucose tolerance test (procedure) | WHO maps it to 113076002 \|Glucose tolerance test (procedure)\|, the whole test rather than its 1-hour value. |
| `ANC.B9.DE166` | 75 g oral glucose tolerance test (OGTT) - 2 hours | `123030002` 2 hours (qualifier value) (equivalent) | Corrected | `49167009` Measurement of glucose 2 hours after glucose challenge for glucose tolerance test (procedure) | WHO maps it to 123030002 \|2 hours (qualifier value)\|, a time qualifier. |
| `ANC.B9.DE167` | Random plasma glucose | `167095005` Plasma random glucose measurement (procedure) (equivalent) | Accepted | `167095005` Plasma random glucose measurement (procedure) |  |
| `ANC.B9.DE186` | Blood haemoglobin test result | `118246004` Laboratory test finding (navigational concept) (equivalent) **inactive** | Inactive target | `271026005` Hemoglobin level estimation (procedure) | WHO maps it to 118246004 \|Laboratory test finding\|, a navigational concept, inactive since 20240501 (ambiguous). Its historical associations (POSSIBLY EQUIVALENT TO Measurement finding, Evaluation finding) are no better: the map was wrong before the inactivation. |
| `ANC.B9.DE45` | HIV status | — | Added (no WHO map) | `278977008` Human immunodeficiency virus status (observable entity) | No WHO map. |

## Values (Observation.value)

| ANC code | ANC display | WHO map | Verdict | Binding | Note |
|---|---|---|---|---|---|
| `ANC.B5.DE49` | No danger signs | `161915001` No general symptom (situation) (relatedto) **inactive** | Inactive target | — | WHO maps it (relatedto) to 161915001 \|No general symptom\|, inactive since 20230531 (ambiguous). Its POSSIBLY EQUIVALENT TO target, 162467007 \|Free of symptoms\|, says more than "no danger signs". Not bound. |
| `ANC.B5.DE50` | Bleeding vaginally | `289530006` Bleeding from vagina (finding) (equivalent) | Accepted | `289530006` Bleeding from vagina (finding) |  |
| `ANC.B5.DE51` | Central cyanosis | `95837007` Central cyanosis (disorder) (equivalent) | Accepted | `95837007` Central cyanosis (disorder) |  |
| `ANC.B5.DE52` | Convulsing | `91175000` Seizure (finding) (equivalent) | Accepted | `91175000` Seizure (finding) |  |
| `ANC.B5.DE53` | Fever | `386661006` Fever (finding) (equivalent) | Accepted | `386661006` Fever (finding) |  |
| `ANC.B5.DE54` | Imminent delivery | `409049004` Delivery risk (finding) (equivalent) **inactive** | Inactive target | — | WHO maps it to 409049004 \|Delivery risk\|, inactive since 20230630, REPLACED BY 1269476005 \|At increased risk for complication during labor and delivery\|. Neither means imminent delivery. Not bound. |
| `ANC.B5.DE55` | Labour | `249123005` Onset of contractions (observable entity) (equivalent) | Corrected | — | WHO maps it to 249123005 \|Onset of contractions (observable entity)\|, an observable, not a value. Not bound. |
| `ANC.B5.DE56` | Looks very ill | `39104002` Illness (finding) (equivalent) | Not bound | — | WHO maps it (equivalent) to 39104002 \|Illness\|, which is much wider: any illness would be a danger sign. Not bound. |
| `ANC.B5.DE57` | Severe headache | `25064002` Headache (finding) (equivalent) | Not bound | — | WHO maps it (equivalent) to 25064002 \|Headache\|, dropping "severe": any headache would be a danger sign. Not bound. |
| `ANC.B5.DE58` | Severe pain | `22253000` Pain (finding) (equivalent) | Corrected | `76948002` Severe pain (finding) | WHO maps it (equivalent) to 22253000 \|Pain\|, dropping "severe". |
| `ANC.B5.DE59` | Severe vomiting | `422400008` Vomiting (disorder) (equivalent) | Not bound | — | WHO maps it (equivalent) to 422400008 \|Vomiting\|, dropping "severe". Not bound. |
| `ANC.B5.DE60` | Severe abdominal pain | `21522001` Abdominal pain (finding) (equivalent) | Not bound | — | WHO maps it (equivalent) to 21522001 \|Abdominal pain\|, dropping "severe". Not bound. |
| `ANC.B5.DE61` | Unconscious | `418107008` Unconscious (finding) (equivalent) | Accepted | `418107008` Unconscious (finding) |  |
| `ANC.B5.DE62` | Visual disturbance | `63102001` Visual disturbance (disorder) (equivalent) | Accepted | `63102001` Visual disturbance (disorder) |  |
| `ANC.B6.DE96` | Hypertension | `38341003` Hypertensive disorder, systemic arterial (disorder) (equivalent) | Accepted | `38341003` Hypertensive disorder, systemic arterial (disorder) |  |
| `ANC.B7.DE5` | Has side-effects from iron and folic acid supplements | `401207004` Medication side effects present (finding) (equivalent) | Accepted | `401207004` Medication side effects present (finding) |  |
| `ANC.B8.DE28` | No symptoms of severe pre-eclampsia | `162001003` No cardiovascular symptom (situation) (relatedto) | Not bound | — | WHO maps it (relatedto) to 162001003 \|No cardiovascular symptom\|, which is not the absence of pre-eclampsia symptoms. Not bound. |
| `ANC.B8.DE29` | Severe headache | `25064002` Headache (finding) (equivalent) | Not bound | — | WHO maps it (equivalent) to 25064002 \|Headache\|, dropping "severe". Not bound. |
| `ANC.B8.DE30` | Visual disturbance | `63102001` Visual disturbance (disorder) (equivalent) | Accepted | `63102001` Visual disturbance (disorder) |  |
| `ANC.B8.DE31` | Epigastric pain | `79922009` Epigastric pain (finding) (equivalent) | Accepted | `79922009` Epigastric pain (finding) |  |
| `ANC.B8.DE32` | Dizziness | `404640003` Dizziness (finding) (equivalent) | Accepted | `404640003` Dizziness (finding) |  |
| `ANC.B8.DE33` | Vomiting | `422400008` Vomiting (disorder) (equivalent) | Accepted | `422400008` Vomiting (disorder) |  |
| `ANC.B9.DE111` | Syphilis positive | `165781001` Syphilis titer test positive (finding) (equivalent) | Accepted | `165781001` Syphilis titer detected (finding)<br>`76272004` Syphilis (disorder) | WHO's lab finding is kept; the disorder is added, being what a diagnosis is recorded with. |
| `ANC.B9.DE145` | None | `260413007` None (qualifier value) (equivalent) | Corrected | `167273002` Urine protein not detected (finding) | WHO maps it to 260413007 \|None (qualifier value)\|. |
| `ANC.B9.DE146` | + | `255604002` Mild (qualifier value) (equivalent) | Corrected | `167275009` Urine protein test = + (finding) | WHO maps it to 255604002 \|Mild (qualifier value)\|. |
| `ANC.B9.DE147` | ++ | `6736007` Moderate (severity modifier) (qualifier value) (equivalent) | Corrected | `167276005` Urine protein test = ++ (finding) | WHO maps it to 6736007 \|Moderate (severity modifier)\|. |
| `ANC.B9.DE148` | +++ | `24484000` Severe (severity modifier) (qualifier value) (equivalent) | Corrected | `167277001` Urine protein test = +++ (finding) | WHO maps it to 24484000 \|Severe (severity modifier)\|. |
| `ANC.B9.DE149` | ++++ | `795002` Deep (qualifier value) (relatedto) | Corrected | `167278006` Urine protein test = ++++ (finding) | WHO maps it (relatedto) to 795002 \|Deep (qualifier value)\|. |
| `ANC.B9.DE46` | HIV positive | `165816005` Human immunodeficiency virus positive (finding) (equivalent) | Accepted | `165816005` Human immunodeficiency virus detected (finding) |  |
| `ANC.B9.DE47` | HIV negative | `165815009` Human immunodeficiency virus negative (finding) (equivalent) | Accepted | `165815009` Human immunodeficiency virus not detected (finding) |  |
