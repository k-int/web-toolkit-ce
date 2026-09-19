// Toolkit's pre-owned-storage table contract, matching mod-ill update-mod-rs-2-8.
// This deliberately creates no owned-S3 column/table or cleanup triggers.
databaseChangeLog = {
  changeSet(author: 'k-int', id: 'storage-test-baseline') {
    createTable(tableName: 'file_object') {
      column(name: 'fo_id', type: 'varchar(36)') { constraints(primaryKey: true, nullable: false) }
      column(name: 'version', type: 'bigint') { constraints(nullable: false) }
      column(name: 'class', type: 'varchar(255)') { constraints(nullable: false) }
      column(name: 'fo_s3ref', type: 'varchar(255)')
      column(name: 'file_contents', type: 'oid')
    }
    createTable(tableName: 'file_upload') {
      column(name: 'fu_id', type: 'varchar(36)') { constraints(primaryKey: true, nullable: false) }
      column(name: 'version', type: 'bigint') { constraints(nullable: false) }
      column(name: 'fu_filesize', type: 'bigint') { constraints(nullable: false) }
      column(name: 'fu_last_mod', type: 'timestamp')
      column(name: 'file_content_type', type: 'varchar(255)')
      column(name: 'fu_owner', type: 'varchar(36)')
      column(name: 'file_object_id', type: 'varchar(36)') { constraints(nullable: false) }
      column(name: 'fu_filename', type: 'varchar(255)') { constraints(nullable: false) }
    }
    addForeignKeyConstraint(baseTableName: 'file_upload', baseColumnNames: 'file_object_id',
      referencedTableName: 'file_object', referencedColumnNames: 'fo_id', constraintName: 'fixture_file_upload_object_fk')
  }
}
