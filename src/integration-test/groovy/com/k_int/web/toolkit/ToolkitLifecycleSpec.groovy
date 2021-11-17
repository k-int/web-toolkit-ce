package com.k_int.web.toolkit


import grails.testing.mixin.integration.Integration
import grails.gorm.transactions.Rollback
import spock.lang.Specification

import com.k_int.web.toolkit.files.FileUploadService
import com.k_int.web.toolkit.files.FileUpload;
import com.k_int.web.toolkit.files.LOBFileObject;
import com.k_int.web.toolkit.files.S3FileObject;

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile

import org.grails.datastore.mapping.multitenancy.resolvers.SystemPropertyTenantResolver


import grails.gorm.multitenancy.Tenants
import com.k_int.web.toolkit.settings.AppSetting
import com.k_int.web.toolkit.testing.HttpSpec



/**
 * inspiration: https://github.com/craighewetson/grails_testcontainers_postgres
 *              https://www.infoq.com/presentations/grails-plugin-testing/
 */

import groovy.util.logging.Slf4j

import spock.lang.Stepwise



@Slf4j
@Stepwise
@Integration
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
      [ 'fileStorage', 'S3Endpoint',    'String', null,                 'http://localhost:9000' ],
      [ 'fileStorage', 'S3AccessKey',   'String', null,                 'DIKU_AGG_ACCESS_KEY' ],
      [ 'fileStorage', 'S3SecretKey',   'String', null,                 'DIKU_AGG_SECRET_KEY' ],
      [ 'fileStorage', 'S3BucketName',  'String', null,                 'diku-shared' ],
      [ 'fileStorage', 'S3ObjectPrefix','String', null,                 '/diku/test-module/' ],
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
          MultipartFile mf = new MockMultipartFile("foo", "foo.txt", "text/plain", "Hello World".getBytes())
          fu = fileUploadService.save(mf);
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
            MultipartFile mf = new MockMultipartFile("foo", "foo.txt", "text/plain", "Hello World".getBytes())
            fu = fileUploadService.save(mf, FileUploadService.S3_STORAGE_ENGINE);

            String retrieved_file_contents = fileUploadService.getInputStreamFor(fu.fileObject).getText("UTF-8")
            log.debug("Retrieved file content: ${retrieved_file_contents}");
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
}
