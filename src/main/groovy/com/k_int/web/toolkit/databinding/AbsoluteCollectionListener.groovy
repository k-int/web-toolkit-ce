package com.k_int.web.toolkit.databinding

import grails.databinding.events.DataBindingListener
import grails.databinding.events.DataBindingListenerAdapter
import grails.util.GrailsNameUtils
import grails.web.databinding.GrailsWebDataBinder
import groovy.transform.CompileStatic
import groovy.util.logging.Log4j

import java.lang.reflect.Field

import javax.annotation.PostConstruct
import javax.servlet.http.HttpServletRequest

import org.grails.core.artefact.DomainClassArtefactHandler
import org.grails.web.json.JSONArray
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

@Log4j
class AbsoluteCollectionListener extends DataBindingListenerAdapter {

  @Autowired
  GrailsWebDataBinder binder
  
  @CompileStatic
  @PostConstruct
  void init () {
    log.debug ("Instantiating and registering...")
    DataBindingListener[] listeners = new DataBindingListener[1]
    listeners[0] = this
    binder.setDataBindingListeners ( listeners )
  }

  @CompileStatic
  protected Field getField(Class clazz, String fieldName) {
    Field field = null
    try {
      field = clazz.getDeclaredField(fieldName)
    } catch (NoSuchFieldException nsfe) {
      def superClass = clazz.getSuperclass()
      if(superClass != Object) {
        field = getField(superClass, fieldName)
      }
    }
    return field
  }

  
  private final Map<String, Set<String>> annotatedFields = [:]
  
  @CompileStatic
  protected Set findAnnotatedFields ( Class clazz ) {
    Set<String> results = annotatedFields[clazz.name]
    
    if (results == null) {
      results = []
      Class c = clazz
      while (c != null) {
        for (Field field : c.getDeclaredFields()) {
          if (field.isAnnotationPresent(AbsoluteCollection)) {
            results << field.name
          }
        }
        c = c.getSuperclass()
      }
      annotatedFields[clazz.name] = results
    }
    results
  }

  @CompileStatic
  protected Set<String> actOn ( obj ) {
    Set<String> results = []
    if (hasSpecialHeader()) {
      results = findAnnotatedFields (obj.class)
    }
    
    results
  }
  
  @CompileStatic
  protected boolean hasSpecialHeader () {
    boolean headerPresent = false
    try {
      // Grab the request from the existing thread local in Spring.
      HttpServletRequest request = ((ServletRequestAttributes)RequestContextHolder.currentRequestAttributes()).request
  
      // Check the value of a specific header.
      headerPresent = request.getHeader("Binding-Source")?.toLowerCase() == 'ng-app'
      log.debug ("hasSpecialHeader ${headerPresent}")
    } catch ( IllegalStateException e ) {
      // This simply means no request was found bound to the thread. Some system processes
      // will validly not create a request. We can safely ignore this in the context of our listener,
      // and return false.
      headerPresent = false
    }
    headerPresent
  }

  @CompileStatic
  public boolean supports(Class<?> clazz) {
    boolean supported = DomainClassArtefactHandler.isDomainClass(clazz)
    log.debug ("supports ${clazz} == ${supported}")
    supported
  }
  
  public Boolean beforeBinding(Object obj, String propertyName, Object value, Object errors) {
    
    if (obj[propertyName] instanceof Collection && actOn (obj).contains(propertyName)) {
      log.debug "Treating property ${propertyName} on ${obj} as absolute collection."

      // Grab the ids of the items to not remove.
      Set<Long> ids = []
      
      value?.each {
        if (it?.id) {
          String val = "${it.id}"
          if (val.isLong()) {
            ids << val.toLong()
          }
        }
      }
            
      // Remove each item not in the supplied vals.
      boolean needsSave = false
      obj[propertyName].collect().each {
        if (it.id && !ids.contains(it.id)) {
          log.debug "Item with id ${it.id} was not present in data for ${propertyName} on ${obj}, so we should remove it."
          obj."removeFrom${GrailsNameUtils.getClassName(propertyName)}" (it)
          needsSave = true
        }
      }
      if (needsSave) {
        obj.save(failOnError:true, flush:true)
      }
    }
    // Return true to state that we still want binding to continue no matter what happens above.
    true
  }
}
