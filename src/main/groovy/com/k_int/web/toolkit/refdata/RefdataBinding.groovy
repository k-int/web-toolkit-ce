package com.k_int.web.toolkit.refdata

import grails.util.GrailsNameUtils

class RefdataBinding {
  public static RefdataValue refDataBinding (obj, propName, source) {

    def data = source[propName]

    // If the data is asking for null binding then ensure we return here.
    if (data == null) {
      return null
    }

    // Create a map of id and value
    if (data instanceof String) {
      data = [
        'id': data,
        'value': data
      ]
    }

    // Default to the original
    RefdataValue val = obj[propName]
    if (data) {
      // Found by Id or lookup by value.
      val = RefdataValue.read(data['id']) ?: ((obj."lookup${GrailsNameUtils.getClassName(propName)}"(data['value'])) ?: val)
    }

    val
  }
}
