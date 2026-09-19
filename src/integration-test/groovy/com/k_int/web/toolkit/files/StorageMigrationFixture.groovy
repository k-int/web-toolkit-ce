package com.k_int.web.toolkit.files

import groovy.sql.Sql
import liquibase.Liquibase
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.DirectoryResourceAccessor
import liquibase.resource.CompositeResourceAccessor
import java.sql.DriverManager

/** Actual Liquibase parser and shipped Grails changesets; no Hibernate schema generation. */
class StorageMigrationFixture {
    static void migrate(String schema, String changelog = 'storage/complete.groovy') {
        def connection = DriverManager.getConnection(System.getenv('TOOLKIT_TEST_JDBC_URL'), 'test', 'test')
        def database = DatabaseFactory.instance.findCorrectDatabaseImplementation(new JdbcConnection(connection))
        database.defaultSchemaName = schema
        database.liquibaseSchemaName = schema
        new Liquibase(changelog, new CompositeResourceAccessor(new DirectoryResourceAccessor(new File('build/resources/main').toPath()), new DirectoryResourceAccessor(new File('build/resources/integrationTest').toPath())), database).withCloseable {
            it.update('')
        }
    }

    static void initializeOrmStorage() {
        grails.gorm.multitenancy.Tenants.withId('test') {
            FileUpload.withTransaction { FileUpload.count() }
        }
        Sql sql = Sql.newInstance(System.getenv('TOOLKIT_TEST_JDBC_URL'), 'test', 'test', 'org.postgresql.Driver')
        try {
            // Replace only empty Hibernate-generated storage tables with the actual baseline and migrations.
            sql.execute('DROP TABLE test.file_upload CASCADE')
            sql.execute('DROP TABLE test.file_object CASCADE')
            sql.execute('DROP TABLE IF EXISTS test.stored_s3_object CASCADE')
            migrate('test')
        } finally { sql.close() }
    }
}
