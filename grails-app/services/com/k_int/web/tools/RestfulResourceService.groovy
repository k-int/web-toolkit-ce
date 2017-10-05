package com.k_int.web.tools

import java.util.concurrent.ConcurrentHashMap

import org.grails.core.artefact.ControllerArtefactHandler
import org.springframework.beans.factory.annotation.Autowired

import com.k_int.web.toolkit.utils.ControllerUtils
import com.k_int.web.toolkit.utils.DomainUtils

import grails.core.GrailsApplication
import grails.core.GrailsControllerClass
import grails.core.GrailsDomainClass
import grails.rest.Resource
import grails.web.mapping.LinkGenerator
import grails.web.mapping.UrlMappingsHolder

class RestfulResourceService {
  
  private Map<String, Boolean> knownResources = new ConcurrentHashMap<String, Boolean>()
  
  @Autowired(required=true)
  GrailsApplication grailsApplication
  
  @Autowired(required=true)
  UrlMappingsHolder grailsUrlMappingsHolder
  
  @Autowired(required=true)
  LinkGenerator grailsLinkGenerator
  
  public Boolean isResource ( final def domainTarget ) {
    def dc = DomainUtils.resolveDomainClass (domainTarget)
    if (dc) {
      Boolean res = knownResources(dc.shortName)
      if (res == null) {
        // Run the info class to populate.
        getResourceInfo()
        res = knownResources(dc.shortName)
      }
      
      return res
    }
    
    // Not a resource.
    null 
  } 
  
  public Map getResourceInfo (boolean extendedInfo = false) {
    def conf = [:]
    Set<String> processedControllers = []
    grailsApplication.domainClasses.each { GrailsDomainClass dc ->
      
      // The name of the controller derived from the DomainClass.
      String name = "${dc.logicalPropertyName}"
      
      // Test the name
      if (!processedControllers.contains(name)) {
        // Get the controller.
        GrailsControllerClass ctrl = grailsApplication.getArtefactByLogicalPropertyName(ControllerArtefactHandler.TYPE, name)
        
        if (!ctrl) {
          ctrl = DomainUtils.findControllersForDomain(dc)
        }
        
        if (ctrl) {
  
          // Grab the associated class with this domain object.
          final clazz = dc.clazz
    
          // Check for the resource annotation.
          Resource rann = clazz.getAnnotation(Resource)
          
          // If the resource is present then we can grab the uri here unless the extra info is required.
          def details
          if (!extendedInfo && rann) {
            // URI ?
            def uri = rann.uri() ?: ControllerUtils.getControllerDefaultUri(ctrl, grailsUrlMappingsHolder) ?: grailsLinkGenerator.link(controller: ctrl.logicalPropertyName)?.toLowerCase()?.replaceAll("\\/${ctrl.defaultAction.toLowerCase()}(\\/?)\$", '')
            if (uri) {
              details = [
                'baseUri': uri,
                'identifierProp':  dc.identifier.name
              ]
            }
          } else {
            
            // Either no annotation, or we need more info.
            // Grab the restful controller details if this is a restful type controller.
            details = ControllerUtils.getRestfulDetails(ctrl, grailsUrlMappingsHolder, rann?.uri() ?: grailsLinkGenerator.link(controller: ctrl.logicalPropertyName)?.toLowerCase()?.replaceAll("\\/${ctrl.defaultAction.toLowerCase()}(\\/?)\$", ''))
            
            if (details && !extendedInfo) {
              // Discard the extras.
              details = [
                baseUri: details['baseUri'],
                'identifierProp':  dc.identifier.name
              ]
            }
          }
          
          if (details) {
            if(extendedInfo) {
              // Grab any other action details that are not part of the default restful API.
              def otherActions = ControllerUtils.getAllActionDetails(ctrl, grailsUrlMappingsHolder, (details?."mapping"?.keySet() ?: []))
              
              if (otherActions) {
                // Merge the mappings.
                details['mapping'].putAll(otherActions['mapping'])
              }
            }
            
            // We can now add our details to the mapping.
            conf[dc.shortName] = details
          }
          
          processedControllers << name
        }
      }
      
      if (conf.containsKey(dc.shortName)) {
        knownResources[dc.shortName] = true
      } else {
        knownResources[dc.shortName] = false
      }
    }
    
    conf
  }
}