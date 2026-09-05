package com.k_int.web.toolkit.files

import groovy.sql.Sql
import javax.sql.DataSource
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy
import grails.gorm.multitenancy.Tenants
import io.minio.*
import io.minio.errors.ErrorResponseException
import io.minio.messages.VersioningConfiguration
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

/** Existing S3 storage, with durable ownership and post-commit deletion. */
class StoredS3ObjectService {
    DataSource dataSource
    S3FileObject upload(String key, InputStream stream, long size, long partSize) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException('S3 file persistence requires a transaction')
        }
        Map config = configuration()
        String id = UUID.randomUUID().toString()
        independent { Sql sql, String objects, String files ->
            sql.executeInsert(("INSERT INTO " + objects + " (id,endpoint,bucket,region,object_key,state,date_created,last_updated) ") +
                "VALUES (?,?,?,?,?,'UPLOADING',current_timestamp,current_timestamp)",
                [id, config.endpoint, config.bucket, config.region, key])
        }
        Serializable tenant = Tenants.currentId()
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    abandonUpload(id, tenant)
                }
            }
        })
        def receipt = client(config).putObject(PutObjectArgs.builder().bucket(config.bucket)
            .region(config.region).object(key).stream(stream, size, partSize).build())
        // Retain the response independently of the file's transaction, so its
        // rollback cannot lose an exact version identity for uploaded bytes.
        independent { Sql sql, String objects, String files ->
            sql.executeUpdate(("UPDATE " + objects + " SET object_version=?,last_updated=current_timestamp WHERE id=?"),
                [receipt.versionId(), id])
        }
        new S3FileObject(s3ref: key, storedObject: StoredS3Object.get(id))
    }

    InputStream read(S3FileObject file) {
        Map config = configuration()
        StoredS3Object object = file.storedObject
        if (object != null) requireLocation(config, object)
        def request = GetObjectArgs.builder().bucket(object?.bucket ?: config.bucket)
            .region(object?.region ?: config.region).object(object?.objectKey ?: file.s3ref)
        if (object?.objectVersion != null) request.versionId(object.objectVersion)
        client(config).getObject(request.build())
    }

    boolean configured() {
        try { configuration(); return true }
        catch (IllegalStateException absent) { return false }
    }

    void cleanupAfterCommit(String id) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException('S3 reference deletion requires a transaction')
        }
        Serializable tenant = Tenants.currentId()
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override void afterCommit() { cleanupInTenant(id, tenant) }
        })
    }

    /** Exact record retry. A failed delete remains owned and visible for retry. */
    boolean cleanup(String id) {
        cleanupInTenant(id, Tenants.currentId())
    }

    private boolean cleanupInTenant(String id, Serializable tenant) {
        try {
            return independent(tenant) { Sql sql, String objects, String files ->
                def object = sql.firstRow(("SELECT * FROM " + objects + " WHERE id=? FOR UPDATE"), [id])
                if (object == null) return true
                if (object.state != 'DELETE_PENDING') return false
                if (sql.firstRow(("SELECT EXISTS(SELECT 1 FROM " + files + " WHERE fo_stored_s3_object_id=?) AS referenced"), [id]).referenced) return false
                Map config = configuration(sql)
                requireLocation(config, object)
                MinioClient storage = client(config)
                if (object.object_version == null && storage.getBucketVersioning(
                    GetBucketVersioningArgs.builder().bucket(object.bucket).build()).status() != VersioningConfiguration.Status.OFF) {
                    throw new IllegalStateException('Missing exact object version for versioned storage')
                }
                def deletion = RemoveObjectArgs.builder().bucket(object.bucket).region(object.region).object(object.object_key)
                if (object.object_version != null) deletion.versionId(object.object_version)
                storage.removeObject(deletion.build())
                def verification = StatObjectArgs.builder().bucket(object.bucket).region(object.region).object(object.object_key)
                if (object.object_version != null) verification.versionId(object.object_version)
                try {
                    storage.statObject(verification.build())
                    throw new IllegalStateException('S3 object remains after deletion')
                } catch (ErrorResponseException absent) {
                    if (!(absent.errorResponse().code() in ['NoSuchKey', 'NoSuchVersion', 'NoSuchObject'])) throw absent
                }
                sql.executeUpdate(("DELETE FROM " + objects + " WHERE id=?"), [id])
                true
            }
        } catch (Exception failure) {
            // Do not retain provider response bodies or credentials in diagnostics.
            try {
                independent(tenant) { Sql sql, String objects, String files ->
                    sql.executeUpdate(("UPDATE " + objects + " SET last_error=?,last_updated=current_timestamp WHERE id=?"),
                        [failure.class.simpleName.take(255), id])
                }
            } catch (Exception unavailable) { /* Existing ownership row remains. */ }
            log.warn('S3 object cleanup remains pending: {}', id)
            false
        }
    }

    int cleanupPending(int limit = 25) {
        if (limit < 1 || limit > 100) throw new IllegalArgumentException('Cleanup limit must be 1..100')
        List<String> ids = independent { Sql sql, String objects, String files ->
            sql.rows(("SELECT id FROM " + objects + " WHERE state='DELETE_PENDING' ORDER BY last_updated,id LIMIT ?"), [limit])*.id
        }
        ids.count { String id -> cleanup(id) }
    }

    /** Called synchronously after the owning module quiesces and disables a tenant. */
    void purgeOwned(Serializable tenant) {
        // Resolve configuration before changing references. Empty LOB-only
        // tenants do not need working S3 configuration to be purged.
        independent(tenant) { Sql sql, String objects, String files ->
            if (sql.firstRow('SELECT EXISTS(SELECT 1 FROM ' + objects + ') AS present').present) {
                Map config = configuration(sql)
                if (sql.firstRow('SELECT EXISTS(SELECT 1 FROM ' + objects +
                    ' WHERE endpoint<>? OR bucket<>? OR region<>?) AS mismatch',
                    [config.endpoint, config.bucket, config.region]).mismatch) {
                    throw new IllegalStateException('S3 configuration does not match retained ownership')
                }
            }
        }
        while (true) {
            List<String> ids = independent(tenant) { Sql sql, String objects, String files ->
                sql.rows('SELECT id FROM ' + objects + ' ORDER BY id LIMIT 100')*.id
            }
            if (ids.isEmpty()) return
            for (String id : ids) {
                independent(tenant) { Sql sql, String objects, String files ->
                    def object = sql.firstRow('SELECT id FROM ' + objects + ' WHERE id=? FOR UPDATE', [id])
                    if (object != null) {
                        sql.executeUpdate('UPDATE ' + files +
                            ' SET fo_stored_s3_object_id=NULL,fo_s3ref=NULL WHERE fo_stored_s3_object_id=?', [id])
                        sql.executeUpdate('UPDATE ' + objects +
                            " SET state='DELETE_PENDING',last_updated=current_timestamp WHERE id=?", [id])
                    }
                }
                if (!cleanupInTenant(id, tenant)) throw new IllegalStateException('S3 tenant cleanup incomplete')
            }
        }
    }

    /** Managed reset must reject legacy keys whose physical ownership is unknown. */
    void requireCompleteOwnership(Serializable tenant) {
        independent(tenant) { Sql sql, String objects, String files ->
            if (sql.firstRow('SELECT EXISTS(SELECT 1 FROM ' + files +
                ' WHERE fo_s3ref IS NOT NULL AND fo_stored_s3_object_id IS NULL) AS legacy').legacy) {
                throw new IllegalStateException('S3 tenant contains unqualified legacy ownership')
            }
            if (sql.firstRow('SELECT EXISTS(SELECT 1 FROM ' + objects + ') AS present').present) {
                Map config = configuration(sql)
                if (client(config).getBucketVersioning(GetBucketVersioningArgs.builder().bucket(config.bucket).build()).status()
                    != VersioningConfiguration.Status.OFF) {
                    throw new IllegalStateException('Managed reset requires qualified unversioned storage')
                }
            }
        }
    }

    private void abandonUpload(String id, Serializable tenant) {
        try {
            independent(tenant) { Sql sql, String objects, String files ->
                def object = sql.firstRow(("SELECT id FROM " + objects + " WHERE id=? FOR UPDATE"), [id])
                if (object != null && !sql.firstRow(("SELECT EXISTS(SELECT 1 FROM " + files + " WHERE fo_stored_s3_object_id=?) AS referenced"), [id]).referenced) {
                    sql.executeUpdate(("UPDATE " + objects + " SET state='DELETE_PENDING',last_updated=current_timestamp WHERE id=?"), [id])
                }
            }
            cleanupInTenant(id, tenant)
        } catch (Exception failure) {
            log.warn('S3 upload ownership retained for reconciliation: {}', id)
        }
    }

    private <T> T independent(Closure<T> work) {
        independent(Tenants.currentId(), work)
    }

    private <T> T independent(Serializable tenant, Closure<T> work) {
        String schema = tenant.toString()
        if (!schema || schema.indexOf(0) >= 0 || schema.getBytes('UTF-8').length > 63) {
            throw new IllegalArgumentException('Invalid tenant schema')
        }
        String quoted = '"' + schema.replace('"', '""') + '"'
        DataSource source = dataSource
        while (source instanceof TransactionAwareDataSourceProxy) source = source.targetDataSource
        def connection = source.connection
        Sql sql = new Sql(connection)
        try {
            if (!connection.autoCommit) throw new IllegalStateException('Independent ownership connection required')
            connection.autoCommit = false
            try {
                sql.execute("SET LOCAL lock_timeout='5s'")
                sql.execute('SET LOCAL search_path TO ' + quoted)
                sql.withStatement { it.queryTimeout = 10 }
                T result = work.call(sql, quoted + '.stored_s3_object', quoted + '.file_object')
                connection.commit()
                return result
            } catch (Throwable failure) {
                // Sql.withTransaction logs exception messages, which can include
                // external provider diagnostics. Retain only bounded error types.
                connection.rollback()
                throw failure
            }
        } finally {
            sql.close()
        }
    }

    private static void requireLocation(Map config, Object object) {
        if (config.endpoint != object.endpoint || config.bucket != object.bucket || config.region != object.region) {
            throw new IllegalStateException('S3 configuration does not match the owned object location')
        }
    }

    private static MinioClient client(Map config) {
        MinioClient.builder().endpoint(config.endpoint).credentials(config.access, config.secret).build()
    }

    private Map configuration() {
        independent { Sql sql, String objects, String files -> configuration(sql) }
    }

    private static Map configuration(Sql sql) {
        // Explicit schema is already installed on this independent connection.
        // No ORM/session re-entry from transaction completion callbacks.
        List rows = sql.rows("SELECT st_key,coalesce(st_value,st_default_value) AS value FROM app_setting " +
            "WHERE st_section='fileStorage' AND st_key IN " +
            "('S3Endpoint','S3BucketName','S3BucketRegion','S3AccessKey','S3SecretKey','S3SecretEnvironmentVariable') " +
            "ORDER BY st_id LIMIT 7")
        Map settings = [:]
        rows.each { row ->
            if (settings.containsKey(row.st_key)) throw new IllegalStateException('Ambiguous S3 configuration')
            settings[row.st_key] = row.value
        }
        String secret = settings.S3SecretKey
        if (secret == null) secret = System.getenv(settings.S3SecretEnvironmentVariable ?: 'GLOBAL_S3_SECRET_KEY')
        Map config = [endpoint: settings.S3Endpoint, bucket: settings.S3BucketName,
            region: settings.S3BucketRegion ?: 'us-east-1', access: settings.S3AccessKey, secret: secret]
        if (config.values().any { it == null || it.toString().isBlank() }) throw new IllegalStateException('S3 storage is not configured')
        config
    }
}
