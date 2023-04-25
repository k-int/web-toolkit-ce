
18th Nov 2021 - 6.0.0 Breaking changes

This release extends the file upload capability to add s3/minio as a storage option and provides a migration facility
to shift LOB storage to S3.

The folloing migrations, or something like them, will be needed

    dropNotNullConstraint(columnName: "file_contents", tableName: "file_object")


    addColumn (tableName: "file_object" ) {
      column(name: "class", type: "VARCHAR(255)")
      column(name: "fo_s3ref", type: "VARCHAR(255)")
    }

    grailsChange {
      change {
        sql.execute("UPDATE ${database.defaultSchemaName}.file_object SET class = 'LOB' where class is null".toString());
      }
    }


