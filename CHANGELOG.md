# Changelog

## 11.2.0 candidate — unpublished

- Preserve submission context through promise execution and callbacks, including
  task decoration required by grails-okapi's tenant work coordination.
- Retain S3 object ownership across upload rollback, deletion and cleanup retry;
  add bounded cleanup and synchronous tenant purge participation.
- Preserve independent LOB ownership and stream multipart LOB rebinding.
- Require both `wtk/owned-file-lob.feat.groovy` and
  `wtk/stored-s3-object.feat.groovy` in consuming tenant changelogs. See
  [storage contract](docs/tenant-owned-files.md). A dependency bump alone is
  insufficient; qualify existing tenant migrations and storage operations.
- Use maintained release versioning, explicit FSL tags and dependency checks
  that reject both moving and timestamped snapshots before publication.

This candidate is not released until its exact source tag and Maven artifacts
are published and externally verified. Historical artifacts remain unchanged.
