package com.k_int.web.toolkit.custprops;

import com.k_int.web.toolkit.custprops.types.CustomPropertyContainer
import grails.databinding.BindUsing

trait CustomProperties {
  @BindUsing({ obj, source -> CustomPropertiesBinder.bind (obj, source) })
  CustomPropertyContainer customProperties
  
  
  
}
