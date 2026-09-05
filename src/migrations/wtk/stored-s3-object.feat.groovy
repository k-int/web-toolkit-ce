databaseChangeLog = {
  changeSet(author: 'k-int', id: '2026-09-05-stored-s3-object') {
    createTable(tableName: 'stored_s3_object') {
      column(name: 'id', type: 'varchar(36)') { constraints(primaryKey: true, nullable: false) }
      column(name: 'endpoint', type: 'varchar(1024)') { constraints(nullable: false) }
      column(name: 'bucket', type: 'varchar(255)') { constraints(nullable: false) }
      column(name: 'region', type: 'varchar(255)') { constraints(nullable: false) }
      column(name: 'object_key', type: 'varchar(1024)') { constraints(nullable: false) }
      column(name: 'object_version', type: 'varchar(1024)')
      column(name: 'state', type: 'varchar(32)') { constraints(nullable: false) }
      column(name: 'date_created', type: 'timestamp') { constraints(nullable: false) }
      column(name: 'last_updated', type: 'timestamp') { constraints(nullable: false) }
      column(name: 'last_error', type: 'varchar(255)')
    }
    addColumn(tableName: 'file_object') { column(name: 'fo_stored_s3_object_id', type: 'varchar(36)') }
    addForeignKeyConstraint(baseTableName: 'file_object', baseColumnNames: 'fo_stored_s3_object_id',
      referencedTableName: 'stored_s3_object', referencedColumnNames: 'id', constraintName: 'file_object_stored_s3_fk')
    createIndex(tableName: 'file_object', indexName: 'file_object_stored_s3_idx') { column(name: 'fo_stored_s3_object_id') }
    createIndex(tableName: 'stored_s3_object', indexName: 'stored_s3_cleanup_idx') {
      column(name: 'state')
      column(name: 'last_updated')
      column(name: 'id')
    }
    grailsChange {
      change {
        String schema = database.defaultSchemaName
        if (!(schema ==~ /[A-Za-z_][A-Za-z0-9_]{0,62}/)) throw new IllegalStateException('Invalid tenant schema')
        // Reference creation/deletion serialize on the physical owner, including
        // clones. The SQL trigger covers ORM cascades and existing bulk deletion.
        sql.execute(('''CREATE FUNCTION "SCHEMA".stored_s3_reference_lock() RETURNS trigger LANGUAGE plpgsql AS $body$
DECLARE object_state varchar;
BEGIN
  IF TG_OP <> 'INSERT' AND OLD.fo_stored_s3_object_id IS NOT NULL THEN
    PERFORM id FROM "SCHEMA".stored_s3_object WHERE id=OLD.fo_stored_s3_object_id FOR UPDATE;
  END IF;
  IF TG_OP <> 'DELETE' AND NEW.fo_stored_s3_object_id IS NOT NULL THEN
    SELECT state INTO object_state FROM "SCHEMA".stored_s3_object WHERE id=NEW.fo_stored_s3_object_id FOR UPDATE;
    IF object_state IS NULL OR object_state='DELETE_PENDING' THEN
      RAISE EXCEPTION 'S3 object is unavailable for new references';
    END IF;
    UPDATE "SCHEMA".stored_s3_object SET state='AVAILABLE',last_updated=current_timestamp
      WHERE id=NEW.fo_stored_s3_object_id;
  END IF;
  IF TG_OP='DELETE' THEN RETURN OLD; ELSE RETURN NEW; END IF;
END
$body$''').replace('SCHEMA', schema))
        sql.execute(('''CREATE FUNCTION "SCHEMA".stored_s3_reference_removed() RETURNS trigger LANGUAGE plpgsql AS $body$
BEGIN
  IF OLD.fo_stored_s3_object_id IS NOT NULL AND
     NOT EXISTS (SELECT 1 FROM "SCHEMA".file_object WHERE fo_stored_s3_object_id=OLD.fo_stored_s3_object_id) THEN
    UPDATE "SCHEMA".stored_s3_object SET state='DELETE_PENDING',last_updated=current_timestamp
      WHERE id=OLD.fo_stored_s3_object_id;
  END IF;
  RETURN NULL;
END
$body$''').replace('SCHEMA', schema))
        sql.execute('CREATE TRIGGER stored_s3_reference_lock BEFORE INSERT OR UPDATE OF fo_stored_s3_object_id OR DELETE ON "' +
          schema + '".file_object FOR EACH ROW EXECUTE FUNCTION "' + schema + '".stored_s3_reference_lock()')
        sql.execute('CREATE TRIGGER stored_s3_reference_removed AFTER UPDATE OF fo_stored_s3_object_id OR DELETE ON "' +
          schema + '".file_object FOR EACH ROW EXECUTE FUNCTION "' + schema + '".stored_s3_reference_removed()')
      }
    }
  }
}
