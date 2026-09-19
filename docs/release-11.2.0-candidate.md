# Web Toolkit 11.2.0 release candidate

Status: qualified source preparation; unpublished. Proposed source tag
`fsl/v11.2.0` requires explicit authorization before creation or push.

The previous final is `v11.1.0`. Four functional commits on main were consumed
through a development snapshot without completing the next Toolkit release.
This reviewed manual mapping preserves history without inventing effort:

| Source | Audit item |
| --- | --- |
| `ce1f26d` promise context | WTK-ASYNC-CONTEXT |
| `022771a` owned storage | WTK-TENANT-STORAGE |
| `572b835` purge cleanup | WTK-TENANT-STORAGE |
| `2a0b47a` multipart LOB | WTK-TENANT-STORAGE |

Earlier documentation-only commits retain the existing 11.1.0 audit. No audit
notes ref was advertised by the remote. Current preparation uses normal paired
audit trailers. Unknown effort remains TBC, not funding approval.

## Qualification

With JDK 21:

```sh
scripts/test-integration.sh test verifyReleaseDependencies
```

125 unit and 20 PostgreSQL/MinIO integration cases pass, zero failures/skips.
The original default environment skipped eight integration test features; their
parameterised cases expand to 20 when enabled. The local runner uses tenant
schemas matching the storage ownership contract and removes its containers.
This checks Toolkit behavior; it does not prove consumer migrations or hosted
rollout, versioned S3 behavior or crash recovery.

The release-dependency task passes on the candidate. A diagnostic Gradle init
script adding `runtimeOnly 'com.k_int.okapi:grails-okapi:9.2.0'` makes that task
fail on `com.k_int.grails:web-toolkit-ce:11.2.0-20260907.041431-1`, reproducing
the actual transitive dependency defect. The diagnostic is not a source pin.

## Publication and downstream gates

After exact-tag approval, tag the reviewed clean source, publish its JAR,
test-fixture JAR, sources, POM and Gradle metadata through the existing Gradle
publisher, then verify externally resolved versions/digests. Do not replace the
old snapshot or change grails-okapi 9.2.0's published metadata.

Next, qualify a new grails-okapi release against final Toolkit, including its
PostgreSQL federation and tenant barrier suites. Then qualify the six module
consumers with committed final pins, required tenant migrations and their own
integration/container gates. Existing FOLIO/Eureka behavior remains required;
no Foundry-only shortcut or implicit drain enablement is included.
