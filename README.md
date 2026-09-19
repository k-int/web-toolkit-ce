# Web Toolkit

Toolkit and value-add module for Grails 7. Requires JDK 21.

Copyright (C) 2015-2024 Knowledge Integration

The canonical distribution uses the Functional Source License, Version 1.1,
ALv2 Future License. See [LICENSE.md](LICENSE.md). Historical community
provenance is retained below; a new Apache publication is a separate operation.


# History

This project is the community edition of our grails app web-toolkit, which was created
to help support applications built on the grails platform. It is published here under an 
APL license so it can be reused in other open source projects. Please observe the contents of 
GUIDANCE.md however, particularly if you are building pay-for SAAS solutions on this code.

# Installation

```
  implementation "com.k_int.grails:web-toolkit-ce:11.x"
```
_NOTE:_ You may need to substitute the version number with the latest release.

# Other Notes

See: https://knowledge-integration.gitlab.io/dev-ops/kint-gradle-plugin/

## Grails 7 Upgrade

For downstream-module migration guidance (including `SimpleLookupService` query behavior/config updates), see:

- [grails7-upgrade.md](grails7-upgrade.md)

## Releasing

Use JDK 21 and the maintained `k-int.git-conventions` plugin. Versions come
from Git; do not assign a release version in `gradle.properties`. The plugin
recognises `fsl/v` as well as historical `v` tags. Its automatic tag tasks still
create `v` tags; do not use those tasks for an FSL release.

Before publication, review the full source interval, update `AUDIT.md` and the
changelog, and run the unit, database/storage and dependency checks below.
`verifyReleaseDependencies` checks declared/resolved dependencies and generated
POM/Gradle metadata. It rejects moving and timestamped snapshots and is required
by `check` and Maven repository publication. Its own development publication
coordinate is excluded when validating test-fixture self-references.

Create and publish a protected annotated `fsl/vX.Y.Z` tag only after explicit
authorization for that exact repository/tag. Publish from that clean tagged
source through the existing `publishAllPublicationsToKIntRepository` task with
the established Maven credentials. Verify the externally resolved JAR, fixtures,
POM and Gradle metadata against the tagged source. Do not overwrite old versions.

## Testing

The default `integrationTest` task skips the database tests. Run:

```sh
JAVA_HOME=/path/to/jdk-21 scripts/test-integration.sh test verifyReleaseDependencies
```

This uses disposable loopback PostgreSQL 17 and the established K-Int MinIO
fixture via Podman, enables `test-livedb`, and removes both containers and their
volumes on exit. It uses the module tenant-schema layout needed by independent
storage ownership transactions. The fixed credentials are local test fixtures.
The older Compose file is retained as historical development configuration.

## MINIO/S3 File Storage

    MINIO File storage has been added. The following AppSetting entries control file uploads now
      * fileStorage.S3Endpoint  - The S3 endpoint - e.g. http://localhost:9000 for MINIO in the test system
      * fileStorage.S3AccessKey - S3 Access Key
      * fileStorage.S3SecretKey - S3 Secret Key
      * fileStorage.S3BucketName - S3 Bucket to use
      * fileStorage.S3ObjectPrefix - The path prefix this service should use - which will allow different contexts to share a single bucket if that is wanted


## Migrations

  If you are using liquibase migrations, the following section outlines the migrations needed by web-toolkit

```
  changeSet(author: "web-toolkit-1 (manual)", id: "202011101241-001") {
    createTable(tableName: "app_setting") {
      column(name: "st_id", type: "VARCHAR(36)") { constraints(nullable: "false") }
      column(name: "st_version", type: "BIGINT") { constraints(nullable: "false") }
      column(name: 'st_section', type: "VARCHAR(255)")
      column(name: 'st_key', type: "VARCHAR(255)")
      column(name: 'st_setting_type', type: "VARCHAR(255)")
      column(name: 'st_vocab', type: "VARCHAR(255)")
      column(name: 'st_default_value', type: "VARCHAR(255)")
      column(name: 'st_value', type: "VARCHAR(255)")
    }

    createTable(tableName: "file_upload") {
      column(name: "fu_id", type: "VARCHAR(36)") { constraints(nullable: "false") }
      column(name: "version", type: "BIGINT") { constraints(nullable: "false") }
      column(name: "fu_filesize", type: "BIGINT") { constraints(nullable: "false") }
      column(name: "fu_last_mod", type: "timestamp")
      column(name: "file_content_type", type: "VARCHAR(255)")
      column(name: "fu_owner", type: "VARCHAR(36)")
      column(name: "fu_filename", type: "VARCHAR(255)") { constraints(nullable: "false") }
      column(name: "fu_bytes", type: "bytea")
      column(name: "file_object_id", type: "varchar(36)")
    }

    createTable(tableName: "file_object") {
      column(name: "fo_id", type: "VARCHAR(36)") { constraints(nullable: "false") }
      column(name: "version", type: "BIGINT") { constraints(nullable: "false") }
      column(name: "file_contents", type: "OID")
      column(name: "class", type: "VARCHAR(255)")
      column(name: "fo_s3ref", type: "VARCHAR(255)")
    }

    addPrimaryKey(columnNames: "fo_id", constraintName: "file_objectPK", tableName: "file_object")

    grailsChange {
      change {
        sql.execute("UPDATE ${database.defaultSchemaName}.file_object SET class = 'LOB' where class is null".toString());
      }
    }

  }


```

# [License](LICENSE.md)

The license must be read in conjunction with [GUIDANCE.md](GUIDANCE.md)

Copyright (C) 2015-2024 Knowledge Integration

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
