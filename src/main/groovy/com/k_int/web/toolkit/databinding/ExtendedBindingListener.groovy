package com.k_int.web.toolkit.databinding

import com.k_int.web.toolkit.utils.DomainUtils
import grails.databinding.events.DataBindingListener
import grails.databinding.events.DataBindingListenerAdapter
import grails.util.GrailsNameUtils
import grails.web.databinding.GrailsWebDataBinder
import groovy.transform.CompileStatic
import groovy.transform.Memoized
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
class ExtendedBindingListener extends DataBindingListenerAdapter {

  @Autowired
  GrailsWebDataBinder binder

//  @CompileStatic
//  @PostConstruct
//  void init () {
//    log.debug ("Instantiating and registering...")
//    binder.setDataBindingListeners ( [ this ] as DataBindingListener[] )
//  }

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
  
  @CompileStatic
//  @Memoized(maxCacheSize=20)
  protected boolean shouldBindCollectionImmutably (Class c, String propName) {
    def field = getField(c, propName)
    if (field) {
      
      // Grab the type of the field and check for an annotation at class level.
      def annotation = field.getAnnotation(BindImmutably)
      if (annotation) {
        return annotation.value()
      }
    }
    
    false
  }

  @CompileStatic
  public boolean supports(Class<?> clazz) {
    boolean supported = DomainClassArtefactHandler.isDomainClass(clazz)
    supported && log.debug ("supports ${clazz}")
    supported
  }
  
  public Boolean bindImmutably (Object obj, String propertyName, Object value, Object errors) {
    log.debug "Treating property ${propertyName} on ${obj} as immutable collection."
    
    // Grab the ids of the items to not remove. i.e. The items present in the new collection
    Set<Serializable> ids = value?.findResults { it?.id }

    // Collect ensures a copy is returned.
    obj[propertyName].collect().each {
      if (it.id && !ids.contains(it.id)) {
        log.debug "Item with id ${it.id} was not present in data for ${propertyName} on ${obj}, so we should remove it."
        obj."removeFrom${GrailsNameUtils.getClassName(propertyName)}" (it)
      }
    }
    
    true
  }
  
  public Boolean bindAdditively(Object obj, String propertyName, Object value, Object errors) {
    log.debug "Treating property ${propertyName} on ${obj} as additive collection."
    // Treat collection as an update. Look for { id:identifier, _delete:true }
    
    Set<Serializable> idsToDelete = value?.findResults { ( it?.hasProperty('_delete') && it.'_delete' == true ) ? it?.id : null }

    // Collect ensures a copy is returned.
    obj[propertyName].collect().each {
      if (it.id && idsToDelete.contains(it.id)) {
        log.debug "Item with id ${it.id} flagged for deletion from ${propertyName} on ${obj}, so we should remove it."
        obj."removeFrom${GrailsNameUtils.getClassName(propertyName)}" (it)
      }
    }
    
    true
  }

  public Boolean beforeBinding(Object obj, String propertyName, Object value, Object errors) {
        
    if (DomainUtils.isDomainPropertyCollection(obj.class, propertyName)) {
      log.debug "Is collection"
      if (shouldBindCollectionImmutably(obj.class, propertyName) ) {
        
        log.debug "Should bind immutably."
        return bindImmutably (obj, propertyName, value, errors)
      } else {
        log.debug "Should bind additively."
        return bindAdditively (obj, propertyName, value, errors)
      }
    }
    
    true
  }
}