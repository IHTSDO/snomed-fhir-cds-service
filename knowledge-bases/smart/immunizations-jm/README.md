# Jamaica routine immunization schedule — WHO SMART decision tables

FHIR Clinical Reasoning content served by the embedded CQL engine as the CDS Hooks service `JMImmunizationSchedule`
(`patient-view`). It returns one card per decision table that has guidance for the patient: due, not due yet,
further evaluation, or schedule complete.

## Content

`PlanDefinition-JMImmunizationSchedule.json` is a CPG StrategyDefinition: the national adaptation of the WHO SMART
Immunizations IG. The IG publishes alternative decision tables per antigen ("options" for countries), and this
StrategyDefinition picks one per antigen to match Jamaica's routine schedule.

The schedule comes from the MOHW *EPI Field Guide for Health Workers*, Vol. 1 (January 2024), Table 2.7:
https://www.moh.gov.jm/wp-content/uploads/2025/05/EPI-Field-Guide-For-Health-Workers-Volume-1-Vaccine-Administration-and-Programme-Management-Jan-2024.pdf

| Antigen | Jamaica | WHO decision table |
|---|---|---|
| BCG | 1 dose at birth (HIV exception) | `IMMZ.D2.DT.BCG` |
| Hepatitis B | Birth dose + 3 doses with DPT | `IMMZ.D2.DT.Hepatitis B.Birth dose`, `IMMZ.D2.DT.Hepatitis B.4 doses` |
| DPT | 6 wks, 3 and 6 months; boosters 18 months, 4–6 years | `IMMZ.D2.DT.DTP.On-time start`, `IMMZ.D2.DT.DTP.Delayed or interrupted series` |
| Polio | bOPV + IPV with DPT; boosters | `IMMZ.D2.DT.Polio.bOPV plus IPV` |
| Hib | 3 doses with DPT, no booster | `IMMZ.D2.DT.Hib.3 doses` |
| Measles, mumps, rubella (MMR) | 12 and 18 months | `IMMZ.D2.DT.Measles.Low transmission`, `IMMZ.D2.DT.Rubella.Low incidence`, `IMMZ.D2.DT.Mumps` |
| HPV | 1 dose, 9–14 years | `IMMZ.D2.DT.HPV.Single dose` |

Pneumococcal and rotavirus vaccines are not in Jamaica's public routine schedule.

The WHO tables are used **unmodified**. They are taken with their dependency closure (85 resources) from the
published package `smart.who.int.immunizations#1.0.0` (CC0-1.0). Known differences from the national schedule:

- **DPT, polio and Hib primary doses.** WHO schedules them at 6, 10 and 14 weeks; Jamaica at 6 weeks, 3 months and
  6 months.
- **Second MMR dose.** WHO schedules it at 15 months; Jamaica at 18 months.
- **Boosters.** Jamaica's polio and DPT boosters are not covered.
- **Hepatitis B delayed start.** For a child who missed the birth dose, the birth-dose table refers to WHO's
  delayed-start table, which is not included; the 4-dose table already proposes the first dose.

A real deployment would adapt these: set the national CQL parameters (e.g. `HepBLowerLimitWeeks`) and change the
ages fixed in the CQL. That content would no longer be WHO's, and would need its own tests.

## How it is served

The service applies each composed table separately and merges the cards, so each card cites its own table. The
engine's nested `$apply` is not used: the WHO tables share ActivityDefinitions, and their requests would collide by
id in a single CarePlan.

Each card links to:

- the table's page in the WHO IG (source);
- its CQL library page;
- the Jamaica schedule document.

## Tests

`SmartImmunizationsOfficialScenariosTest` runs the IG's own test scenarios for all 11 tables (138 scenarios, from
`src/test/resources/smart-immunizations/tests/`). Six DTP scenarios expect one vaccine proposal where the guidance
asks for two boosters (tetanus-diphtheria and pertussis). The IG's test server loses one because both get the same
id; this service keeps both.
