databaseChangeLog = {
  changeSet(author: 'k-int', id: '2026-09-05-owned-file-lob') {
    grailsChange {
      change {
        String schema = database.defaultSchemaName
        if (!(schema ==~ /[A-Za-z_][A-Za-z0-9_]{0,62}/)) throw new IllegalStateException('Invalid tenant schema')
        // PostgreSQL's standard trigger releases an exclusively owned large
        // object in the same transaction as reference replacement/deletion.
        // LOBFileObject.clone copies bytes into a distinct large object.
        sql.execute('CREATE EXTENSION IF NOT EXISTS lo WITH SCHEMA public')
        sql.execute('CREATE TRIGGER file_object_owned_lob BEFORE UPDATE OF file_contents OR DELETE ON "' +
          schema + '".file_object FOR EACH ROW EXECUTE FUNCTION public.lo_manage(file_contents)')
      }
    }
  }
}
