package com.k_int.web.toolkit.custprops;

import com.k_int.web.toolkit.custprops.types.CustomPropertyContainer

import groovy.transform.CompileStatic

@CompileStatic
trait CustomProperties {
  CustomPropertyContainer customProperties = new CustomPropertyContainer()
}
