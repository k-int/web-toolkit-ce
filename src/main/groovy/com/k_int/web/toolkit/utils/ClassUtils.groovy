package com.k_int.web.toolkit.utils

import java.lang.reflect.Field

import groovy.transform.CompileStatic

@CompileStatic
public class ClassUtils {
  public static Field getField(Class clazz, String fieldName) {
    Field field = null
    try {
      field = clazz.getDeclaredField(fieldName)
    } catch (NoSuchFieldException nsfe) {
      // Try and find a matching field within an implemented trait.
      def fields = clazz.declaredFields
      for (int i=(fields.length - 1); !field && i>=0; i--) {
        Field f = fields[i]
        println "${f}"
        // The below is nasty! There must be a better way to get the remapped name. 
        if (f.name.endsWith("__${fieldName}")) {
          field = f
        }
      }
      
      // Still no field found. We should consult the superclass.
      if (!field) {
        final Class superClass = clazz.getSuperclass()
        if(superClass != Object) {
          field = getField(superClass, fieldName)
        }
      }
    }
    return field
  }
}
