package com.k_int.web.toolkit

import java.util.Map
import javax.servlet.http.HttpServletRequest
import com.k_int.web.toolkit.json.JsonSchemaUtils
import com.k_int.web.tools.RestfulResourceService

import grails.converters.JSON

class ConfigController {
  
  static responseFormats = ['json']
  RestfulResourceService restfulResourceService
  
  private String getRootLink(String type) {
    String theUri = request.forwardURI.toLowerCase().replaceAll("${type.toLowerCase()}(\\/?)\$", "").replaceAll(/\/\//, '/')
    
    String link = request.getHeader('host')
    
    if (!link) {
      link = grailsLinkGenerator.link(absolute: true, uri: theUri)
    } else {
      String proto = request.getHeader("X-Forwarded-Proto") ?: request.scheme
      link = "${proto}://${link}${theUri}"
    }
    
    link
  }
  
  def resources () {
    boolean extended = params.get('extended') == 'extended'
    render ([
      resources : restfulResourceService.getResourceInfo (extended)
    ] as JSON )
  }
  
  def schemaEmbedded (String type) {
    def schema = JsonSchemaUtils.jsonSchema(type, getRootLink(type), true)
    render schema as JSON
  }
  
  
  def schema (String type) {
    def schema = JsonSchemaUtils.jsonSchema(type, getRootLink(type) ,false)
    render schema as JSON
  }
}
