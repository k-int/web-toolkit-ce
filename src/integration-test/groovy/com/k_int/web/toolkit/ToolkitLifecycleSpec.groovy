package com.k_int.web.toolkit


import org.grails.datastore.mapping.multitenancy.resolvers.SystemPropertyTenantResolver
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile

import com.k_int.web.toolkit.files.FileUpload;
import com.k_int.web.toolkit.files.FileUploadService
import com.k_int.web.toolkit.files.LOBFileObject;
import com.k_int.web.toolkit.files.S3FileObject;
import com.k_int.web.toolkit.settings.AppSetting
import com.k_int.web.toolkit.testing.HttpSpec

import grails.gorm.multitenancy.Tenants
import grails.testing.mixin.integration.Integration
import grails.util.Environment
import groovy.util.logging.Slf4j
import net.bytebuddy.asm.Advice.This
import spock.lang.Requires
import spock.lang.Stepwise

@Slf4j
@Stepwise
@Integration
@Requires({Environment.currentEnvironment.name == 'test-livedb'})
class ToolkitLifecycleSpec extends HttpSpec {
  @Autowired
  FileUploadService fileUploadService
	
	private static final String TENANT_ID = 'test' 

  @spock.lang.Shared boolean storageInitialized = false

  def setup() {
    if (!storageInitialized) {
      com.k_int.web.toolkit.files.StorageMigrationFixture.initializeOrmStorage()
      storageInitialized = true
    }
    System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, TENANT_ID) 
  }

  def cleanup() {
    System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, '')
  }

  def setupData() {
		
    [
      [ 'fileStorage', 'storageEngine', 'String', 'FileStorageEngines', 'LOB' ],
      [ 'fileStorage', 'S3Endpoint',    'String', null,                 System.getenv('TOOLKIT_TEST_S3_ENDPOINT') ?: 'http://localhost:9009' ],
      [ 'fileStorage', 'S3AccessKey',   'String', null,                 'DIKU_AGG_ACCESS_KEY' ],
      [ 'fileStorage', 'S3SecretKey',   'String', null,                 'DIKU_AGG_SECRET_KEY' ],
      [ 'fileStorage', 'S3BucketName',  'String', null,                 'diku-shared' ],
      [ 'fileStorage', 'S3ObjectPrefix','String', null,                 '/diku/test-module/' ],
      [ 'fileStorage', 'S3BucketRegion','String', null,                 'us-east-1' ],
    ].each {
			
			def (String section, String key, String settingType, String vocab, String value) = it
			
      log.debug("Adding setting ${it}")
	      AppSetting new_as = new AppSetting( 
          'section': section, 
          'key': key, 
          'settingType': settingType, 
          'vocab': vocab, 
          'value': value).save(flush:true, failOnError:true);
    }

    AppSetting.list().each { as_entry ->
      log.debug("App setting ${as_entry} ${as_entry.section}/${as_entry.key} = ${as_entry.value}");
    }
  }

  void "test LOB file upload"() {
    given:
      Tenants.withId(TENANT_ID) {
        AppSetting.withTransaction { status ->
          setupData();
        }
      }

    when:"We ask the file upload service to upload a file and store it as a LOB"
      FileUpload fu = null;
      Tenants.withId(TENANT_ID) {
        FileUpload.withTransaction { status ->
          MultipartFile mf = new MockMultipartFile("foo-lob.txt", "foo-lob.txt", "text/plain", "Hello World - LOB version".getBytes())
          fu = fileUploadService.save(mf);
          log.debug("Saved LOB test file as ${fu?.fileName}");
        }
      }

    then:"The FileUpload is properly returned"
      fu != null
  }

  void "test S3 file upload"() {
    when:"We ask the file upload service to upload a file and store it as a LOB"
        FileUpload fu = null;
        Tenants.withId(TENANT_ID) {
          FileUpload.withTransaction { status ->
            String initial_upload = "Hello World"
            MultipartFile mf = new MockMultipartFile("foo", "foo.txt", "text/plain", initial_upload.getBytes())
            fu = fileUploadService.save(mf, FileUploadService.S3_STORAGE_ENGINE);

            String retrieved_file_contents = fileUploadService.getInputStreamFor(fu.fileObject).getText("UTF-8")
            log.debug("Saved file upload with name ${fu.fileName} - Retrieved file content: ${retrieved_file_contents}");
            assert retrieved_file_contents.equals(initial_upload)
          }
        }

    then:"The FileUpload is properly returned"
      fu != null

  }

  void "test migration"() {
    when: "We create lots of LOB objects"
      Tenants.withId(TENANT_ID) {
        FileUpload.withTransaction { status ->
          for ( int i=0; i<25; i++ ) {
            String file_content = "Hello World ${i}";
            MultipartFile mf = new MockMultipartFile("foo-${i}", "foo-${i}.txt", "text/plain", file_content.getBytes())
            FileUpload fu = fileUploadService.save(mf, FileUploadService.LOB_STORAGE_ENGINE);
            log.debug("Created LOB test record: ${i}");

            // Retrieve content
            String retrieved_file_contents = fileUploadService.getInputStreamFor(fu.fileObject).getText("UTF-8") 
            log.debug("Retrieved file content: ${retrieved_file_contents}");
            assert file_content.equals(retrieved_file_contents)
          }
        }
      }

    then: "We ask for files to be migrated"
      Tenants.withId(TENANT_ID) {
        FileUpload.withTransaction { status ->
          fileUploadService.migrateAtMost(100,'LOB','S3');
        }
      }

    then: "Files migrated"
      long post_migration_lob_count = 0;
      long post_migration_s3_count = 0;
      Tenants.withId(TENANT_ID) {
        post_migration_lob_count = LOBFileObject.executeQuery('select count(*) from LOBFileObject').get(0);
        post_migration_s3_count = S3FileObject.executeQuery('select count(*) from S3FileObject').get(0);
      }
      post_migration_lob_count == 0;
      post_migration_s3_count == 27;
  }

  void "test object retrieval post migration"() {
    when: "We create lots of LOB objects"

      String retrieved_content = null;

      Tenants.withId(TENANT_ID) {
        FileUpload.withTransaction { status ->
          FileUpload.list().each { fu ->
            log.debug("[${fu.id}] ${fu.fileName} ${fu.fileObject.class.name}");
            if ( fu.fileObject != null ) {
              InputStream is = fileUploadService.getInputStreamFor(fu.fileObject)
              if ( is ) {
                String s = is.getText("UTF-8")
                log.debug(s);
              }
              else {
                log.warn("getInputStreamFor returned NULL");
              }
            }
            else {
              log.warn("MISSING file object");
            }
          }

          FileUpload fu = FileUpload.findByFileName('foo-lob.txt');
          if ( fu != null ) {
            InputStream is = fileUploadService.getInputStreamFor(fu.fileObject)
            if ( is ) {
              retrieved_content = is.getText("UTF-8")
            }
          }
          else {
            log.warn("Unable to locate foo-lob.txt");
          }
        }
      }

    then: "Check content is as expected"
      retrieved_content.equals('Hello World - LOB version')
  }

  void "test clone S3 File Object"() {
    when:"We try to clone an S3 file"

        String created_fu_id = null;
        String cloned_fu_id = null;

        Tenants.withId(TENANT_ID) {
          FileUpload.withTransaction { status ->

            FileUpload fu = null;

            String initial_upload = "Hello World"
            MultipartFile mf = new MockMultipartFile("foo86", "foo86.txt", "text/plain", initial_upload.getBytes())
            fu = fileUploadService.save(mf, FileUploadService.S3_STORAGE_ENGINE);
            String retrieved_file_contents = fileUploadService.getInputStreamFor(fu.fileObject).getText("UTF-8")
            log.debug("Saved file upload with name ${fu.fileName} - Retrieved file content: ${retrieved_file_contents}");
            assert retrieved_file_contents.equals(initial_upload)
            created_fu_id = fu.id;
            // fu2 = fu.clone();
            // fu2.save(flush:true, failOnError:true)

            FileUpload.withSession { session ->
              session.flush()
              session.clear()
            }
          }

          FileUpload.withTransaction { status ->
            FileUpload fu_loaded = FileUpload.executeQuery('select fu from FileUpload as fu').find { it.id == created_fu_id }
            if ( fu_loaded != null ) {
              log.debug("Got instance of ${fu_loaded?.class?.name}");
              FileUpload fu2 = fu_loaded.clone();
              fu2.save(flush:true, failOnError:true);
              cloned_fu_id = fu2.id;
              log.debug("File upload cloned -- ${cloned_fu_id}");
            }
            else {
              log.warn("Unable to find file upload");
            }
          }
        }

    then:"The FileUpload is properly returned"
      cloned_fu_id != null

  }

  @spock.lang.Unroll
  void "missing #trigger blocks #engine upload without opening its stream"() {
    given:
    def sql = groovy.sql.Sql.newInstance(System.getenv('TOOLKIT_TEST_JDBC_URL'), 'test', 'test', 'org.postgresql.Driver')
    sql.execute('ALTER TABLE test.file_object DISABLE TRIGGER ' + trigger)
    long owners = sql.firstRow('SELECT count(*) AS n FROM test.stored_s3_object').n
    long objects = sql.firstRow('SELECT count(*) AS n FROM pg_largeobject_metadata').n
    def multipart = Mock(MultipartFile)

    when:
    Tenants.withId(TENANT_ID) {
      FileUpload.withTransaction {
        fileUploadService.save(multipart, engine)
      }
    }

    then:
    thrown(com.k_int.web.toolkit.files.StorageSchemaPrerequisiteException)
    0 * multipart.getInputStream()
    sql.firstRow('SELECT count(*) AS n FROM test.stored_s3_object').n == owners
    sql.firstRow('SELECT count(*) AS n FROM pg_largeobject_metadata').n == objects

    cleanup:
    sql.execute('ALTER TABLE test.file_object ENABLE TRIGGER ' + trigger)
    sql.close()

    where:
    [engine, trigger] << [['LOB', 'S3'], ['file_object_owned_lob', 'stored_s3_reference_lock', 'stored_s3_reference_removed']].combinations()
  }

  void "LOB callbacks block replacement and deletion before physical mutation"() {
    given:
    String id
    Tenants.withId(TENANT_ID) {
      FileUpload.withTransaction {
        id = fileUploadService.save(new MockMultipartFile('guard', 'guard.txt', 'text/plain', 'keep'.bytes), 'LOB').fileObject.id
      }
    }
    def sql = groovy.sql.Sql.newInstance(System.getenv('TOOLKIT_TEST_JDBC_URL'), 'test', 'test', 'org.postgresql.Driver')
    sql.execute('ALTER TABLE test.file_object DISABLE TRIGGER file_object_owned_lob')
    long objects = sql.firstRow('SELECT count(*) AS n FROM pg_largeobject_metadata').n

    when:
    Tenants.withId(TENANT_ID) {
      LOBFileObject.withTransaction {
        def file = LOBFileObject.get(id)
        file.setFileContents(new ByteArrayInputStream('replace'.bytes), 7)
        file.save(flush: true, failOnError: true)
      }
    }

    then:
    thrown(com.k_int.web.toolkit.files.StorageSchemaPrerequisiteException)
    sql.firstRow('SELECT count(*) AS n FROM pg_largeobject_metadata').n == objects
    sql.firstRow("SELECT convert_from(lo_get(file_contents),'UTF8') AS contents FROM test.file_object WHERE fo_id=?", [id]).contents == 'keep'

    when:
    Tenants.withId(TENANT_ID) {
      LOBFileObject.withTransaction {
        LOBFileObject.get(id).fileUpload.delete(flush: true)
      }
    }

    then:
    thrown(com.k_int.web.toolkit.files.StorageSchemaPrerequisiteException)
    sql.firstRow('SELECT count(*) AS n FROM test.file_object WHERE fo_id=?', [id]).n == 1

    cleanup:
    sql.execute('ALTER TABLE test.file_object ENABLE TRIGGER file_object_owned_lob')
    sql.close()
  }

  void "missing S3 safeguard propagates through cleanup and configuration checks"() {
    given:
    def sql = groovy.sql.Sql.newInstance(System.getenv('TOOLKIT_TEST_JDBC_URL'), 'test', 'test', 'org.postgresql.Driver')
    sql.execute('ALTER TABLE test.file_object DISABLE TRIGGER stored_s3_reference_removed')

    when:
    Tenants.withId(TENANT_ID) { fileUploadService.storedS3ObjectService.cleanup('absent') }

    then:
    thrown(com.k_int.web.toolkit.files.StorageSchemaPrerequisiteException)

    when:
    Tenants.withId(TENANT_ID) { fileUploadService.storedS3ObjectService.configured() }

    then:
    thrown(com.k_int.web.toolkit.files.StorageSchemaPrerequisiteException)

    when:
    Tenants.withId(TENANT_ID) { fileUploadService.storedS3ObjectService.cleanupPending(1) }

    then:
    thrown(com.k_int.web.toolkit.files.StorageSchemaPrerequisiteException)

    when:
    Tenants.withId(TENANT_ID) { FileUpload.withTransaction { fileUploadService.migrateAtMost(1, 'LOB', 'S3') } }

    then:
    thrown(com.k_int.web.toolkit.files.StorageSchemaPrerequisiteException)

    cleanup:
    sql.execute('ALTER TABLE test.file_object ENABLE TRIGGER stored_s3_reference_removed')
    sql.close()
  }

  void "real migrated LOB clones keep independent bytes through replacement and deletion"() {
    given:
    String originalId
    String cloneId
    String originalObjectId
    String cloneObjectId
    Tenants.withId(TENANT_ID) {
      FileUpload.withTransaction {
        def original = fileUploadService.save(new MockMultipartFile('cloned', 'cloned.txt', 'text/plain', 'original'.bytes), 'LOB')
        def copy = original.clone().save(flush: true, failOnError: true)
        originalId = original.id
        cloneId = copy.id
        originalObjectId = original.fileObject.id
        cloneObjectId = copy.fileObject.id
      }
    }
    def sql = groovy.sql.Sql.newInstance(System.getenv('TOOLKIT_TEST_JDBC_URL'), 'test', 'test', 'org.postgresql.Driver')
    long originalOid = sql.firstRow('SELECT file_contents FROM test.file_object WHERE fo_id=?', [originalObjectId]).file_contents as long
    long cloneOid = sql.firstRow('SELECT file_contents FROM test.file_object WHERE fo_id=?', [cloneObjectId]).file_contents as long

    when:
    Tenants.withId(TENANT_ID) {
      FileUpload.withTransaction {
        def file = org.hibernate.Hibernate.unproxy(FileUpload.get(originalId).fileObject)
        file.setFileContents(new ByteArrayInputStream('replacement'.bytes), 11)
        file.save(flush: true, failOnError: true)
      }
    }

    then:
    originalOid != cloneOid
    !sql.firstRow('SELECT EXISTS(SELECT 1 FROM pg_largeobject_metadata WHERE oid=?) AS present', [originalOid]).present
    sql.firstRow("SELECT convert_from(lo_get(?),'UTF8') AS content", [cloneOid]).content == 'original'

    when:
    Tenants.withId(TENANT_ID) {
      FileUpload.withTransaction {
        FileUpload.get(originalId).delete(flush: true)
        FileUpload.get(cloneId).delete(flush: true)
      }
    }

    then:
    !sql.firstRow('SELECT EXISTS(SELECT 1 FROM pg_largeobject_metadata WHERE oid=?) AS present', [cloneOid]).present
    sql.firstRow('SELECT count(*) AS n FROM test.file_object WHERE fo_id IN (?,?)', [originalObjectId, cloneObjectId]).n == 0

    cleanup:
    sql.close()
  }

  void "real migrated S3 sharing, replacement and last-reference cleanup remove physical bytes"() {
    given:
    String originalId
    String cloneId
    String ownerId
    String oldKey
    String replacementKey
    String replacementOwner
    Tenants.withId(TENANT_ID) {
      FileUpload.withTransaction {
        def original = fileUploadService.save(new MockMultipartFile('s3shared', 's3shared.txt', 'text/plain', 'shared'.bytes), 'S3')
        def copy = original.clone().save(flush: true, failOnError: true)
        originalId = original.id
        cloneId = copy.id
        ownerId = original.fileObject.storedObject.id
        oldKey = original.fileObject.s3ref
      }
    }
    Tenants.withId(TENANT_ID) { FileUpload.withTransaction { FileUpload.get(originalId).delete(flush: true) } }
    def storage = io.minio.MinioClient.builder().endpoint(System.getenv('TOOLKIT_TEST_S3_ENDPOINT'))
        .credentials('DIKU_AGG_ACCESS_KEY', 'DIKU_AGG_SECRET_KEY').build()

    expect:
    storage.getObject(io.minio.GetObjectArgs.builder().bucket('diku-shared').object(oldKey).build()).withCloseable { it.text } == 'shared'

    when:
    Tenants.withId(TENANT_ID) {
      FileUpload.withTransaction {
        replacementKey = 'replacement-' + UUID.randomUUID()
        def replacement = fileUploadService.storedS3ObjectService.upload(replacementKey, new ByteArrayInputStream('new'.bytes), 3, -1)
        replacementOwner = replacement.storedObject.id
        def file = org.hibernate.Hibernate.unproxy(FileUpload.get(cloneId).fileObject)
        file.s3ref = replacement.s3ref
        file.storedObject = replacement.storedObject
        file.save(flush: true, failOnError: true)
      }
      assert fileUploadService.storedS3ObjectService.cleanup(ownerId)
    }
    storage.statObject(io.minio.StatObjectArgs.builder().bucket('diku-shared').object(oldKey).build())

    then:
    def absent = thrown(io.minio.errors.ErrorResponseException)
    absent.errorResponse().code() in ['NoSuchKey', 'NoSuchObject']
    storage.getObject(io.minio.GetObjectArgs.builder().bucket('diku-shared').object(replacementKey).build()).withCloseable { it.text } == 'new'

    when:
    Tenants.withId(TENANT_ID) { FileUpload.withTransaction { FileUpload.get(cloneId).delete(flush: true) } }
    storage.statObject(io.minio.StatObjectArgs.builder().bucket('diku-shared').object(replacementKey).build())

    then:
    def lastAbsent = thrown(io.minio.errors.ErrorResponseException)
    lastAbsent.errorResponse().code() in ['NoSuchKey', 'NoSuchObject']
    Tenants.withId(TENANT_ID) { com.k_int.web.toolkit.files.StoredS3Object.withTransaction { com.k_int.web.toolkit.files.StoredS3Object.get(replacementOwner) == null } }
  }

}
