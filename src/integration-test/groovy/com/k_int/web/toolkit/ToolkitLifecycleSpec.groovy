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
import spock.lang.Requires
import spock.lang.Stepwise

@Slf4j
@Stepwise
@Integration
@Requires({Environment.currentEnvironment.name == 'test-livedb'})
class ToolkitLifecycleSpec extends HttpSpec {
  @Autowired
  FileUploadService fileUploadService

  def setup() {
    System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'test') 
  }

  def cleanup() {
    System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, '')
  }

  def setupData() {
    [
      [ 'fileStorage', 'storageEngine', 'String', 'FileStorageEngines', 'LOB' ],
      [ 'fileStorage', 'S3Endpoint',    'String', null,                 'http://localhost:9009' ],
      [ 'fileStorage', 'S3AccessKey',   'String', null,                 'DIKU_AGG_ACCESS_KEY' ],
      [ 'fileStorage', 'S3SecretKey',   'String', null,                 'DIKU_AGG_SECRET_KEY' ],
      [ 'fileStorage', 'S3BucketName',  'String', null,                 'diku-shared' ],
      [ 'fileStorage', 'S3ObjectPrefix','String', null,                 '/diku/test-module/' ],
      [ 'fileStorage', 'S3BucketRegion','String', null,                 'us-east-1' ],
    ].each { st_row ->
      log.debug("Adding setting ${st_row}");
      AppSetting new_as = new AppSetting( 
                                        section:st_row[0], 
                                        key:st_row[1], 
                                        settingType:st_row[2], 
                                        vocab:st_row[3], 
                                        value:st_row[4]).save(flush:true, failOnError:true);

    }

    AppSetting.list().each { as_entry ->
      log.debug("App setting ${as_entry} ${as_entry.section}/${as_entry.key} = ${as_entry.value}");
    }
  }

  void "test LOB file upload"() {
    given:
      Tenants.withId('test') {
        AppSetting.withTransaction { status ->
          setupData();
        }
      }

    when:"We ask the file upload service to upload a file and store it as a LOB"
      FileUpload fu = null;
      Tenants.withId('test') {
        FileUpload.withTransaction { status ->
          MultipartFile mf = new MockMultipartFile("foo-lob.txt", "foo-lob.txt", "text/plain", "Hello World - LOB version".getBytes())
          fu = fileUploadService.save(mf);
          log.debug("Saved LOB test file as ${fu.fileName}");
        }
      }

    then:"The FileUpload is properly returned"
      fu != null
  }

  void "test S3 file upload"() {
    when:"We ask the file upload service to upload a file and store it as a LOB"
        FileUpload fu = null;
        Tenants.withId('test') {
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
      Tenants.withId('test') {
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
      Tenants.withId('test') {
        FileUpload.withTransaction { status ->
          fileUploadService.migrateAtMost(100,'LOB','S3');
        }
      }

    then: "Files migrated"
      long post_migration_lob_count = 0;
      long post_migration_s3_count = 0;
      Tenants.withId('test') {
        post_migration_lob_count = LOBFileObject.executeQuery('select count(*) from LOBFileObject').get(0);
        post_migration_s3_count = S3FileObject.executeQuery('select count(*) from S3FileObject').get(0);
      }
      post_migration_lob_count == 0;
      post_migration_s3_count == 27;
  }

  void "test object retrieval post migration"() {
    when: "We create lots of LOB objects"

      String retrieved_content = null;

      Tenants.withId('test') {
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

        Tenants.withId('test') {
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

}
