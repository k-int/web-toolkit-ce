package com.k_int.web.toolkit.files
import com.k_int.web.toolkit.domain.traits.Clonable

import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity
import grails.gorm.multitenancy.Tenants
import groovy.util.logging.Slf4j

@Slf4j
@Entity
class FileUpload implements MultiTenant<FileUpload>, Clonable<FileUpload> {

  String id
  FileObject fileObject
  
  String fileContentType
  String fileName
  Long fileSize
  Date lastUpdated
  SingleFileAttachment owner
  
  static copyByCloning = ['fileObject']

  static constraints = {
    fileObject nullable: false
    fileContentType nullable: true
    lastUpdated nullable: true
    owner nullable: true
  }

  static mapping = {
                  id column: 'fu_id', generator: 'uuid2', length: 36
    fileContentBytes column: 'fu_file_object'
            fileName column: 'fu_filename'
            fileSize column: 'fu_filesize'
        lastUpdated column: 'fu_last_mod'
               owner column: 'fu_owner', type: 'string', length: 36
  }

  def afterUpdate() {
    log.info("afterUpdate() for FileUpload")

    if (this.owner == null) {
      final String toDelete = this.id
      final Serializable currentTenantId = Tenants.currentId()
      Tenants.withId(currentTenantId) {
        try {
          FileUpload.get(toDelete).delete()
        } catch(Exception e) {
          log.error("Error trying to delete ownerless fileUpload objects: ${e.getMessage()}")
        }

      }
    }
  }
  
  @Override
  public FileUpload clone () {
    Clonable.super.clone()
  }
}
