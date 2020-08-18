package com.k_int.web.toolkit.utils

import org.grails.datastore.gorm.GormEnhancer
import org.grails.datastore.gorm.GormStaticApi
import org.grails.datastore.mapping.model.PersistentEntity
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.DefaultTransactionDefinition
import org.springframework.util.Assert

import grails.gorm.transactions.GrailsTransactionTemplate
import grails.util.Holders
import groovy.transform.CompileStatic

@CompileStatic
class GormUtils {
  
  private static PlatformTransactionManager transactionManager
  private static PlatformTransactionManager getTransactionManager() {
    if (!transactionManager) {
      transactionManager = Holders.applicationContext.getBean(PlatformTransactionManager)
    }
    
    transactionManager
  }
  
  public static <T> T withTransaction (Closure<T> callable) {
    Assert.notNull getTransactionManager(), "No transactionManager bean configured"
    if (!callable) {
        return
    }
    
    new GrailsTransactionTemplate(transactionManager, new DefaultTransactionDefinition()).execute(callable)
  }
  
  public static <D>PersistentEntity currentGormEntity( Class<D> entityClass ) {
    currentGormStaticApi(entityClass).persistentEntity
  }
  
  public static <D> GormStaticApi<D> currentGormStaticApi( Class<D> entityClass ) {
    (GormStaticApi<D>)GormEnhancer.findStaticApi( entityClass )
  }
}
