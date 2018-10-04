package com.k_int.web.toolkit.databinding

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

@Target( ElementType.TYPE ) // Class only
@Retention(RetentionPolicy.RUNTIME)
@interface BindUsingWhenRef {
  Class<?> value()
}
