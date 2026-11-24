/*
 * Library: JMDiabetesElements
 * The criteria (named clinical facts) of the Jamaica 2024 type 2 diabetes diagnostic pathways, translated from
 * knowledge-bases/diabetes_criteria.tsv. Each criterion is a Boolean define with three-valued semantics:
 *   true  - the data is present and meets the criterion;
 *   false - the data is present and contradicts the criterion;
 *   null  - unknown: the data is missing, or present but cannot be interpreted (unit, qualifier, distinctness).
 * Missing data never becomes false.
 */
library JMDiabetesElements version '0.1.0'

using FHIR version '4.0.1'

include FHIRHelpers version '4.0.1'

codesystem "SNOMEDCT": 'http://snomed.info/sct'

code "Glucose tolerance test": '113076002' from "SNOMEDCT" display 'Glucose tolerance test'
code "2 hours": '123030002' from "SNOMEDCT" display '2 hours'

// Intensional ValueSet resource with a SNOMED CT ECL filter (see ValueSet-dm-classic-hyperglycaemic-symptoms.json)
valueset "Classic hyperglycaemic symptoms": 'http://snomed.org/fhir/cds-demo/ValueSet/dm-classic-hyperglycaemic-symptoms'
// Implicit SNOMED CT ECL value sets
valueset "Hyperglycaemic crisis": 'http://snomed.info/sct?fhir_vs=ecl/(<< 420422005 |Ketoacidosis due to diabetes mellitus| OR << 310505005 |Hyperosmolar non-ketotic state due to diabetes mellitus| OR << 441656006 |Hyperglycemic crisis due to diabetes mellitus|) MINUS ((<< 420422005 OR << 310505005 OR << 441656006) : 42752001 |Due to| = (<< 46635009 |Type 1 diabetes mellitus| OR << 75524006 |Malnutrition related diabetes mellitus|))'
valueset "Type 2 diabetes mellitus": 'http://snomed.info/sct?fhir_vs=ecl/<< 44054006'

context Patient

/*
 * Shared conventions
 */

// A semantic qualifier (time aspect, aggregation) applies when its SNOMED CT concept is among the Observation's
// own codings: code, category, method or component codes.
// Note: flatten binds looser than union, so each flatten operand must be parenthesised.
define function "Has Qualifier"(O FHIR.Observation, Qualifier System.Code):
  exists (
    (O.code.coding
      union (flatten (O.category C return C.coding))
      union Coalesce(O.method.coding, List<FHIR.Coding>{})
      union (flatten (O.component C return C.code.coding))) K
      where K.system.value = Qualifier.system and K.code.value = Qualifier.code
  )

// Entered-in-error and inactive conditions are ignored; a condition without a clinical status counts.
define function "Is Clinically Relevant"(C FHIR.Condition):
  not exists (C.verificationStatus.coding V where Lower(V.code.value) = 'entered-in-error')
    and (
      not exists C.clinicalStatus.coding
        or exists (C.clinicalStatus.coding S where Lower(S.code.value) in { 'active', 'recurrence', 'relapse' })
    )

// The calendar day of an Observation: effective dateTime, else start of the effective period, else issued.
define function "Calendar Day"(O FHIR.Observation):
  Coalesce(
    date from (O.effective as FHIR.dateTime).value,
    date from (O.effective as FHIR.Period).start.value,
    date from O.issued.value
  )

/*
 * Two-hour plasma glucose after a 75 g OGTT (code 113076002 with the 2 hours time aspect), in mmol/L, from final,
 * amended or corrected Observations. Each Observation is classified as qualifying (value >= 11.1), failing,
 * indeterminate (qualifier not found, unit not mmol/L, or no value) or not applicable (no Quantity value).
 */
define function "OGTT Classification"(O FHIR.Observation):
  if not "Has Qualifier"(O, "2 hours") then 'indeterminate'
  else if not (O.value is FHIR.Quantity) then 'not applicable'
  else if Coalesce((O.value as FHIR.Quantity).system.value, '') != 'http://unitsofmeasure.org'
    or Coalesce((O.value as FHIR.Quantity).code.value, '') != 'mmol/L' then 'indeterminate'
  else if (O.value as FHIR.Quantity).value.value is null then 'indeterminate'
  else if (O.value as FHIR.Quantity).value.value >= 11.1 then 'qualifying'
  else 'failing'

define "OGTT Observations":
  [Observation] O
    where O.code ~ "Glucose tolerance test"

define "OGTT Classified Observations":
  "OGTT Observations" O
    where O.status.value in { 'final', 'amended', 'corrected' }
    return all Tuple { observation: O, classification: "OGTT Classification"(O) }

define "OGTT Qualifying Observations":
  "OGTT Classified Observations" R
    where R.classification = 'qualifying'
    return all R.observation

define "OGTT Has Indeterminate Observation":
  exists ("OGTT Classified Observations" R where R.classification = 'indeterminate')

define "OGTT Has Evaluated Observation":
  exists ("OGTT Classified Observations" R where R.classification in { 'qualifying', 'failing' })

/*
@criterion: DM_OGTT_2H_GE_11_1
@label: Two-hour plasma glucose after 75 g OGTT at or above 11.1 mmol/L
*/
define "DM_OGTT_2H_GE_11_1":
  if Count("OGTT Qualifying Observations") >= 1 then true
  else if "OGTT Has Indeterminate Observation" then null
  else if not exists "OGTT Observations" then null
  else if "OGTT Has Evaluated Observation" then false
  else null

define "OGTT Qualifying Days":
  ("OGTT Qualifying Observations" O return "Calendar Day"(O)) D
    where D is not null

define "OGTT Qualifying Observations Without Day":
  "OGTT Qualifying Observations" O
    where "Calendar Day"(O) is null

/*
@criterion: DM_OGTT_2H_GE_11_1_TWO_OCCASIONS
@label: Two-hour OGTT plasma glucose at or above 11.1 mmol/L on two separate occasions
*/
define "DM_OGTT_2H_GE_11_1_TWO_OCCASIONS":
  if Count("OGTT Qualifying Days") >= 2 then true
  else if "OGTT Has Indeterminate Observation"
    or (exists "OGTT Qualifying Observations Without Day"
      and Count("OGTT Qualifying Days") + Count("OGTT Qualifying Observations Without Day") >= 2) then null
  else if not exists "OGTT Observations" then null
  else if "OGTT Has Evaluated Observation" then false
  else null

/*
@criterion: DM_CLASSIC_HYPERGLYCAEMIC_SYMPTOMS_PRESENT
@label: Classic symptoms of hyperglycaemia are present
Presence is evidence; absence is unknown, because the prefetch may be incomplete.
*/
define "DM_CLASSIC_HYPERGLYCAEMIC_SYMPTOMS_PRESENT":
  if exists ([Condition] C where C.code in "Classic hyperglycaemic symptoms" and "Is Clinically Relevant"(C)) then true
  else null

/*
@criterion: DM_HYPERGLYCAEMIC_CRISIS_PRESENT
@label: Hyperglycaemic crisis is present
*/
define "DM_HYPERGLYCAEMIC_CRISIS_PRESENT":
  if exists ([Condition] C where C.code in "Hyperglycaemic crisis" and "Is Clinically Relevant"(C)) then true
  else null

// The patient already has type 2 diabetes mellitus, or a subtype of it, recorded as a relevant condition.
define "Type 2 diabetes mellitus recorded":
  exists ([Condition] C where C.code in "Type 2 diabetes mellitus" and "Is Clinically Relevant"(C))
