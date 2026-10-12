# WHO SMART Immunizations — BCG decision table

FHIR Clinical Reasoning content served by the embedded CQL engine. Each `PlanDefinition` in this directory is
published as a CDS Hooks service, with its prefetch derived from the PlanDefinition's data requirements.

The `PlanDefinition` for decision table `IMMZ.D2.DT.BCG` and its dependency closure are included: Libraries
with CQL and ELM, ValueSets, CodeSystems and ActivityDefinitions. They are taken unchanged from the published
package `smart.who.int.immunizations#1.0.0` (https://smart.who.int/immunizations/package.tgz, built 2026-10-02).

The IG's test scenarios for this table are in `src/test/resources/smart-immunizations/`.

Source: https://github.com/WorldHealthOrganization/smart-immunizations — licensed CC0-1.0.
