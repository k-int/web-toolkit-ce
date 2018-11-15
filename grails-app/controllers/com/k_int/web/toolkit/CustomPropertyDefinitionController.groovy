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
}
