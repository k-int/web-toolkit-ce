package com.k_int.web.toolkit

import java.util.Map

import com.k_int.web.toolkit.json.JsonSchemaUtils
import com.k_int.web.tools.RestfulResourceService

import grails.converters.JSON

class ConfigController {
  
  static responseFormats = ['json']
  RestfulResourceService restfulResourceService
  
  
  
  def resources () {    
    render ([
      resources : restfulResourceService.resourceInfo
    ]) as JSON
  }
  
  def schemaEmbedded (String type) {

    def schema = JsonSchemaUtils.jsonSchema(type, grailsLinkGenerator.link(absolute: true, action: 'schemaEmbeeded'), true)
    render schema as JSON
  }
  
  
  private static final Map<String, Map> existingSchemas = [:]
  def schema (String type) {
    def schema = JsonSchemaUtils.jsonSchema(type, grailsLinkGenerator.link(absolute: true, action: 'schema') ,false, existingSchemas)
    render schema as JSON
  }
}
