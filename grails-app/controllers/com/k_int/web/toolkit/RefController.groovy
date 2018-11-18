package com.k_int.web.toolkit

import static org.springframework.http.HttpStatus.*

import org.grails.datastore.mapping.model.PersistentEntity

import com.k_int.web.toolkit.SimpleLookupService
import com.k_int.web.toolkit.utils.DomainUtils

import grails.core.GrailsApplication
import grails.gorm.transactions.Transactional


class RefController {

  static responseFormats = ['json', 'xml']

  GrailsApplication grailsApplication
  SimpleLookupService simpleLookupService

  @Transactional(readOnly = true)
  def get (String domain, String prop, String search) {

    // Try and locate the Domain class.
    PersistentEntity target = DomainUtils.findDomainClass (domain)

    if (!(target && prop)) {
      return notFound()
    }

    // Resolve the property and classes we need.
    def propDef
    try {
      propDef = DomainUtils.resolveProperty(target, prop)
    } catch (Throwable t) { propDef = null }
    
    if (!propDef) return notFound()
    
    Set vals = []
    switch ( propDef['type'] ) {

      default:
        vals = lookup ( propDef['type'], search ) as Set
    }

    respond vals
  }

  def blank(String domain, String prop) {

    // Try and locate the Domain class.
    PersistentEntity target = DomainUtils.findDomainClass (domain)

    if (!(target && prop)) {
      return notFound();
    }

    // Resolve the property and classes we need.
    def propDef
    try {
      propDef = DomainUtils.resolveProperty(target, prop)
    } catch (Throwable t) { propDef = null }
    if (!propDef) return notFound()

    // The object to return
    def obj
    
    // Class
    Class<?> theClass = propDef['type'];
    if (theClass?.metaClass.getMetaMethod("createBlank")) {
      // The class declares a createBlank method...
      obj = theClass.createBlank(propDef)
    } else {
      // Simply create an instance.
      obj = propDef['type']?.newInstance()
    }
    
    respond obj
  }

  protected def lookup (Class c, String term) {
    
    return simpleLookupService.lookup(c, term, Math.min(params.int('perPage') ?: 100, 100), params.int("page"), params.list("filters"), params.list("match"))
  }

  private notFound () {
    // Not found response.
    render (status : NOT_FOUND)
    return
  }
}


