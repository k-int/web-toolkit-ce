# Tenant-owned file cleanup

Feature branch `000091`; no library publication or Snapshot reset qualification.
Upload/download interfaces, file IDs and the `LOB`/`S3` switch are retained.

Consumers must include both tenant migrations before adopting these sources:

```groovy
include file: 'wtk/owned-file-lob.feat.groovy'
include file: 'wtk/stored-s3-object.feat.groovy'
```

The LOB migration requires PostgreSQL's `lo` extension in `public`. Its
`lo_manage` trigger unlinks an exclusively owned OID when its file is deleted or
replaced, in the same transaction. Clones receive independent LOBs. DROP SCHEMA
and TRUNCATE do not fire row triggers: tenant purge still requires synchronous
cleanup before dropping references. Never substitute a database-wide OID sweep.
See [PostgreSQL's lo documentation](https://www.postgresql.org/docs/17/lo.html).

`StoredS3Object` retains the physical endpoint, bucket, region, key and returned
object version independently of the file transaction. File references and
ordinary deletion serialize against that record. A clone shares its ownership
record; deleting the last reference marks it `DELETE_PENDING`. Bytes are deleted
and their absence checked after commit. Failure retains the record for retry.
Credentials are resolved from existing committed settings, never stored here.
A changed storage location does not redirect cleanup: it remains pending until
matching configuration is restored.

Call `storedS3ObjectService.cleanupPending(25)` from existing tenant housekeeping,
inside the tenant's work permit. Each call selects a bounded oldest-attempt batch;
failed attempts move behind older work. Do not infer that an `UPLOADING` record
is abandoned from its age. Process-loss reconciliation needs proven tenant drain.

Versioned objects use exact returned version IDs. Missing version information on
a versioned bucket fails cleanup closed; a delete marker is not byte-removal
proof. The current local qualification covers unversioned MinIO. Existing legacy
rows retain their original read path; no historical ownership backfill is added.

Independent JDBC transactions preserve ownership through upload/save rollback.
Transaction-completion callbacks use the captured schema and do not re-enter
GORM sessions. The connection pool must accommodate the independent ownership
connection alongside the application's transaction. SQL filters and limits work
in PostgreSQL; neither file bytes nor ownership tables are loaded wholesale.

Toolkit's nested clone helper passes an empty property selection. S3's
`cloneDefaultProperties` therefore includes both key and ownership, even though
the latter is nullable for legacy compatibility. Its general cloning contract
is unchanged.

Agreements integration tests cover real LOB/S3 clone/deletion, rollback, upload
and metadata-save failure, retained deletion/retry and a 200 MiB streamed upload
with byte-integrity verification. These ordinary-file tests do not prove managed reconstruction, crash recovery,
versioned storage, or other module consumers. Public purge is covered below.

## Synchronous purge follow-on

`purgeOwned(schema)` runs after the module quiesces and deactivates that tenant.
It preflights retained locations, detaches references in the still-present schema
and deletes each owned S3 object, retaining pending records on failure. The
Grails purge participant must propagate failure and retain the schema. Legacy
rows remain outside automatic external cleanup; managed reset must first call
`requireCompleteOwnership(schema)` and reject ambiguous legacy or unqualified
versioned storage. This does not retrofit historical ownership.

New S3 keys use the configured prefix plus a UUID. Filenames remain metadata;
a legal 255-character filename must not exceed a provider's physical path-component
limit. Existing keys remain readable through their stored coordinates.

Real servlet multipart streams need not support reset. LOB JDBC binding may read
them more than once, so the multipart adapter reopens the same source on rewind
and closes it at transaction completion. It does not buffer the full file or
change upload/download interfaces. The regression uses a non-resettable source.

## Mandatory schema preflight

`storageSchemaValidator` is the Toolkit-owned `StorageSchemaValidator` bean.
The lifecycle owner must call `validateSchema(resolvedPhysicalSchema)` after
Liquibase commits and before tenant activation, and validate already-ready
tenants during startup. Alternatively `validate(connection, schema)` checks the
actual migration connection, including its uncommitted schema changes. A tenant
identifier is not necessarily its physical schema name. Toolkit does not discover
ready tenants, validate unprovisioned tenants at startup, or create/repair schemas.

Validation checks bounded PostgreSQL catalogs for storage column types/nullability,
S3 primary/foreign keys and cleanup indexes, and all three enabled row triggers
with the correct functions and definitions. S3 function bodies must match the
shipped migration. The LOB trigger must call PostgreSQL's `public.lo_manage`.
Success is never cached: later trigger removal, disabling or schema recreation
cannot inherit earlier success. Concurrent administrator DDL is outside this
preflight guarantee; callers must not alter safeguards during tenant work.

A missing prerequisite throws `StorageSchemaPrerequisiteException`, identifying
the schema, missing requirements and both include paths. Upload and cleanup
handlers propagate it. Apply missing includes through the tenant's normal
migration lifecycle, or explicitly repair changed objects, then retry activation.
Do not clear the changeset history or automatically rerun an applied changeset.
The migrations require CREATE on the tenant schema, function/trigger privileges,
and permission to install `lo` in `public` (or an administrator-preinstalled
extension there). The populated baseline retains legacy S3 reads without claiming
ownership/backfill; managed reset still rejects legacy unowned keys.

Covered use sites are `FileUploadService.save` and its bounded `migrateAtMost`,
`FileObject` ORM insert/update/delete (including cascading deletion and clones),
and every independent S3 ownership/cleanup transaction, including purge and
transaction-completion cleanup. The ORM checks use the actual Hibernate
connection's current schema; S3 cleanup uses its explicitly selected schema.
Direct caller-written HQL/JDBC bulk mutations bypass ORM callbacks: consumers
must invoke `validate(connection, schema)` before such work. Toolkit's supported
bulk migration and purge paths already do so. Do not use TRUNCATE/DROP as ordinary
file deletion; neither fires the LOB row cleanup trigger.

The disposable runner executes the shipped Grails Liquibase files against an
explicit pre-owned-storage fixture matching the Toolkit tables in ILL's 2.8
migration. It covers fresh/populated upgrade, omitted includes, absent/disabled
triggers and changed functions; storage tests then use those migrated file tables.
Other unrelated test domains still use Hibernate schema creation. This is Toolkit
migration proof, not qualification of a whole module changelog, tenant admission,
legacy S3 backfill, versioned buckets, concurrent administrator DDL or rollback to
an older library. Whole-module fresh/upgrade and lifecycle retry remain consumer
gates before publication/adoption.

Run `JAVA_HOME=/path/to/jdk-21 scripts/test-integration.sh test verifyReleaseDependencies`.
The runner retains JUnit, raw Gradle output in
`build/reports/toolkit-qualification.log`, and a compact JSON result in
`build/reports/toolkit-qualification.json` with source/input hashes, fixture image
IDs, counts and cleanup status. Missing required suites, skips, test failures or
failed disposable-container cleanup fail the command. This repository currently
has no configured CI workflow; local proof does not imply CI qualification.

Retained source qualification: 125 unit + 41 integration cases, zero failures,
errors or skips; final dependency gate and disposable cleanup passed. See
[qualification summary](storage-prerequisite-qualification.json). CI wiring needs
an owner-approved runner with JDK 21, Podman, Python 3, Gradle repository access
and access to the existing private MinIO fixture image; none is configured here.
