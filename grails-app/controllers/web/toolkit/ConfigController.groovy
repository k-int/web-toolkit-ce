package web.toolkit

import static org.springframework.http.HttpStatus.*
import grails.converters.JSON
import grails.core.GrailsApplication
import grails.core.GrailsDomainClass
import grails.core.GrailsDomainClassProperty
import grails.rest.Resource
import grails.util.GrailsClassUtils

import java.util.concurrent.ConcurrentHashMap

import org.grails.core.DefaultGrailsControllerClass
import org.grails.core.artefact.ControllerArtefactHandler

import com.k_int.grails.tools.formly.FormlyConfigBuilder
import com.k_int.grails.tools.refdata.RefdataValue
import com.k_int.grails.tools.utils.DomainUtils

class ConfigController {
  
  // Priority ordered list of available HTTP methods.
  private static final List<String> httpMethods = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE']
  
  static responseFormats = ['json', 'xml']

  GrailsApplication grailsApplication
  def grailsUrlMappingsHolder
  
  ConcurrentHashMap cache = [:]
  
  def resourceConfig () {
    
    // Check the cache.
    ConcurrentHashMap conf = cache["resourceConfig"] ?: new ConcurrentHashMap();
        
    // Need to generate.
    if (!conf) {
      grailsApplication.domainClasses.each { GrailsDomainClass dc ->
        
        // Grab the associated class with this domain object.
        final clazz = dc.clazz
        
        // Check for the resource annotation.
        Resource rann = clazz.getAnnotation(Resource)
        if (rann) {
        
          // The config object for this domain class.
          def domainConf = [:].withDefault { [:] }
          
          // Common properties for the link generation.
          def prop = [
            id : ":id"
          ]
          
          // The name of the controller.
          String name = "${dc.logicalPropertyName}"
          
          // Get the controller.
          DefaultGrailsControllerClass ctrl = grailsApplication.getArtefactByLogicalPropertyName(ControllerArtefactHandler.TYPE, name)
          
          // Base uri
          String base = rann.uri()
          if (!base) {
            // Attempt to generate.
            base = grailsLinkGenerator.link(prop + [resource: name, action: ctrl.defaultActionName, absolute: true] )?.replaceAll("\\?(.*)\$", "")?.replaceAll("%3A", ":")
          } else {
            base = grailsLinkGenerator.link(absolute: true, uri: (base + "/:id" ))
          }
          
          if (base) {
            
            // Get any declare "allowedMethods" on the controller.
            def allowedMethods = (ctrl.hasProperty("${ctrl.ALLOWED_HTTP_METHODS_PROPERTY}") ? ctrl.metaClass.getProperty(ctrl, "${ctrl.ALLOWED_HTTP_METHODS_PROPERTY}") : [:]) ?: [:]
            
            // Map the actions.
            def exclude_list = ["edit"]
            ctrl.getActions().each { String action ->
              
              if (!exclude_list.contains(action)) {
                
                // Grab the actual URI.
                String uri = grailsLinkGenerator.link(prop + [resource: name, action: action, absolute: true] ).replaceAll("\\?(.*)\$", "").replaceAll("%3A", ":")
                
                if (uri) {
                  
                  // First check if the allowedMethod prop contained entries, and if not get from URL def.
                  def methods = [] + allowedMethods[action] ?: grailsUrlMappingsHolder.allowedMethods(uri.replaceAll("\\:id", "1"))?.collect { it.name } ?: []
                  
                  // Get the allowed HTTP methods for the above URI
                  def method = methods.intersect(httpMethods)?.getAt(0) ?: httpMethods[0]
                  
                  // Rename the action after we have used the internal name to determine method etc..
                  if (action == ctrl.defaultActionName) {
                    // Change to "list" to keep in line with ngResource.
                    action = "list"
                  }
                  
                  // Let's construct a config entry.
                  domainConf."actions"."${action}" = [
                    "url"     : "${uri}",
                    "method"  : "${method}"
                  ]
                }
              }
            }
            
            // If we have config at this point then we should generate the remainder.
            if (domainConf) {
              domainConf['url'] = base
              
              // Flag all refdata properties
              def rdp = []
              for (GrailsDomainClassProperty p : dc.getPersistentProperties()) {
                Class<?> type = p.getReferencedPropertyType()
                if (GrailsClassUtils.isAssignableOrConvertibleFrom(RefdataValue.class, type)) {
                  rdp << p.name
                }
              }
              
              // Add the refdata config.
              if (rdp) {
                domainConf['refdata'] = rdp
              }
              
              // Add the config to the global map.
              conf [dc.getName()] = ['resourceConfig' : domainConf]
            }
          }
        }
      }
      cache["resourceConf"] = conf
    }
    
    respond conf
  }

  private notFound () {
    // Not found response.
    render (status : NOT_FOUND)
    return
  }
}
