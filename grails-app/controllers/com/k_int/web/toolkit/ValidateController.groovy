package com.k_int.web.toolkit

import static org.springframework.http.HttpStatus.*

import org.grails.datastore.mapping.model.PersistentEntity

import com.k_int.web.toolkit.utils.DomainUtils

import grails.core.GrailsApplication


class ValidateController {
  
  static responseFormats = ['json', 'xml']
  static allowedMethods = [index: ["POST", "PATCH", "PUT"]]
  
  GrailsApplication grailsApplication
  
  def index(String domain, String prop) {
    
    // Try and locate the Domain class.
    PersistentEntity target = DomainUtils.findDomainClass(domain)
    
    // Do the 
    Class type
    if (!(target)) {
      return notFound();
    }
    
    // Create instance of object to validate.
    def object = target.newInstance()
    
    // Handle the id specially.
    def bindings = getObjectToBind()
    
    String idName = target.getIdentity().name
    
    if (idName) {
      object[idName] = bindings."${idName}"
    }
    
    // Bind the supplied properties.
    bindData(object, bindings)
    
    // Validate and respond with error messages and appropriate error code..
    if (prop) {
      // Validate only the single property.
      object.validate([prop])
    } else {
      // Validate entire object.
      object.validate()
    }
    if (object.hasErrors()) {
        respond object.errors
        return
    }
    
    // If all is well we should just respond with a NO_CONTENT response code. Denoting a successful
    // request without any content being returned.
    render (status : NO_CONTENT)
  }
  
  protected notFound () {
    // Not found response.
    render (status : NOT_FOUND)
    return
  }
  
  protected getObjectToBind() {
    request.JSON
  }
}
