# Web Toolkit Community Edition
Toolkit and value-add module for grails 6.x.x

Copyright (C) 2015-2024 Knowledge Integration

This software is distributed under the terms of the Apache License, Version 2.0. See [License](#license) for more information. The license must be read in conjunction with [GUIDANCE.md](GUIDANCE.md)


# History

This project is the community edition of our grails app web-toolkit, which was created
to help support applications built on the grails platform. It is published here under an 
APL license so it can be reused in other open source projects. Please observe the contents of 
GUIDANCE.md however, particularly if you are building pay-for SAAS solutions on this code.

# Installation

```
  compile "com.k_int.grails:web-toolkit-ce:9.0.3"
```
_NOTE:_ You may need to substitute the version number with the latest release.

# Other Notes

See: https://knowledge-integration.gitlab.io/dev-ops/kint-gradle-plugin/

## Releasing
```
./gradlew cgTagFinal to tag a final "release"
```

or

```
./gradlew cgTagPre to try a pre-release...
```

Then to publish that:
```
    Add kintMavenUser and kintMavenPassword to ~/.gradle/gradle.properties

    ./gradlew publishAllPublicationsToKIntRepository
```

## Testing

A root level docker-compose file is provided that provisions the components needed for the integration tests to run. Test with

    docker-compose down -v   # To clear any previous data
    docker-compose up
    ./gradlew clean build

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

