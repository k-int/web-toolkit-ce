package com.k_int.web.toolkit.rest;

import static org.springframework.http.HttpStatus.*

import org.grails.datastore.gorm.query.NamedCriteriaProxy

import com.k_int.web.toolkit.SimpleLookupService

import grails.gorm.transactions.Transactional
import grails.web.http.HttpHeaders

@SuppressWarnings("deprecation")
public class RestfulController<T> extends grails.rest.RestfulController<T> {

  static responseFormats = ['json', 'xml']
  SimpleLookupService simpleLookupService

  public RestfulController (Class<T> resource) {
    super(resource)
  }
  
  public RestfulController (Class<T> resource, boolean readOnly) {
    super(resource, readOnly)
  }
    
  protected def doTheLookup (NamedCriteriaProxy namedQuery = null, Class res = this.resource) {
    doTheLookup ( res , namedQuery?.criteriaClosure as Closure)
  }
  
  protected def doTheLookup (Class res = this.resource, Closure baseQuery) {
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

    // This is the properties assigning that does not work for entities not in the
    // domain folder. Replace it with a bindData instead.
//    instance.properties = getObjectToBind()
    bindData(instance, getObjectToBind())

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
