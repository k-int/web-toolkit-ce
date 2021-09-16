package com.k_int.web.toolkit


import grails.testing.mixin.integration.Integration
import grails.gorm.transactions.Rollback
import spock.lang.Specification

import com.k_int.web.toolkit.files.FileUploadService
import com.k_int.web.toolkit.files.FileUpload;

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile

/**
 * inspiration: https://github.com/craighewetson/grails_testcontainers_postgres
 *              https://www.infoq.com/presentations/grails-plugin-testing/
 */

@Integration
@Rollback
class ToolkitLifecycleSpec extends Specification {

    @Autowired
    FileUploadService fileUploadService

    def setup() {
    }

    def cleanup() {
    }

    void "test LOB file upload"() {
      when:"We ask the file upload service to upload a file and store it as a LOB"
        MultipartFile mf = new MockMultipartFile("foo", "foo.txt", "text/plain", "Hello World".getBytes())
        FileUpload fu = fileUploadService.save(mf);

      then:"The FileUpload is properly returned"
        fu != null
    }
}
