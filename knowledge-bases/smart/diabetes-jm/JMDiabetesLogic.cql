/*
 * Library: JMDiabetesLogic
 * Decision logic of the Jamaica 2024 type 2 diabetes diagnostic pathways, translated from
 * knowledge-bases/diabetes_rules.tsv. Rules combine the criteria of JMDiabetesElements with three-valued
 * (Kleene) and/or/not; a rule fires only when it evaluates to true.
 */
library JMDiabetesLogic version '0.1.0'

using FHIR version '4.0.1'

include FHIRHelpers version '4.0.1'
include JMDiabetesElements version '0.1.0' called E

context Patient

/*
@rule: DM-2024-OGTT-01
@pathway: Oral glucose tolerance test pathway
@logic: DM_OGTT_2H_GE_11_1_TWO_OCCASIONS OR (DM_OGTT_2H_GE_11_1 AND (DM_CLASSIC_HYPERGLYCAEMIC_SYMPTOMS_PRESENT OR DM_HYPERGLYCAEMIC_CRISIS_PRESENT))
*/
define "DM-2024-OGTT-01":
  E."DM_OGTT_2H_GE_11_1_TWO_OCCASIONS"
    or (E."DM_OGTT_2H_GE_11_1"
      and (E."DM_CLASSIC_HYPERGLYCAEMIC_SYMPTOMS_PRESENT" or E."DM_HYPERGLYCAEMIC_CRISIS_PRESENT"))

// Suppressed when the outcome (Type 2 diabetes mellitus, or a subtype of it) is already recorded.
define "DM-2024-OGTT-01 Applies":
  "DM-2024-OGTT-01" is true
    and not E."Type 2 diabetes mellitus recorded"

// The criteria on the path that made the rule true, in expression order.
define "DM-2024-OGTT-01 Matched Criteria":
  Combine(
    ({
      if E."DM_OGTT_2H_GE_11_1_TWO_OCCASIONS" is true
        then 'Two-hour OGTT plasma glucose at or above 11.1 mmol/L on two separate occasions' else null,
      if "OGTT With Symptoms Or Crisis" is true
        then 'Two-hour plasma glucose after 75 g OGTT at or above 11.1 mmol/L' else null,
      if "OGTT With Symptoms Or Crisis" is true and E."DM_CLASSIC_HYPERGLYCAEMIC_SYMPTOMS_PRESENT" is true
        then 'Classic symptoms of hyperglycaemia are present' else null,
      if "OGTT With Symptoms Or Crisis" is true and E."DM_HYPERGLYCAEMIC_CRISIS_PRESENT" is true
        then 'Hyperglycaemic crisis is present' else null
    }) L
      where L is not null,
    ', '
  )

define "OGTT With Symptoms Or Crisis":
  E."DM_OGTT_2H_GE_11_1"
    and (E."DM_CLASSIC_HYPERGLYCAEMIC_SYMPTOMS_PRESENT" or E."DM_HYPERGLYCAEMIC_CRISIS_PRESENT")

define "DM-2024-OGTT-01 Summary":
  'Diagnostic OGTT criterion met: ' + "DM-2024-OGTT-01 Matched Criteria" + ' → Type 2 diabetes mellitus.'

define "DM-2024-OGTT-01 Detail":
  'The two-hour OGTT criterion is diagnostic when supported by classic hyperglycaemic symptoms or hyperglycaemic crisis, or when an abnormal result is confirmed on a separate occasion.'

// The guidance shown to the clinician: the summary line, then the detail.
define "DM-2024-OGTT-01 Message":
  "DM-2024-OGTT-01 Summary" + '\n\n' + "DM-2024-OGTT-01 Detail"
