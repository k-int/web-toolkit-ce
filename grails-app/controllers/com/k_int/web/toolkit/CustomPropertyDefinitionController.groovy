package com.k_int.web.toolkit

import com.k_int.web.toolkit.custprops.CustomPropertyDefinition
import com.k_int.web.toolkit.rest.RestfulController

import grails.web.Controller
import groovy.util.logging.Slf4j

@Slf4j
@Controller
class CustomPropertyDefinitionController extends RestfulController<CustomPropertyDefinition> {
  CustomPropertyDefinitionController() {
    super(CustomPropertyDefinition)
  }
  
  protected CustomPropertyDefinition createResource(Map params) {
    def obj = getObjectToBind()
    if (!obj.type) return super.createResource(params)
      
    resource.forType("${obj.type}", params)
  }
  
  protected CustomPropertyDefinition createResource() {
    def obj = getObjectToBind()
    if (!obj.type) return super.createResource()
      
    resource.forType("${obj.type}")
  }
}
