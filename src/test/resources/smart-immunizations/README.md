# WHO SMART Immunizations — test fixtures

Content used by the CQL engine spike to run a WHO SMART Guidelines decision table end to end.

- `content/` — the `PlanDefinition` for decision table `IMMZ.D2.DT.BCG` and its dependency closure
  (Libraries with CQL + ELM, ValueSets, CodeSystems, ActivityDefinitions), taken unchanged from the published
  package `smart.who.int.immunizations#1.0.0` (https://smart.who.int/immunizations/package.tgz, built 2026-10-02).
- `tests/IMMZD2DTBCG/` — the patient bundles of the IG's Karate tests
  (`input/testing/features/plandefinition/IMMZBCG/`, repository commit
  `bd7fa726706efc92d972d0dcebba9505f386b078`), and `expectations.json`, extracted from `IMMZD2DTBCG.feature`:
  the evaluation date, and for each scenario whether a medication proposal is expected and the guidance text.

Source: https://github.com/WorldHealthOrganization/smart-immunizations — licensed CC0-1.0.
