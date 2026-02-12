# Grails 7 Migration Checklist (web-toolkit-ce)

## Baseline
- [ ] Record current branch and confirm no local changes to discard.
- [ ] Ensure JDK 17 is available for builds/tests.
- [ ] Note current Grails version in `gradle.properties`.

## Build Alignment
- [ ] Update to Grails 7.x in `gradle.properties`.
- [ ] Remove `grailsGradlePluginVersion` (Grails 7 uses a unified version).
- [ ] Switch Grails dependencies to `org.apache.grails:*`.
- [ ] Add/align Grails BOM for dependency management.
- [ ] Align Grails views JSON plugin to Grails 7.
- [ ] Update Hibernate/GORM plugin to Grails 7 compatible version.
- [ ] Ensure Gradle plugin coordinates are for Grails 7.
- [ ] If this plugin is a subproject of an app, apply `org.apache.grails.gradle.grails-exploded` for exploded reloading.
- [ ] Set Java 17 compatibility if not already set.
- [ ] Remove or confirm any Grails 6 era workarounds (e.g., migration sourceSet tweaks).

## Code Migration
- [ ] Replace all `javax.*` imports with `jakarta.*` equivalents.
- [ ] Update any servlet request attribute keys if they reference `javax` namespaces.
- [ ] Verify Spring Security integration against Spring Security 6.
- [ ] Verify Micronaut usage against Micronaut 4+ (Grails 7 integration).

## Tests and Verification
- [ ] Run `./gradlew check` (or a smaller compile target) with JDK 17.
- [ ] Resolve compilation errors.
- [ ] Re-run tests until green.
- [ ] Record any behavioral differences.

## Release Readiness
- [ ] Update plugin metadata (`grailsVersion` range in plugin descriptor).
- [ ] Confirm published POM is consistent with Grails 7.
- [ ] Update README/NEWS if needed.
