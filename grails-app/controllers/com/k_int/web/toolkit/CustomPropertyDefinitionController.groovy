package com.k_int.web.toolkit

import com.k_int.web.toolkit.custprops.CustomPropertyDefinition
import com.k_int.web.toolkit.rest.RestfulController

class CustomPropertyDefinitionController extends RestfulController<CustomPropertyDefinition> {
  CustomPropertyDefinitionController() {
    super(CustomPropertyDefinition)
  }
}
