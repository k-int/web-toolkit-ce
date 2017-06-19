package com.k_int.web.toolkit

import static org.springframework.http.HttpStatus.*

import org.grails.core.DefaultGrailsDomainClass

import grails.core.GrailsApplication


class ValidateController {
  
  static responseFormats = ['json', 'xml']
  static allowedMethods = [index: ["POST", "PATCH", "PUT"]]
  
  GrailsApplication grailsApplication
  
  def index(String domain, String prop) {
    
    // Try and locate the Domain class.
    DefaultGrailsDomainClass target = grailsApplication.getDomainClass("${domain}") ?: grailsApplication.domainClasses.find { it.clazz.simpleName == domain }
    
    // Do the 
    Class type
    if (!(target)) {
      return notFound();
    }
    
    // Create instance of object to validate.
    def object = target.newInstance()
    
    // Handle the id specially.
    def bindings = getObjectToBind()
    if (bindings.id) {
      object['id'] = bindings.id
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
