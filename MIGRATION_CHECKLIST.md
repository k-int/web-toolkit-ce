# Grails 7 Migration Checklist (web-toolkit-ce)

## Baseline
- [x] Record current branch and confirm no local changes to discard.
  - Branch: `grails-7-upgrade`
- [x] Ensure JDK 17 is available for builds/tests.
  - Java toolchain configured to 17; `./gradlew check` passes.
- [x] Note current Grails version in `gradle.properties`.
  - `grailsVersion=7.0.7`

## Build Alignment
- [x] Update to Grails 7.x in `gradle.properties`.
- [x] Remove `grailsGradlePluginVersion` (Grails 7 uses a unified version).
- [x] Switch Grails dependencies to `org.apache.grails:*`.
- [x] Add/align Grails BOM for dependency management.
- [x] Align Grails views JSON plugin to Grails 7.
- [x] Update Hibernate/GORM plugin to Grails 7 compatible version.
- [x] Ensure Gradle plugin coordinates are for Grails 7.
- [x] If this plugin is a subproject of an app, apply `org.apache.grails.gradle.grails-exploded` for exploded reloading.
  - Not required for this standalone plugin build.
- [x] Set Java 17 compatibility if not already set.
- [x] Remove or confirm any Grails 6 era workarounds (e.g., migration sourceSet tweaks).
  - Integration test disables removed from `build.gradle`.

## Code Migration
- [x] Replace all `javax.*` imports with `jakarta.*` equivalents.
- [x] Update any servlet request attribute keys if they reference `javax` namespaces.
  - `jakarta.servlet.error.exception` handling present.
- [x] Verify Spring Security integration against Spring Security 6.
  - Build/test passes with Spring Security 6.x on Grails 7 BOM.
- [x] Verify Micronaut usage against Micronaut 4+ (Grails 7 integration).
  - Build/test passes with Grails Micronaut integration enabled.

## Tests and Verification
- [x] Run `./gradlew check` (or a smaller compile target) with JDK 17.
- [x] Resolve compilation errors.
  - Added Groovy 4 compatibility bridge for `groovy.util.slurpersupport.GPathResult`.
- [x] Re-run tests until green.
- [x] Record any behavioral differences.
  - Query semantics now split via backend selector with native JPA parity coverage and explicit fallback table in backlog.

## Release Readiness
- [x] Update plugin metadata (`grailsVersion` range in plugin descriptor).
- [ ] Confirm published POM is consistent with Grails 7.
- [ ] Update README/NEWS if needed.
