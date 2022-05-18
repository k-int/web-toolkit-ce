package com.k_int.web.toolkit.custprops

import javax.persistence.MappedSuperclass
import javax.persistence.Transient

import com.k_int.web.toolkit.custprops.types.*

import grails.compiler.GrailsCompileStatic
import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity

abstract class CustomPropertyMulti<T> extends CustomProperty<Set<T>> {
}
