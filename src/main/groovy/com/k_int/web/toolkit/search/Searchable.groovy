package com.k_int.web.toolkit.search


import java.lang.annotation.Documented
import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

@Documented
@Target([ElementType.FIELD])
@Retention(RetentionPolicy.RUNTIME)
@interface Searchable {
  boolean value() default true
  boolean filter() default true
  boolean sort() default true
}