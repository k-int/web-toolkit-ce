package com.k_int.web.toolkit.files

import groovy.sql.Sql
import java.sql.Connection
import javax.sql.DataSource
import org.hibernate.jdbc.Work
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy

/** Read-only, uncached validation. Lifecycle owners select ready schemas; this bean never discovers or repairs tenants. */
class StorageSchemaValidator {
    DataSource dataSource

    void validateSchema(Serializable tenant) {
        String schema = tenant?.toString()
        requireSchemaName(schema)
        DataSource source = dataSource
        while (source instanceof TransactionAwareDataSourceProxy) source = source.targetDataSource
        source.connection.withCloseable { Connection connection -> validate(connection, schema) }
    }

    void validateCurrentTenant() {
        FileObject.withSession { session ->
            session.doWork({ Connection connection ->
                String schema = new Sql(connection).firstRow('SELECT current_schema() AS schema').schema
                validate(connection, schema)
            } as Work)
        }
    }

    static void requireSchemaName(String schema) {
        if (!schema || schema.indexOf(0) >= 0 || schema.getBytes('UTF-8').length > 63) {
            throw new IllegalArgumentException('Invalid tenant schema')
        }
    }

    void validate(Connection connection, String schema) {
        requireSchemaName(schema)
        Sql sql = new Sql(connection) // The caller owns this connection and transaction.
        sql.withStatement { it.queryTimeout = 10 }
        List<String> missing = []
        Map<String, Map> columns = sql.rows('''SELECT c.relname, a.attname, a.attnotnull, a.attnum,
            pg_catalog.format_type(a.atttypid,a.atttypmod) AS type
            FROM pg_catalog.pg_attribute a JOIN pg_catalog.pg_class c ON c.oid=a.attrelid
            JOIN pg_catalog.pg_namespace n ON n.oid=c.relnamespace
            WHERE n.nspname=? AND c.relname IN ('file_object','stored_s3_object')
              AND a.attname IN ('file_contents','fo_stored_s3_object_id','id','endpoint','bucket','region',
                'object_key','object_version','state','date_created','last_updated','last_error')
              AND a.attnum>0 AND NOT a.attisdropped''', [schema]).collectEntries {
                [(it.relname + '.' + it.attname): it]
            }
        Map expected = [
            'file_object.file_contents': ['oid', false],
            'file_object.fo_stored_s3_object_id': ['character varying(36)', false],
            'stored_s3_object.id': ['character varying(36)', true],
            'stored_s3_object.endpoint': ['character varying(1024)', true],
            'stored_s3_object.bucket': ['character varying(255)', true],
            'stored_s3_object.region': ['character varying(255)', true],
            'stored_s3_object.object_key': ['character varying(1024)', true],
            'stored_s3_object.object_version': ['character varying(1024)', false],
            'stored_s3_object.state': ['character varying(32)', true],
            'stored_s3_object.date_created': ['timestamp without time zone', true],
            'stored_s3_object.last_updated': ['timestamp without time zone', true],
            'stored_s3_object.last_error': ['character varying(255)', false]
        ]
        expected.each { name, requirement ->
            def actual = columns[name]
            if (actual == null || actual.type != requirement[0] || actual.attnotnull != requirement[1]) {
                missing.add('column ' + name + ' (' + requirement[0] + ')')
            }
        }
        def constraints = sql.rows('''SELECT con.contype, con.convalidated,
            pg_catalog.pg_get_constraintdef(con.oid) AS definition
            FROM pg_catalog.pg_constraint con JOIN pg_catalog.pg_class c ON c.oid=con.conrelid
            JOIN pg_catalog.pg_namespace n ON n.oid=c.relnamespace
            WHERE n.nspname=? AND ((c.relname='file_object' AND con.conname='file_object_stored_s3_fk')
              OR (c.relname='stored_s3_object' AND con.contype='p'))''', [schema])
        if (!constraints.any { it.contype == 'p' && it.definition == 'PRIMARY KEY (id)' }) missing.add('stored_s3_object primary key')
        // Resolve the referenced relation by identity, independent of the caller's search_path.
        if (!sql.firstRow('''SELECT EXISTS(SELECT 1 FROM pg_catalog.pg_constraint con
            JOIN pg_catalog.pg_class c ON c.oid=con.conrelid
            JOIN pg_catalog.pg_namespace n ON n.oid=c.relnamespace
            JOIN pg_catalog.pg_class target ON target.oid=con.confrelid
            JOIN pg_catalog.pg_namespace tn ON tn.oid=target.relnamespace
            WHERE n.nspname=? AND tn.nspname=? AND c.relname='file_object'
              AND target.relname='stored_s3_object' AND con.conname='file_object_stored_s3_fk'
              AND con.contype='f' AND con.convalidated AND con.confdeltype='a' AND con.confupdtype='a'
              AND con.conkey=ARRAY[(SELECT attnum FROM pg_catalog.pg_attribute WHERE attrelid=c.oid AND attname='fo_stored_s3_object_id')]::smallint[]
              AND con.confkey=ARRAY[(SELECT attnum FROM pg_catalog.pg_attribute WHERE attrelid=target.oid AND attname='id')]::smallint[])
              AS valid''', [schema, schema]).valid) missing.add('file_object_stored_s3_fk')
        def indexes = sql.rows('''SELECT indexrel.relname, i.indisvalid, i.indisready,
            pg_catalog.pg_get_indexdef(i.indexrelid) AS definition
            FROM pg_catalog.pg_index i JOIN pg_catalog.pg_class indexrel ON indexrel.oid=i.indexrelid
            JOIN pg_catalog.pg_namespace n ON n.oid=indexrel.relnamespace
            WHERE n.nspname=? AND indexrel.relname IN ('file_object_stored_s3_idx','stored_s3_cleanup_idx')''', [schema])
        ['file_object_stored_s3_idx': '(fo_stored_s3_object_id)',
         'stored_s3_cleanup_idx': '(state, last_updated, id)'].each { name, suffix ->
            if (!indexes.any { it.relname == name && it.indisvalid && it.indisready && it.definition.endsWith(suffix) }) missing.add('index ' + name)
        }
        def triggers = sql.rows('''SELECT t.tgname,t.tgenabled,t.tgtype,t.tgnargs,t.tgattr::text AS attributes,encode(t.tgargs,'escape') AS args,
            pg_catalog.pg_get_triggerdef(t.oid) AS definition, p.proname,p.prosrc,p.probin,
            pn.nspname AS function_schema, l.lanname, p.prorettype='pg_catalog.trigger'::regtype AS returns_trigger
            FROM pg_catalog.pg_trigger t JOIN pg_catalog.pg_class c ON c.oid=t.tgrelid
            JOIN pg_catalog.pg_namespace n ON n.oid=c.relnamespace
            JOIN pg_catalog.pg_proc p ON p.oid=t.tgfoid JOIN pg_catalog.pg_namespace pn ON pn.oid=p.pronamespace
            JOIN pg_catalog.pg_language l ON l.oid=p.prolang
            WHERE n.nspname=? AND c.relname='file_object' AND NOT t.tgisinternal
              AND t.tgname IN ('file_object_owned_lob','stored_s3_reference_lock','stored_s3_reference_removed')''', [schema])
        String role = sql.firstRow('SHOW session_replication_role').session_replication_role
        Map definitions = [
            file_object_owned_lob: [27, 'public', 'lo_manage', 'file_contents', 1],
            stored_s3_reference_lock: [31, schema, 'stored_s3_reference_lock', 'fo_stored_s3_object_id', 0],
            stored_s3_reference_removed: [25, schema, 'stored_s3_reference_removed', 'fo_stored_s3_object_id', 0]
        ]
        definitions.each { name, requirement ->
            def t = triggers.find { it.tgname == name }
            boolean valid = t != null && t.tgenabled in ['O', 'A'] && (role != 'replica' || t.tgenabled == 'A') &&
                t.tgtype == requirement[0] && t.function_schema == requirement[1] && t.proname == requirement[2] &&
                t.tgnargs == requirement[4] && t.returns_trigger &&
                t.attributes == columns['file_object.' + requirement[3]]?.attnum?.toString() && !t.definition.contains(' WHEN (')
            if (valid && name == 'file_object_owned_lob') {
                valid = t.args == 'file_contents\\000' && t.lanname == 'c' && t.prosrc == 'lo_manage' && t.probin == '$libdir/lo'
            } else if (valid) {
                valid = t.lanname == 'plpgsql' && normalize(t.prosrc) == normalize(expectedFunction(name, schema))
            }
            if (!valid) missing.add('enabled trigger/function ' + name)
        }
        if (missing) throw new StorageSchemaPrerequisiteException(schema, missing)
    }

    private static String normalize(String sql) { sql.replaceAll(/\s+/, ' ').trim() }

    private static String expectedFunction(String name, String schema) {
        // Compare with the shipped migration rather than a second copy of its function body.
        String migration = StorageSchemaValidator.classLoader.getResource('wtk/stored-s3-object.feat.groovy').getText('UTF-8')
        def matcher = migration =~ /(?s)CREATE FUNCTION "SCHEMA"\.${name}\(\) RETURNS trigger LANGUAGE plpgsql AS [$]body[$](.*?)[$]body[$]/
        if (!matcher.find()) throw new IllegalStateException('Packaged Toolkit storage migration is unavailable')
        matcher.group(1).replace('SCHEMA', schema)
    }
}
