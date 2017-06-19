package com.k_int.web.toolkit

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
  
  def schema (String type) {
    def schema = JsonSchemaUtils.jsonSchema(type)
    render schema as JSON
  }
}
