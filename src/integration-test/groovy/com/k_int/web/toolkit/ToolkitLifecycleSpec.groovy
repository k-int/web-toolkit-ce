package com.k_int.web.toolkit


import grails.testing.mixin.integration.Integration
import grails.gorm.transactions.Rollback
import spock.lang.Specification

import com.k_int.web.toolkit.files.FileUploadService
import com.k_int.web.toolkit.files.FileUpload;

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
        [ 'fileStorage', 'S3BucketName', 'String', null, 'KITWBlobStore' ],
        [ 'fileStorage', 'S3BucketUser', 'String', null, 'testuser' ],
        [ 'fileStorage', 'S3BucketPass', 'String', null, 'testpass' ],
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
}
