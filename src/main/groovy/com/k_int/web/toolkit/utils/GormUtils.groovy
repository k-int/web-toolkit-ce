package com.k_int.web.toolkit.utils

import org.grails.datastore.gorm.GormEnhancer
import org.grails.datastore.gorm.GormInstanceApi
import org.grails.datastore.gorm.GormStaticApi
import org.grails.datastore.mapping.model.PersistentEntity
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.DefaultTransactionDefinition
import org.springframework.util.Assert

import grails.gorm.transactions.GrailsTransactionTemplate
import grails.util.Holders
import groovy.transform.CompileStatic

@CompileStatic
class GormUtils {
  
  private static PlatformTransactionManager transactionManager
  public static <D>PersistentEntity currentGormEntity( Class<D> entityClass ) {
    gormStaticApi(entityClass).persistentEntity
  }
  
  private static PlatformTransactionManager getTransactionManager() {
    if (!transactionManager) {
      transactionManager = Holders.applicationContext.getBean(PlatformTransactionManager)
    }
    
    transactionManager
  }
  
  public static <D> GormInstanceApi<D> gormInstanceApi( Class<D> entityClass ) {
    GormEnhancer.findInstanceApi(entityClass)
  }
  
  public static <D> GormStaticApi<D> gormStaticApi( Class<D> entityClass ) {
    GormEnhancer.findStaticApi( entityClass )
  }
  
  public static <T> T withNewReadOnlyTransaction (Closure<T> callable) {
    final TransactionDefinition txd = new DefaultTransactionDefinition()
    txd.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW)
    txd.setReadOnly(true)
    
    withTransaction(txd, callable)
  }
  
  public static <T> T withNewTransaction (Closure<T> callable) {
    final TransactionDefinition txd = new DefaultTransactionDefinition()
    txd.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW)
    
    withTransaction(txd, callable)
  }
  
  public static <T> T withTransaction (Closure<T> callable) {    
    withTransaction( new DefaultTransactionDefinition(), callable)
  }
  
  public static <T> T withTransaction (TransactionDefinition txd, Closure<T> callable) {
    Assert.notNull getTransactionManager(), "No transactionManager bean configured"
    if (!callable) {
        return
    }
    
    new GrailsTransactionTemplate(transactionManager, txd).execute(callable)
  }
}
