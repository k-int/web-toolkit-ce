package com.k_int.web.toolkit.custprops

import javax.persistence.Entity

import org.grails.orm.hibernate.cfg.Settings

import com.k_int.web.toolkit.custprops.types.CustomPropertyContainer
import com.k_int.web.toolkit.custprops.types.CustomPropertyInteger
import com.k_int.web.toolkit.custprops.types.CustomPropertyMultiInteger

import grails.databinding.SimpleMapDataBindingSource
import grails.gorm.MultiTenant
import grails.gorm.multitenancy.CurrentTenant
import grails.gorm.transactions.Transactional
import grails.test.hibernate.HibernateSpec
import grails.testing.mixin.integration.Integration
import grails.web.databinding.GrailsWebDataBinder
import groovy.util.logging.Slf4j
import spock.lang.Specification
import spock.lang.Stepwise

@Slf4j
@Integration
@Stepwise
@CurrentTenant
class CustomPropertiesSpec extends Specification {
  
  @Autowired
  GrailsWebDataBinder grailsWebDataBinder
  
  private void addDefinitions(final List<Map<String,?>> propertyDefinitions) {
    for (Map definition : propertyDefinitions) {
      final String type = definition.remove('type')
      final boolean multi = definition.remove('multi')
      CustomPropertyDefinition cpd = CustomPropertyDefinition.forType(type, definition)
      cpd.save(flush: true, failOnError:true)
    }
  }
  
  @Transactional
  def 'Create Integer value' () {
    
    when:"Create integer property definition"
      addDefinitions([[
        "name" : "testInteger",
        "label" : "Test Integer",
        "type": "Integer",
        "description" : "An integer value custom property",
        "primary": true
      ]])
      
      
    and: 'Query by name'
      final CustomPropertyDefinition cpd = CustomPropertyDefinition.findByName('testInteger')
      
    then: 'Definition recalled'
      cpd?.id != null
      cpd?.type == CustomPropertyInteger
  }
  
  @Transactional
  def 'Create Multi Integer value' () {
    
    when:"Create integer property definition"
      addDefinitions([[
        "name" : "testMultiInteger",
        "label" : "Test Multi-Valued Integer",
        "type": "MultiInteger",
        "description" : "An integer value custom property",
        "primary": true
      ]])
      
      
    and: 'Query by name'
      final CustomPropertyDefinition cpd = CustomPropertyDefinition.findByName('testMultiInteger')
      
    then: 'Definition recalled'
      cpd?.id != null
      cpd?.type == CustomPropertyMultiInteger
  }
  
  @Transactional(readOnly=true)
  def 'Bind custom property data' () {
    when: 'Binder called with data'
      final TestEntity te = new TestEntity()
      grailsWebDataBinder.bind te, new SimpleMapDataBindingSource ([
        custProps: [
          'testInteger': [value: 10],
          'testMultiInteger': [value: [20,30]]
        ]
      ])
      
    then: 'Property bound'
      (te.custProps.value?.size() ?: 0) == 2
      
    and: 'Values as expected'
      final CustomProperty single = te.custProps?.value?.find { it.definition.name == 'testInteger'}
      final CustomProperty multi = te.custProps?.value?.find { it.definition.name == 'testMultiInteger'}
      final Collection multiVals = multi?.value
      
      single?.value == 10
      multiVals?.contains(20 as BigInteger)
      multiVals?.contains(30 as BigInteger)
  }
}

@Entity
public class TestEntity implements MultiTenant<TestEntity>{
  CustomPropertyContainer custProps = new CustomPropertyContainer()
}

