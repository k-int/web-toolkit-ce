package com.k_int.web.toolkit.files

import grails.testing.mixin.integration.Integration
import grails.util.Environment
import groovy.sql.Sql
import spock.lang.Requires
import spock.lang.Specification
import spock.lang.Unroll
import org.springframework.beans.factory.annotation.Autowired

@Integration
@Requires({ Environment.currentEnvironment.name == 'test-livedb' })
class StorageMigrationSpec extends Specification {
    @Autowired StorageSchemaValidator storageSchemaValidator
    @Autowired StoredS3ObjectService storedS3ObjectService
    Sql sql
    String schema

    def 'qualification application explicitly enables strict validation'() {
        expect:
        storageSchemaValidator.mode == 'strict'
    }

    @Unroll
    def '#selectedMode validation reports #changelog without silently accepting missing safeguards'() {
        given:
        StorageMigrationFixture.migrate(schema, changelog)
        def validator = new StorageSchemaValidator(dataSource: storageSchemaValidator.dataSource)
        if (selectedMode != 'default') validator.mode = selectedMode
        def logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(StorageSchemaValidator)
        def warnings = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>()
        warnings.start()
        logger.addAppender(warnings)
        StorageSchemaPrerequisiteException failure = null

        when:
        try { validator.validateSchema(schema) }
        catch (StorageSchemaPrerequisiteException rejected) { failure = rejected }

        then:
        if (selectedMode == 'strict') {
            assert failure != null
            assert failure.schema == schema
            assert warnings.list.empty
        } else {
            assert failure == null
            assert warnings.list.size() == 1
            assert warnings.list.first().level == ch.qos.logback.classic.Level.WARN
            assert warnings.list.first().formattedMessage.contains(schema)
            assert warnings.list.first().formattedMessage.contains('WTK_STORAGE_SCHEMA_VALIDATION=strict')
            assert warnings.list.first().formattedMessage.contains('wtk/owned-file-lob.feat.groovy')
        }

        cleanup:
        logger.detachAppender(warnings)
        warnings.stop()

        where:
        [selectedMode, changelog] << [['default', 'warn', 'strict'],
            ['storage/without-lob.groovy', 'storage/without-s3.groovy']].combinations()
    }

    def setup() {
        schema = 'storage_' + UUID.randomUUID().toString().replace('-', '')
        sql = Sql.newInstance(System.getenv('TOOLKIT_TEST_JDBC_URL'), 'test', 'test', 'org.postgresql.Driver')
        sql.execute('CREATE SCHEMA ' + schema)
        sql.execute('SET search_path TO ' + schema)
    }

    def cleanup() {
        // Explicit LOB cleanup for negative fixtures whose safety trigger is deliberately absent.
        if (sql.firstRow("SELECT to_regclass(?) AS relation", [schema + '.file_object']).relation) {
            sql.execute('SELECT lo_unlink(file_contents) FROM file_object WHERE file_contents IS NOT NULL')
        }
        sql.execute('DROP SCHEMA ' + schema + ' CASCADE')
        sql.close()
    }

    @Unroll
    def 'real Liquibase #mode supports LOB replacement, sharing and last-reference cleanup'() {
        given:
        if (mode == 'populated upgrade') {
            StorageMigrationFixture.migrate(schema, 'storage/baseline.groovy')
            insertLob('old', 'before upgrade')
            sql.execute("INSERT INTO file_object(fo_id,version,class,fo_s3ref) VALUES ('legacy',0,'S3','legacy-key')")
        }
        StorageMigrationFixture.migrate(schema)
        storageSchemaValidator.validateSchema(schema)

        when:
        if (mode == 'fresh') insertLob('old', 'before upgrade')
        long original = sql.firstRow("SELECT file_contents FROM file_object WHERE fo_id='old'").file_contents as long
        sql.execute("UPDATE file_object SET file_contents=lo_from_bytea(0,convert_to('replacement','UTF8')) WHERE fo_id='old'")

        then:
        !sql.firstRow('SELECT EXISTS(SELECT 1 FROM pg_largeobject_metadata WHERE oid=?) AS present', [original]).present
        sql.firstRow("SELECT convert_from(lo_get(file_contents),'UTF8') AS contents FROM file_object WHERE fo_id='old'").contents == 'replacement'

        when:
        sql.execute("INSERT INTO stored_s3_object(id,endpoint,bucket,region,object_key,state,date_created,last_updated) VALUES ('owner','http://fixture','bucket','region','key','UPLOADING',now(),now())")
        sql.execute("INSERT INTO file_object(fo_id,version,class,fo_s3ref,fo_stored_s3_object_id) VALUES ('s3a',0,'S3','key','owner'),('s3b',0,'S3','key','owner')")
        sql.execute("DELETE FROM file_object WHERE fo_id='s3a'")

        then:
        sql.firstRow("SELECT state FROM stored_s3_object WHERE id='owner'").state == 'AVAILABLE'

        when:
        sql.execute("DELETE FROM file_object WHERE fo_id='s3b'")

        then:
        sql.firstRow("SELECT state FROM stored_s3_object WHERE id='owner'").state == 'DELETE_PENDING'
        mode != 'populated upgrade' || sql.firstRow("SELECT fo_stored_s3_object_id FROM file_object WHERE fo_id='legacy'").fo_stored_s3_object_id == null

        where:
        mode << ['fresh', 'populated upgrade']
    }

    @Unroll
    def 'omitted #migration fails before owned cleanup or reference mutation'() {
        given:
        StorageMigrationFixture.migrate(schema, changelog)
        insertLob('untouched', 'keep')
        long oid = sql.firstRow("SELECT file_contents FROM file_object WHERE fo_id='untouched'").file_contents as long

        when:
        storedS3ObjectService.purgeOwned(schema)

        then:
        def failure = thrown(StorageSchemaPrerequisiteException)
        failure.schema == schema
        failure.message.contains(migration)
        sql.firstRow('SELECT EXISTS(SELECT 1 FROM pg_largeobject_metadata WHERE oid=?) AS present', [oid]).present
        sql.firstRow("SELECT count(*) AS n FROM file_object WHERE fo_id='untouched'").n == 1

        where:
        changelog | migration
        'storage/without-lob.groovy' | 'file_object_owned_lob'
        'storage/without-s3.groovy' | 'stored_s3_object'
    }

    @Unroll
    def '#action #trigger is detected after earlier successful validation'() {
        given:
        StorageMigrationFixture.migrate(schema)
        storageSchemaValidator.validateSchema(schema)
        insertLob('untouched', 'keep')
        if (action == 'disable') sql.execute('ALTER TABLE file_object DISABLE TRIGGER ' + trigger)
        else sql.execute('DROP TRIGGER ' + trigger + ' ON file_object')

        when:
        storedS3ObjectService.purgeOwned(schema)

        then:
        def failure = thrown(StorageSchemaPrerequisiteException)
        failure.requirements.any { it.contains(trigger) }
        sql.firstRow("SELECT convert_from(lo_get(file_contents),'UTF8') AS contents FROM file_object WHERE fo_id='untouched'").contents == 'keep'

        where:
        [action, trigger] << [['disable', 'remove'],
            ['file_object_owned_lob', 'stored_s3_reference_lock', 'stored_s3_reference_removed']].combinations()
    }

    def 'altered trigger function is rejected even with intact changeset history'() {
        given:
        StorageMigrationFixture.migrate(schema)
        sql.execute('CREATE OR REPLACE FUNCTION ' + schema + '.stored_s3_reference_removed() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RETURN NULL; END $$')

        when:
        storageSchemaValidator.validateSchema(schema)

        then:
        thrown(StorageSchemaPrerequisiteException)
    }

    private void insertLob(String id, String value) {
        sql.execute("INSERT INTO file_object(fo_id,version,class,file_contents) VALUES (?,0,'DB',lo_from_bytea(0,convert_to(?,'UTF8')))", [id, value])
    }
}
