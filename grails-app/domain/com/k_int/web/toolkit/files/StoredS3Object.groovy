package com.k_int.web.toolkit.files

import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity

/** Physical ownership survives failed uploads and deletion of file references. */
@Entity
class StoredS3Object implements MultiTenant<StoredS3Object> {
    String id
    String endpoint
    String bucket
    String region
    String objectKey
    String objectVersion
    String state = 'UPLOADING'
    Date dateCreated
    Date lastUpdated
    String lastError

    static mapping = {
        table 'stored_s3_object'
        id generator: 'uuid2', length: 36
        version false
    }
    static constraints = {
        endpoint maxSize: 1024
        bucket maxSize: 255
        region maxSize: 255
        objectKey maxSize: 1024
        objectVersion nullable: true, maxSize: 1024
        state inList: ['UPLOADING', 'AVAILABLE', 'DELETE_PENDING']
        lastError nullable: true, maxSize: 255
    }
}
