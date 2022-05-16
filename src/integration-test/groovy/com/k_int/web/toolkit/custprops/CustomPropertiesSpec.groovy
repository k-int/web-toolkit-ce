package com.k_int.web.toolkit.custprops

import javax.persistence.Entity

import org.grails.orm.hibernate.cfg.Settings

import com.k_int.web.toolkit.refdata.RefdataCategory
import com.k_int.web.toolkit.refdata.RefdataValue

import com.k_int.web.toolkit.custprops.types.CustomPropertyContainer

import com.k_int.web.toolkit.custprops.types.CustomPropertyInteger
import com.k_int.web.toolkit.custprops.types.CustomPropertyMultiInteger
import com.k_int.web.toolkit.custprops.types.CustomPropertyBlob
import com.k_int.web.toolkit.custprops.types.CustomPropertyMultiBlob
import com.k_int.web.toolkit.custprops.types.CustomPropertyBoolean
import com.k_int.web.toolkit.custprops.types.CustomPropertyDecimal
import com.k_int.web.toolkit.custprops.types.CustomPropertyMultiDecimal
import com.k_int.web.toolkit.custprops.types.CustomPropertyLocalDate
import com.k_int.web.toolkit.custprops.types.CustomPropertyMultiLocalDate

import com.k_int.web.toolkit.custprops.types.CustomPropertyRefdataDefinition
import com.k_int.web.toolkit.custprops.types.CustomPropertyRefdata
import com.k_int.web.toolkit.custprops.types.CustomPropertyMultiRefdata
import com.k_int.web.toolkit.custprops.types.CustomPropertyText
import com.k_int.web.toolkit.custprops.types.CustomPropertyMultiText

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
import spock.lang.Shared


@Slf4j
@Integration
@Stepwise
@CurrentTenant
class CustomPropertiesSpec extends Specification {

  @Shared
  def grailsApplication
  
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

  /////////////////////////// Setup Refdata ///////////////////////////
  @Shared
  def refdataCategory = new RefdataCategory([
    desc: 'Test',
    internal: true,
  ]);

  /////////////////////////// Setup Custom properties ///////////////////////////
  @Transactional
  def "Setup custom properties" (String type, boolean hasMulti) {
    def custPropClass = grailsApplication.getDomainClass("com.k_int.web.toolkit.custprops.types.CustomProperty${type}").clazz
    def custPropMultiClass = hasMulti ? grailsApplication.getDomainClass("com.k_int.web.toolkit.custprops.types.CustomPropertyMulti${type}").clazz : null

    when:"Create ${type} property definition"
      def propertyDefs = [[
        "name" : "test${type}",
        "label" : "Test ${type}",
        "type": type,
        "description" : "A ${type} value custom property",
        "primary": true,
        "category": refdataCategory // Will be ignored by all but refdata
      ]]

      if (hasMulti) {
        propertyDefs << [
          "name" : "testMulti${type}",
          "label" : "Test Multi ${type}",
          "type": "Multi${type}",
          "multi": true,
          "description" : "A multi-valued ${type} custom property",
          "primary": true,
          "category": refdataCategory // Will be ignored by all but refdata
        ]
      }

      addDefinitions(propertyDefs)
      
    and: 'Query by name'
      final CustomPropertyDefinition cpd = CustomPropertyDefinition.findByName("test${type}")
      final CustomPropertyDefinition cpdm = hasMulti ? CustomPropertyDefinition.findByName("testMulti${type}") : null
      
    then: 'Definition recalled'
      cpd?.id != null
      cpd?.type == custPropClass
      hasMulti == false || cpdm?.id != null
      hasMulti == false || cpdm.type == custPropMultiClass

    where: 
      type        | hasMulti
      "Integer"   | true
      "Blob"      | true
      "Decimal"   | true
      "Refdata"   | true
      "Text"      | true
      "LocalDate" | true
      "Boolean"   | false
  }

  // Setup refdata
  @Shared
  def refdataOne
  @Shared
  def refdataTwo
  @Shared
  def refdataThree

  @Transactional
  def "Setup refdata" () {
    when: "refdata set up"
      refdataOne = RefdataValue.lookupOrCreate(refdataCategory, 'one')
      refdataTwo = RefdataValue.lookupOrCreate(refdataCategory, 'two')
      refdataThree = RefdataValue.lookupOrCreate(refdataCategory, 'three')
    then:
      1==1 // Just want to set up the refdata, don't really care to test this here
  }

  @Transactional(readOnly=true)
  def 'Bind custom property data' (String type, boolean hasMulti, def singleBind, def singleTest, def multiBind, def multiTest) {
    when: 'Binder called with data'
      final TestEntity te = new TestEntity()
      def custpropMap = [
        ("test${type}".toString()): [value: singleBind]
      ];

      if (hasMulti) {
        custpropMap["testMulti${type}".toString()] = [value: multiBind]
      }

      grailsWebDataBinder.bind te, new SimpleMapDataBindingSource ([
        custProps: custpropMap
      ])
      
    then: 'Property bound'
      (
        (hasMulti == true && (te.custProps.value?.size() ?: 0) == 2) ||
        (hasMulti == false && te.custProps.value?.size() ?: 0) == 1
      )
      
    and: 'Values as expected'
      final CustomProperty single = te.custProps?.value?.find { it.definition.name == "test${type}"}
      final CustomProperty multi = te.custProps?.value?.find { it.definition.name == "testMulti${type}"}
      final Collection multiVals = multi?.value

      (singleTest != null && single?.value == singleTest) ||
      (singleTest == null && single?.value == singleBind)

      hasMulti == false || (
        (
          multiTest != null &&
          multiTest?.every {mt ->
            multiVals.any {mv -> mv == mt} // This in place of "contains" for byteArray comparison
          }
        ) ||
        (
          multiTest == null &&
          multiBind?.every {mb ->
            multiVals.any {mv -> mv == mb} // This in place of "contains" for byteArray comparison
          }
        )
      )
  
    where:
      type        | hasMulti | singleBind           | singleTest | multiBind                            | multiTest
      "Integer"   | true     | 10                   | null       | [20, 30]                             | null
      "Blob"      | true     | [0, 0] as byte[]     | null       | [[0, 1] as byte[], [0, 2] as byte[]] | null
      "Decimal"   | true     | 0.1                  | null       | [0.2, 0.3]                           | null
      // TODO can't seem to bind refdata
      "Refdata"   | false    | refdataOne?.value    | refdataOne | null                                 | null

  }


/* 
  /////////////////////////// BLOB ///////////////////////////

  @Transactional
  def 'Create Blob value' () {
    
    when:"Create blob property definition"
      addDefinitions([[
        "name" : "testBlob",
        "label" : "Test Blob",
        "type": "Blob",
        "description" : "A blob value custom property",
        "primary": true
      ]])
      
      
    and: 'Query by name'
      final CustomPropertyDefinition cpd = CustomPropertyDefinition.findByName('testBlob')
      
    then: 'Definition recalled'
      cpd?.id != null
      cpd?.type == CustomPropertyBlob
  }
  
  @Transactional
  def 'Create Multi Blob value' () {
    
    when:"Create multi blob property definition"
      addDefinitions([[
        "name" : "testMultiBlob",
        "label" : "Test Multi-Valued Blob",
        "type": "MultiBlob",
        "description" : "A multi blob value custom property",
        "primary": true
      ]])
      
      
    and: 'Query by name'
      final CustomPropertyDefinition cpd = CustomPropertyDefinition.findByName('testMultiBlob')
      
    then: 'Definition recalled'
      cpd?.id != null
      cpd?.type == CustomPropertyMultiInteger
  }
  
  @Transactional(readOnly=true)
  def 'Bind custom property blob data' () {
    when: 'Binder called with data'
    def zeroArray = [0, 0, 0] as byte[]
    def oneArray = [1, 1, 1] as byte[]
    def twoArray = [2, 2, 2] as byte[]


      final TestEntity te = new TestEntity()
      grailsWebDataBinder.bind te, new SimpleMapDataBindingSource ([
        custProps: [
          'testBlob': [value: zeroArray],
          'testMultiBlob': [value: [oneArray,twoArray]]
        ]
      ])
      
    then: 'Property bound'
      (te.custProps.value?.size() ?: 0) == 2
      
    and: 'Values as expected'
      final CustomProperty single = te.custProps?.value?.find { it.definition.name == 'testBlob'}
      final CustomProperty multi = te.custProps?.value?.find { it.definition.name == 'testMultiBlob'}
      final Collection multiVals = multi?.value
      
      single?.value == zeroArray
      multiVals?.contains(oneArray)
      multiVals?.contains(twoArray)
  }

  /////////////////////////// BOOLEAN ///////////////////////////

   @Transactional
  def 'Create Boolean value' () {
    
    when:"Create boolean property definition"
      addDefinitions([[
        "name" : "testBool",
        "label" : "Test Boolean",
        "type": "Boolean",
        "description" : "A boolean value custom property",
        "primary": true
      ]])
      
      
    and: 'Query by name'
      final CustomPropertyDefinition cpd = CustomPropertyDefinition.findByName('testBool')
      
    then: 'Definition recalled'
      cpd?.id != null
      cpd?.type == CustomPropertyInteger
  }
  
  @Transactional(readOnly=true)
  def 'Bind custom property boolean data' () {
    when: 'Binder called with data'
      final TestEntity te = new TestEntity()
      grailsWebDataBinder.bind te, new SimpleMapDataBindingSource ([
        custProps: [
          'testBool': [value: true],
        ]
      ])
      
    then: 'Property bound'
      (te.custProps.value?.size() ?: 0) == 1
      
    and: 'Values as expected'
      final CustomProperty single = te.custProps?.value?.find { it.definition.name == 'testBool'}
      single?.value == true
  }

  // FIXME how do we test container
  /////////////////////////// DECIMAL ///////////////////////////

  @Transactional
  def 'Create Decimal value' () {
    
    when:"Create decimal property definition"
      addDefinitions([[
        "name" : "testDecimal",
        "label" : "Test Decimal",
        "type": "Decimal",
        "description" : "A decimal value custom property",
        "primary": true
      ]])
      
      
    and: 'Query by name'
      final CustomPropertyDefinition cpd = CustomPropertyDefinition.findByName('testDecimal')
      
    then: 'Definition recalled'
      cpd?.id != null
      cpd?.type == CustomPropertyInteger
  }
  
  @Transactional
  def 'Create Multi Decimal value' () {
    
    when:"Create multi decimal property definition"
      addDefinitions([[
        "name" : "testMultiDecimal",
        "label" : "Test Multi-Valued Decimal ",
        "type": "MultiDecimal",
        "description" : "A multi decimal value custom property",
        "primary": true
      ]])
      
      
    and: 'Query by name'
      final CustomPropertyDefinition cpd = CustomPropertyDefinition.findByName('testMultiBlob')
      
    then: 'Definition recalled'
      cpd?.id != null
      cpd?.type == CustomPropertyMultiInteger
  }
  
  @Transactional(readOnly=true)
  def 'Bind custom property decimal data' () {
    when: 'Binder called with data'

      final TestEntity te = new TestEntity()
      grailsWebDataBinder.bind te, new SimpleMapDataBindingSource ([
        custProps: [
          'testDecimal': [value: new BigDecimal(0.1)],
          'testMultiDecimal': [value: [new BigDecimal(0.2), new BigDecimal(0.3)]]
        ]
      ])
      
    then: 'Property bound'
      (te.custProps.value?.size() ?: 0) == 2
      
    and: 'Values as expected'
      final CustomProperty single = te.custProps?.value?.find { it.definition.name == 'testDecimal'}
      final CustomProperty multi = te.custProps?.value?.find { it.definition.name == 'testMultiDecimal'}
      final Collection multiVals = multi?.value
      
      single?.value == 0.1 as BigDecimal
      multiVals?.contains(0.2 as BigDecimal)
      multiVals?.contains(0.3 as BigDecimal)
  }

  /////////////////////////// LOCALDATE ///////////////////////////

  @Transactional
  def 'Create LocalDate value' () {
    
    when:"Create localDate property definition"
      addDefinitions([[
        "name" : "testLocalDate",
        "label" : "Test LocalDate",
        "type": "LocalDate",
        "description" : "A localDate value custom property",
        "primary": true
      ]])
      
      
    and: 'Query by name'
      final CustomPropertyDefinition cpd = CustomPropertyDefinition.findByName('testLocalDate')
      
    then: 'Definition recalled'
      cpd?.id != null
      cpd?.type == CustomPropertyInteger
  }
  
  @Transactional
  def 'Create Multi LocalDate value' () {
    
    when:"Create multi localDate property definition"
      addDefinitions([[
        "name" : "testMultiLocalDate",
        "label" : "Test Multi-Valued LocalDate ",
        "type": "MultiLocalDate",
        "description" : "A multi localDate value custom property",
        "primary": true
      ]])
      
      
    and: 'Query by name'
      final CustomPropertyDefinition cpd = CustomPropertyDefinition.findByName('testMultiLocalDate')
      
    then: 'Definition recalled'
      cpd?.id != null
      cpd?.type == CustomPropertyMultiInteger
  }
  
  @Transactional(readOnly=true)
  def 'Bind custom property decimal data' () {
    when: 'Binder called with data'
      def localDate1 = LocalDate.parse("1996-10-10")
      def localDate2 = LocalDate.parse("1999-02-06")
      def localDate3 = LocalDate.parse("2001-12-06")


      final TestEntity te = new TestEntity()
      grailsWebDataBinder.bind te, new SimpleMapDataBindingSource ([
        custProps: [
          'testLocalDate': [value: localDate1],
          'testMultiLocalDate': [value: [localDate2, localDate3]]
        ]
      ])
      
    then: 'Property bound'
      (te.custProps.value?.size() ?: 0) == 2
      
    and: 'Values as expected'
      final CustomProperty single = te.custProps?.value?.find { it.definition.name == 'testLocalDate'}
      final CustomProperty multi = te.custProps?.value?.find { it.definition.name == 'testMultiLocalDate'}
      final Collection multiVals = multi?.value
      
      single?.value == localDate1
      multiVals?.contains(localDate2)
      multiVals?.contains(localDate3)
  }
 */
}

@Entity
public class TestEntity implements MultiTenant<TestEntity>{
  CustomPropertyContainer custProps = new CustomPropertyContainer()
}

