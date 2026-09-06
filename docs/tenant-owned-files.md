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
