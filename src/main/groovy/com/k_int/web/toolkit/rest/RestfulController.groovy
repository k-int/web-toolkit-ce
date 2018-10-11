package com.k_int.web.toolkit.rest;

import org.grails.datastore.gorm.query.NamedCriteriaProxy;

import com.k_int.web.toolkit.SimpleLookupService

import groovy.lang.Closure;

public class RestfulController<T> extends grails.rest.RestfulController<T> {

  static responseFormats = ['json', 'xml']
  SimpleLookupService simpleLookupService

  public RestfulController (Class<T> resource) {
    super(resource)
  }
  
  public RestfulController (Class<T> resource, boolean readOnly) {
    super(resource, readOnly)
  }
    
  protected def doTheLookup (Class res = this.resource, NamedCriteriaProxy namedQuery = null) {
    doTheLookup ( res , namedQuery?.criteriaClosure as Closure)
  }
  
  protected def doTheLookup (Class res, Closure baseQuery) {
    final int offset = params.int("offset") ?: 0
    final int perPage = Math.min(params.int('perPage') ?: params.int('max') ?: 10, 100)
    final int page = params.int("page") ?: (offset ? (offset / perPage) + 1 : 1)
    final List<String> filters = params.list("filters[]") ?: params.list("filters")
    final List<String> match_in = params.list("match[]") ?: params.list("match")
    final List<String> sorts = params.list("sort[]") ?: params.list("sort")
    
    if (params.boolean('stats')) {
      return simpleLookupService.lookupWithStats(res, params.term, perPage, page, filters, match_in, sorts, null, baseQuery)
    } else {
      return simpleLookupService.lookup(res, params.term, perPage, page, filters, match_in, sorts, baseQuery)
    }
  }
  
  def index(Integer max) {
    respond doTheLookup()
  }
}
