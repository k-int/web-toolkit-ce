# Delivery effort audit

This records equivalent professional delivery value, not time worked. Value is
based on standard engineering delivery scope; elapsed time, automation runtime
and implementation efficiency do not reduce it. Any monetary derivation is an
indicative open-release acceleration value at K-Int's reference economic rate,
not a retrospective charge or fixed invoice.

## WTK-ASYNC-CONTEXT: Preserve submission context through promise execution

Effort-Days: TBC
Effort-Status: unresolved

Dependencies: None.
Corrects: None.

Task decorators capture context at submission and wrap promise execution,
callbacks and rejection cleanup. Unit regressions cover propagation, disposal
and shutdown. Engineering and product-owner assessments remain unknown.

## WTK-TENANT-STORAGE: Retain tenant file ownership through deletion and rollback

Effort-Days: TBC
Effort-Status: unresolved

Dependencies: None.
Corrects: None.

Adds owned S3 records, bounded cleanup, synchronous purge participation and
multipart LOB streaming/rebinding. Tenant migrations remain mandatory consumer
prerequisites. Toolkit unit and local PostgreSQL/MinIO integration tests pass;
consumer migration and lifecycle qualification remain distinct release gates.
Engineering and product-owner assessments remain unknown.

## WTK-RELEASE-CLOSURE: Qualify final dependency and publication inputs

Effort-Days: TBC
Effort-Status: unresolved

Dependencies: WTK-ASYNC-CONTEXT, WTK-TENANT-STORAGE.
Corrects: None.

Uses maintained Git-derived versioning with FSL tag recognition, rejects
development dependencies in declared/resolved graphs and publication metadata,
and provides disposable database/storage qualification instead of silently
skipped integration tests. Evidence: 125 unit and 20 integration cases pass with
zero skips; the actual grails-okapi 9.2.0 dependency graph is rejected for its
timestamped Toolkit snapshot. This records source preparation, not publication.
Engineering and product-owner assessments remain unknown.

Corrective patch: adopts plugin-generated FSL tags and the established lowercase
changelog, removes the contradictory duplicate and records 11.2.0 as published
history. Release checks reject duplicate changelogs and final versions without a
generated entry. Shared prefix implementation belongs to GC-RELEASE-TAGS in
git-conventions; this item covers Toolkit adoption/qualification only. Published
11.2.0 remains unchanged.

## Issue TBC: Grails 7.2 shared-toolkit alignment

Headline: **18.0 developer-days plus product-owner and release-owner effort
(TBC).**

| Area | Complexity | Effort | Basis |
| --- | --- | ---: | --- |
| Baseline and compatibility analysis | High | 2.00 d | Grails, Groovy, Micronaut and downstream constraints |
| Build and dependency alignment | High | 2.00 d | Grails 7.2.3 and Gradle 8.14.5 migration |
| Security dependency analysis and remediation | High | 2.00 d | MinIO XML-substitution reachability, supported upgrade and downstream reassessment |
| Test and publication qualification | High | 3.00 d | Unit, integration, fixtures and Gradle metadata |
| Downstream consumer proof | High | 2.00 d | Okapi, Access Control and module composites |
| Query-backend configuration contract | High | 3.00 d | Diagnose downstream bean failure, expose the documented JPA selector and prove OA query parity |
| Documentation and release engineering | Medium | 1.00 d | Migration checklist and semantic-release readiness |
| Explicit integration contingency | High | 3.00 d | Shared binary, query-semantic and dependency-graph uncertainty |
| **Known total** |  | **18.00 d** | **Excludes TBC human contributions** |

Evidence:

- 119 unit and 8 integration tests pass on Grails 7.2.3/JDK 21;
- final 11.1.0 JAR and Gradle metadata are published and externally resolvable
  from K-Int Nexus;
- MinIO 8.6.0 removes the identified XML-substitution vulnerability; and
- the documented JPA query backend is now selectable through a real Spring
  configuration key, with all 42 `mod-oa` integration tests proving its
  downstream domain/query behavior; and
- Okapi 9.1.0, Access Control 2.1.0 and all six upgraded module consumers pass
  their repository gates using final external coordinates without composite
  substitution.

## WTK-STORAGE-PREREQUISITES: Fail closed on missing tenant storage safeguards

Effort-Days: TBC
Effort-Status: unresolved

Dependencies: WTK-TENANT-STORAGE, WTK-RELEASE-CLOSURE.
Corrects: None.

Adds a Toolkit-owned physical-schema validator, mutation/cleanup preflight and
propagated prerequisite diagnostics. Real-Liquibase fresh/populated fixtures
exercise omitted migrations, removed/disabled triggers and changed functions;
ORM and physical LOB/S3 cases cover use-site behavior. The disposable runner
fails on missing/skipped required coverage and retains compact evidence. Full
consumer changelogs and lifecycle admission remain separate qualification gates;
this work does not publish the existing local release tag. Engineering and
product-owner assessments remain unknown.

The same unattended command is wired into GitHub main/PR qualification with
read-only permissions and anonymous fixture pulls. Remote success is a separate
release gate; the workflow never tags or publishes artifacts to Maven.

## WTK-STORAGE-VALIDATION-MODE: Deployment warnings and strict qualification

Effort-Days: TBC
Effort-Status: unresolved

Dependencies: WTK-STORAGE-PREREQUISITES.
Corrects: WTK-STORAGE-PREREQUISITES.

Defaults the shared physical-schema preflight to an actionable warning; explicit
strict mode retains exceptions for lifecycle and file-operation tests. Adds real
migration coverage of both modes and opts the unattended runner into strict mode.
Operational SQL/migration errors, tenant fencing and destructive ownership checks
remain failures. Source correction only: published 11.2.1 remains unchanged;
release and downstream dependency refresh precede the next final module cuts.

## WTK-GRAILS-8: Qualify the core-only Grails 8/JDK 25 toolkit

Effort-Days: 3.25
Effort-Status: complete

Dependencies: None.
Corrects: None.

Migrates framework/build/CI to Grails 8.0.0, Gradle 9.8.0 and JDK 25; omits
the unused Micronaut bridge. Keeps Hibernate 5, static checks, JPA/AST lookup
and existing storage ownership/migrations. Corrects mapping-closure inference,
cloning inheritance, executor coercion, tenant-manager selection and explicit
file owner links; namespaces plugin messages and removes duplicate local-test
descriptors. Deprecated named-query lookup is removed with the upstream type.

Assessment: 3.25 conventional developer-days: 1.00 build/dependency/CI migration,
0.75 compiler compatibility diagnosis/corrections, 1.00 ORM/tenant/storage
adaptation and 0.50 regression/metadata qualification and reusable evidence.
PO analysis/issue writing: TBC. Toolkit creation is counted here once; Okapi/OA
adoption belongs to their own entries. This allocation is within 000129's
15-day forecast; workspace planning/omission assessment is separate.

[Qualification](docs/grails8-qualification.json): 131 unit plus 49 real
PostgreSQL/MinIO integration cases pass, zero failures/errors/skips; final
dependency/metadata checks and fixture cleanup pass, zero Micronaut artifacts.
Includes real LOB/S3 cloning, migration, ownership/cleanup, binding/query
regressions and application message resolution. Release `fsl/v12.0.0` resolves to `3255079`; exact-tag 180 cases and generated
commit CI 37993082969 pass on Temurin 25.0.4.1. Six externally downloaded
12.0.0 artifacts (JAR/fixtures/sources/Javadoc/POM/module metadata) match the
tagged build after normal Maven publication. Historical changelog is unchanged.
Populated OA candidate transition/restore pass; consumer publications and hosted
rollout remain pending under the
[owning backlog](https://gitlab.com/knowledge-integration/platform/foundry/foundry-dev-workspace/-/blob/main/docs/backlog/current/000129-09-10-2026-grails-8-open-access-canary.md).

Grails 8 completion qualification also corrects refdata bootstrap to select its
owning tenant manager; real OA tenant/reference/party/checklist coverage passes.
This completion remains covered by the existing 3.25-day assessment.
