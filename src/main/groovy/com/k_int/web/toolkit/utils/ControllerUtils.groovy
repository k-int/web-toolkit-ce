package com.k_int.web.toolkit.utils

import java.util.List
import org.codehaus.groovy.runtime.memoize.Memoize
import org.grails.web.mapping.DefaultUrlMappingInfo
import org.springframework.http.HttpMethod

import grails.core.GrailsControllerClass
import grails.rest.RestfulController
import grails.web.mapping.UrlCreator
import grails.web.mapping.UrlMappingInfo
import grails.web.mapping.UrlMappingsHolder
import groovy.transform.Memoized

public class ControllerUtils {
  
  // Priority ordered list of available HTTP methods.
  private static final List<HttpMethod> httpMethods = [HttpMethod.POST, HttpMethod.GET, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE]

  @Memoized(maxCacheSize=200)
  public static Map<String,Object> actionMap (GrailsControllerClass gcc, UrlMappingsHolder grailsUrlMappingsHolder, String actionName) {
    
    int id = (Math.random() * ( 500 )) as int 
    
    Map<String, Object> mapping = [
      methods : [] as List
    ]
    
    UrlCreator uriC = grailsUrlMappingsHolder.getReverseMapping(gcc.logicalPropertyName, actionName, null)
    String uri = uriC ? uriC.createRelativeURL(gcc.logicalPropertyName, actionName, null, null) : null
    
    def methods = uri ? grailsUrlMappingsHolder.allowedMethods("${uri}/${id}") : null
    if (methods) {
      mapping['uri'] = uri
      boolean restful = false
      httpMethods.each { HttpMethod method ->
        if (methods.contains(method)) {
          grailsUrlMappingsHolder.matchAll(uri, method).each {UrlMappingInfo inf ->
            if (gcc.logicalPropertyName == inf.controllerName && inf.actionName == actionName) {
              mapping["methods"] << "${method}"
            }
          }
        }
      }
      
      return mapping
    }
    
    null    
  }
  
  @Memoized(maxCacheSize=200)
  public static Map<String,Object> getAllActionDetails (GrailsControllerClass gcc, UrlMappingsHolder grailsUrlMappingsHolder, Set exclude_list = []) {
    
    Map<String,Object> details = [
      mapping : [:].withDefault { [] }
    ]
        
    // Grab the actual URI.
    String uri = getControllerDefaultUri(gcc, grailsUrlMappingsHolder)

    if (uri) {
      
      // Firstly add the default action if not in exclude list.
      if (!exclude_list.contains(gcc.defaultAction)) {
        
        def am = actionMap(gcc, grailsUrlMappingsHolder, gcc.defaultAction)
        if (am) {
          am['methods']?.each {
            details['mapping'][gcc.defaultAction] << [method: it, uri: am['uri']]
          }
        }
      }
    }
    
    gcc.getActions().each { String action ->
      
      // We need to add the URI to call this action along with the method.
      if (!exclude_list.contains(action)) {
        
        def am = actionMap(gcc, grailsUrlMappingsHolder, action)
        if (am) {
          am['methods']?.each {
            details['mapping'][action] << [method: it, uri: am['uri']]
          }
        }
      }
    }
    
    details
  }
  
  @Memoized(maxCacheSize=200)
  public static String getControllerDefaultUri (GrailsControllerClass gcc, UrlMappingsHolder grailsUrlMappingsHolder) {
    UrlCreator entryPoint
    for (int i=0; entryPoint == null && i<(httpMethods.size() - 1); i++) {
      entryPoint = grailsUrlMappingsHolder.getReverseMapping(gcc.logicalPropertyName, null, null, null, httpMethods[i].toString(), null)
    }
    
    entryPoint?.createRelativeURL(gcc.logicalPropertyName, null, null, null)
  }
  
  @Memoized(maxCacheSize=200)
  public static Map<String,Object> getRestfulDetails (GrailsControllerClass gcc, UrlMappingsHolder grailsUrlMappingsHolder, String baseUri = null, boolean readOnly)  {

    Map<String,Object> details = [
      mapping : [:].withDefault { [] }
    ]
    String ctrlName = "${gcc.logicalPropertyName}"
    
    if (!baseUri) {
      UrlCreator entryPoint = grailsUrlMappingsHolder.getReverseMapping(ctrlName, null, null, null, HttpMethod.GET.toString(), null)
      baseUri = entryPoint?.createRelativeURL(ctrlName, null, null, null)
    }

    if (baseUri) {
      // Attempt to infer whether this controller responds to restful URIs.
      // To do that we first reverse lookup the default action for the controller.
      // The returned URI should match the URI that responds with a list when a HTTP GET is used.

      // Check for default action mapping for this URI.
      boolean notDefault = grailsUrlMappingsHolder.matchAll(baseUri, HttpMethod.GET)?.findAll { UrlMappingInfo inf ->
        // Only care about none-default URL Mappings.
        if (!DefaultUrlMappingInfo.class.isAssignableFrom(inf.class)) {
          
          // Grab the action name for exclusion when we add custom "actions"
          if (inf.actionName && ctrlName == inf.controllerName) {
            details['mapping'][inf.actionName] << [method: "${HttpMethod.GET}", uri: "${baseUri}", type:'index']
          }
          return true
        }

        false
      }
      
      if (notDefault) {
        
        //Add base URI for ease of access.
        details['baseUri'] = baseUri

        // So now we know that the controller responds to a GET request to its default action we
        // should look for get and post with an identifier next.
        notDefault = readOnly || grailsUrlMappingsHolder.matchAll(baseUri, HttpMethod.POST)?.findAll { UrlMappingInfo inf ->
          // Only care about none-default URL Mappings.
          if (!DefaultUrlMappingInfo.class.isAssignableFrom(inf.class)) {
  
            // Grab the action name for exclusion when we add custom "actions"
            if (inf.actionName && ctrlName == inf.controllerName) {
              details['mapping'][inf.actionName] << [method: "${HttpMethod.POST}", uri: "${baseUri}", type:'save']
            }
            return true
          }
  
          false
        }
        if (notDefault) {
          
          // Check GET request to /baseUri/ID
          notDefault = grailsUrlMappingsHolder.matchAll("${baseUri}/1", HttpMethod.GET)?.findAll { UrlMappingInfo inf ->
            // Only care about none-default URL Mappings.
            if (!DefaultUrlMappingInfo.class.isAssignableFrom(inf.class)) {
  
              // Grab the action name for exclusion when we add custom "actions"
              if (inf.actionName && ctrlName == inf.controllerName) {
                details['mapping'][inf.actionName] << [method: "${HttpMethod.GET}", uri: "${baseUri}/{id}", type:'show']
              }
              return true
            }
  
            false
          }
          
          if (notDefault) {
  
            // Check PUT request to /baseUri/ID
            notDefault = readOnly || grailsUrlMappingsHolder.matchAll("${baseUri}/1", HttpMethod.PUT)?.findAll { UrlMappingInfo inf ->
              // Only care about none-default URL Mappings.
              if (!DefaultUrlMappingInfo.class.isAssignableFrom(inf.class)) {
    
                // Grab the action name for exclusion when we add custom "actions"
                if (inf.actionName && ctrlName == inf.controllerName) {
                  details['mapping'][inf.actionName] << [method: "${HttpMethod.PUT}", uri: "${baseUri}/{id}", type:'update']
                }
                return true
              }
    
              false
            }
            if (notDefault) {
              // Check DELETE
              notDefault = readOnly || grailsUrlMappingsHolder.matchAll("${baseUri}/1", HttpMethod.DELETE)?.findAll { UrlMappingInfo inf ->
                // Only care about none-default URL Mappings.
                if (!DefaultUrlMappingInfo.class.isAssignableFrom(inf.class)) {
    
                  // Grab the action name for exclusion when we add custom "actions"
                  if (inf.actionName && ctrlName == inf.controllerName) {
                    details['mapping'][inf.actionName] << [method: "${HttpMethod.DELETE}", uri: "${baseUri}/{id}", type:'delete']
                  }
                  return true
                }
    
                false
              }
              
              if (notDefault) {
                
                // PATCH isn't a deal breaker for us. We include if present.
                if (!readOnly) {
                  grailsUrlMappingsHolder.matchAll("${baseUri}/1", HttpMethod.PATCH)?.each { UrlMappingInfo inf ->
                    // Only care about none-default URL Mappings.
                    if (!DefaultUrlMappingInfo.class.isAssignableFrom(inf.class)) {
        
                      // Grab the action name for exclusion when we add custom "actions"
                      if (inf.actionName && ctrlName == inf.controllerName) {
                        details['mapping'][inf.actionName] << [method: "${HttpMethod.PATCH}", uri: "${baseUri}/{id}", type:'patch']
                      }
                      return true
                    }
        
                    false
                  }
                }
                
                return details
              }
            }
          }
        }
      }
    }

    // If the resource fails any of the required restful mappings above then we drop out.
    null
  }
}
