package com.k_int.web.toolkit

import com.k_int.web.toolkit.databinding.ExtendedWebDataBinder

import grails.util.Holders
import grails.web.databinding.DataBindingUtils

class ValueConverterService {
  public static <T> T convert ( Class<T> targetType, value ) {
    ExtendedWebDataBinder grailsWebDataBinder = Holders.getApplicationContext().getBean(DataBindingUtils.DATA_BINDER_BEAN_NAME)
    grailsWebDataBinder.attemptConversion (targetType, value)
  }
  
  ExtendedWebDataBinder grailsWebDataBinder
  
  public <T> T attemptConversion (Class<T> targetType, value) {
    T val = grailsWebDataBinder.attemptConversion (targetType, value)
    val
  }
}
