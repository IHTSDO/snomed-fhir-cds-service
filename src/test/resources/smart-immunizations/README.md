# WHO SMART Immunizations — test scenarios

The IG's own test scenarios for the 11 WHO decision tables of the Jamaica schedule in
`knowledge-bases/smart/immunizations-jm/`.

Each `tests/<PlanDefinition id>/` directory holds the patient bundles of the IG's Karate tests
(`input/testing/features/plandefinition/IMMZ*/`, repository commit `bd7fa726706efc92d972d0dcebba9505f386b078`). It
also holds `expectations.json`, extracted from `<PlanDefinition id>.feature`, with:

- the evaluation date;
- for each scenario: the expected guidance text, the number of vaccine proposals, and the number of resources in
  the `$apply` response.

138 scenarios in total.

Source: https://github.com/WorldHealthOrganization/smart-immunizations — licensed CC0-1.0.
