package com.k_int.web.toolkit.rest;

import static org.springframework.http.HttpStatus.*

import org.grails.datastore.gorm.query.NamedCriteriaProxy

import com.k_int.web.toolkit.SimpleLookupService

import grails.gorm.transactions.Transactional
import grails.web.http.HttpHeaders
import grails.core.GrailsApplication

public class RestfulController<T> extends grails.rest.RestfulController<T> {

  static responseFormats = ['json', 'xml']
  SimpleLookupService simpleLookupService
  GrailsApplication grailsApplication

  public RestfulController (Class<T> resource) {
    super(resource)
  }
  
  public RestfulController (Class<T> resource, boolean readOnly) {
    super(resource, readOnly)
  }
  
  protected def doTheLookup (@SuppressWarnings("deprecation") NamedCriteriaProxy namedQuery = null, Class res = this.resource) {
    doTheLookup ( res , namedQuery?.criteriaClosure as Closure)
  }
  
  protected List getParamList(final String name) {
    params.list("${name}[]") ?: params.list("${name}")
  } 
  
  protected def doTheLookup (Class<T> res = this.resource, Closure baseQuery) {
    final int offset = params.int("offset") ?: 0
    final int perPage = Math.min(params.int('perPage') ?: params.int('max') ?: 10, 100)
    final int page = params.int("page") ?: (offset ? (offset / perPage) + 1 : 1)
    final List<String> filters = getParamList("filters")
    final List<String> match_in = getParamList("match")
    final List<String> sorts = getParamList("sort")
    
    if (params.boolean('stats')) {
      return simpleLookupService.lookupWithStats(res, params.term, perPage, page, filters, match_in, sorts, null, baseQuery)
    } else {
      return simpleLookupService.lookup(res, params.term, perPage, page, filters, match_in, sorts, baseQuery)
    }
  }
  
  protected Iterator doChunkedStreamingLookup (Class<T> res = this.resource, final int maxChunkSize, Closure baseQuery) {
    final int offset = params.int("offset") ?: 0
    final int page = params.int("page") ?: (offset ? (offset / perPage) + 1 : 1)
    final List<String> filters = getParamList("filters")
    final List<String> match_in = getParamList("match")
    final List<String> sorts = getParamList("sort")
    
    simpleLookupService.lookupAsBatchedStream(res, params.term, maxChunkSize, filters, match_in, sorts, baseQuery)
  }
  
  @Transactional
  def index(Integer max) {
    respond doTheLookup()
  }
  
  @Override
  @Transactional
  def update() {
    if(handleReadOnly()) {
        return
    }

    T instance = queryForResource(params.id)
    if (instance == null) {
        transactionStatus.setRollbackOnly()
        notFound()
        return
    }

    Object object_to_bind = getObjectToBind();

    if ( grailsApplication.config.getProperty('enforceVersionCheck',Boolean.class,false) == true ) {
      if (object_to_bind?.version != null) {
        if (instance.version > object_to_bind.version) {
          log.debug("RestfulController.update() :: DB Version: ${instance?.version} request version: ${getObjectToBind()?.version} - Reject");
          instance.errors.rejectValue("version", "default.optimistic.locking.failure", "Another user has updated this record while you were editing")
          return
        }
      }
    }

    bindData(instance, object_to_bind);

    instance.validate()
    if (instance.hasErrors()) {
        transactionStatus.setRollbackOnly()
        respond instance.errors, view:'edit' // STATUS CODE 422
        return
    }
    updateResource instance
    request.withFormat {
        form multipartForm {
            flash.message = message(code: 'default.updated.message', args: [classMessageArg, instance.id])
            redirect instance
        }
        '*'{
            response.addHeader(HttpHeaders.LOCATION,
                    grailsLinkGenerator.link( resource: this.controllerName, action: 'show',id: instance.id, absolute: true,
                                        namespace: hasProperty('namespace') ? this.namespace : null ))
            respond instance, [status: OK]
        }
    }
}
}
