# Delivery effort audit

This records equivalent professional delivery value, not time worked. Value is
based on standard engineering delivery scope; elapsed time, automation runtime
and implementation efficiency do not reduce it. Any monetary derivation is an
indicative open-release acceleration value at K-Int's reference economic rate,
not a retrospective charge or fixed invoice.

## Issue TBC: Grails 7.2 shared-toolkit alignment

Headline: **14.0 developer-days plus product-owner and release-owner effort
(TBC).**

| Area | Complexity | Effort | Basis |
| --- | --- | ---: | --- |
| Baseline and compatibility analysis | High | 2.00 d | Grails, Groovy, Micronaut and downstream constraints |
| Build and dependency alignment | High | 2.00 d | Grails 7.2.3 and Gradle 8.14.5 migration |
| Security dependency analysis and remediation | High | 2.00 d | MinIO XML-substitution reachability, supported upgrade and downstream reassessment |
| Test and publication qualification | High | 3.00 d | Unit, integration, fixtures and Gradle metadata |
| Downstream consumer proof | High | 2.00 d | Okapi, Access Control and module composites |
| Documentation and release engineering | Medium | 1.00 d | Migration checklist and semantic-release readiness |
| Explicit integration contingency | High | 2.00 d | Shared binary and dependency-graph uncertainty |
| **Known total** |  | **14.00 d** | **Excludes TBC human contributions** |

Evidence:

- 119 unit and 8 integration tests pass on Grails 7.2.3/JDK 21;
- publication to Maven Local produces Gradle metadata for 11.1.0-SNAPSHOT;
- MinIO 8.6.0 removes the identified XML-substitution vulnerability; and
- Okapi, Access Control and module composites consume the candidate. Final
  external-resolution evidence remains pending the reviewed shared release.
