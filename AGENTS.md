# Web Toolkit release instructions

Normal development targets `main`. Preserve existing releases and provenance.
The controlling licence is `LICENSE.md`; historical README community wording
does not authorize a new Apache publication.

Use `k-int.git-conventions` and Git-derived versions. Canonical FSL source tags
are protected, annotated `fsl/vX.Y.Z`; never create, move or push one without
explicit user authorization naming this repository and exact tag.

Release dependencies must be final releases. Timestamped Maven snapshots remain
development artifacts. Run `verifyReleaseDependencies` and inspect generated
POM/Gradle metadata, including fixtures, before publication.

Run `JAVA_HOME=/path/to/jdk-21 scripts/test-integration.sh test verifyReleaseDependencies`
for local qualification. The default integration task skips the database suite;
always report executed/skipped test counts. The script owns disposable local
fixtures only. Hosted tests require their own authorization.

Read `docs/tenant-owned-files.md` before storage or migration changes. Preserve
bounded database queries, independent ownership transactions and tenant fencing.
Maintain the audit and changelog with source evidence; never count a prepared
candidate or green build as a published release.
