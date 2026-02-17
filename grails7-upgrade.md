# Grails 7 Upgrade Guidance (Downstream Modules)

This document summarizes downstream-impacting changes in `web-toolkit-ce` on branch `grails-7-upgrade`, with emphasis on `SimpleLookupService` query behavior.

## What Changed

- Plugin is migrated to Grails 7 and Hibernate-Criteria-era behavior has been replaced with JPA-backed query construction.
- `SimpleLookupService` now routes query behavior through explicit backend construction (`legacy` vs `jpa`) with JPA as the upgrade target.
- JPA filter execution is now AST-based (structured parse + table-driven operator semantics), and this AST mode is now default.
- `matchIn` and many filter-path cases now use schema-driven metamodel traversal instead of hardcoded fixture-path logic.
- Distinct/pagination behavior was stabilized for duplicate root rows (dedup by `id`, order-preserving).

## Required Downstream Actions

1. Upgrade to the Grails 7-compatible plugin version.
2. Confirm the service is using JPA backend (`queryBackend: jpa`) where `SimpleLookupService` is used.
3. Confirm AST gate is enabled (global default is on):
   - `k_int.webToolkit.query.jpa.astFilterParserEnabled: true`
4. Re-run your own query parity tests and saved-filter scenarios on your domain model.

## Configuration Notes

- Current global default in plugin config is:
  - `k_int.webToolkit.query.jpa.astFilterParserEnabled: true`
- Temporary environment-specific rollout overrides were removed after stabilization; downstream apps should not rely on env overrides for normal operation.
- If a module must temporarily diagnose behavior, it can still explicitly set the key above.

## SimpleLookupService Query Semantics

- Filters are parsed into an AST (`and`, `or`, `not`, atomic predicates).
- Atomic predicates are evaluated via an explicit operator table.
- Correlated handling is applied for relevant to-many array/conjunction cases to preserve same-row semantics where required for parity.
- Strict JPA mode remains fail-fast on unsupported paths/operators (no silent compatibility shim).

## Supported Generic Operator Families

- Null-style: `isNull`, `isNotNull`, `isSet`, `isNotSet`
- Comparisons: `==`, `!=`, `=i=`, `=~`, `!~`
- Comparators/ranges: `<`, `<=`, `>`, `>=`, chained range expressions

## Intentional Compatibility Exceptions

These remain intentionally special/test-locked for parity with legacy behavior:

- `checklists.items.* != ...` widening behavior
- `checklists.items.* isEmpty|isNotEmpty` handling
- Selected unsupported `isEmpty|isNotEmpty` combinations continue to fail fast in strict JPA mode

## Red-Line Guardrail

- The test-tree data model in this repository is an exemplar for tests, not a downstream specification.
- Plugin implementation must stay schema-agnostic and must not require downstream apps to mirror this repository's test paths.
- Downstream validation must be done against each consuming app's own domain model.

## Downstream Validation Checklist

- [ ] Upgrade plugin dependency and verify startup.
- [ ] Set/confirm `queryBackend=jpa`.
- [ ] Set/confirm `k_int.webToolkit.query.jpa.astFilterParserEnabled=true`.
- [ ] Run clean no-cache build in downstream module.
- [ ] Run query parity tests for grouped filters (`&&`, `||`, `!`) and to-many filters.
- [ ] Verify paged screens/endpoints for duplicate-row collapse behavior.
- [ ] Verify saved filters that previously relied on Hibernate Criteria behavior.
