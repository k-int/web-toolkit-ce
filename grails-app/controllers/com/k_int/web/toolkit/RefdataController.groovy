package com.k_int.web.toolkit

import com.k_int.web.toolkit.custprops.CustomPropertyDefinition
import com.k_int.web.toolkit.refdata.GrailsDomainRefdataHelpers
import com.k_int.web.toolkit.refdata.RefdataCategory
import com.k_int.web.toolkit.rest.RestfulController
import com.k_int.web.toolkit.utils.DomainUtils

import grails.artefact.Artefact
import grails.web.Controller
import groovy.util.logging.Slf4j

@Slf4j
@Artefact('Controller')
class RefdataController extends RestfulController<RefdataCategory>  {

  RefdataController() {
    super(RefdataCategory)
  }
  
  def lookup (String domain, String property) {
    DomainUtils d
    def c = DomainUtils.resolveDomainClass(domain)?.javaClass
    def cat = c ? GrailsDomainRefdataHelpers.getCategoryString(c, property) : null
    
    // Bail if no cat.
    if (!cat) {
      render status: 404
    } else {
      forward action: "index", params: [filters: ["owner.desc==${cat}"]]
    }
  }
}