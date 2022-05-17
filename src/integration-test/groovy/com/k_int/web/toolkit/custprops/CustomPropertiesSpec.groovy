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

import java.time.LocalDate

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
      "Container" | false
  }

  @Transactional(readOnly=true)
  def 'Bind custom property data' (String type, boolean hasMulti, def singleBind, def singleTest, def multiBind, def multiTest) {
    when: 'Binder called with data'
      final TestEntity te = new TestEntity()

      def custpropMap = [:]
      custpropMap["test${type}"] = [value: singleBind]
      
      if (hasMulti) {
        custpropMap["testMulti${type}"] = [value: multiBind]
      }

      grailsWebDataBinder.bind te, new SimpleMapDataBindingSource ([
        custProps: custpropMap
      ])
      
    then: 'Property bound'
      (
        (hasMulti == true && (te.custProps.value?.size() ?: 0) == 2) ||
        (hasMulti == false && (te.custProps.value?.size() ?: 0) == 1)
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
      type        | hasMulti | singleBind                     | singleTest | multiBind                                                      | multiTest
      "Integer"   | true     | 10                             | null       | [20, 30]                                                       | null
      "Blob"      | true     | [0, 0] as byte[]               | null       | [[0, 1] as byte[], [0, 2] as byte[]]                           | null
      "Decimal"   | true     | 0.1                            | null       | [0.2, 0.3]                                                     | null
      "LocalDate" | true     | LocalDate.parse("1996-10-10")  | null       | [LocalDate.parse("2001-12-06"), LocalDate.parse("2007-10-05")] | null
      "Text"      | true     | "wibble"                       | null       | ["wobble", "wubble"]                                           | null
      "Boolean"   | false    | true                           | null       | null                                                           | null
  }

  // Keeping Refdata separate to avoid passing objects around test.
  @Transactional
  def 'Bind refdata custom property data' () {
    def refdataOne = RefdataValue.lookupOrCreate(refdataCategory, 'one')
    def refdataTwo = RefdataValue.lookupOrCreate(refdataCategory, 'two')
    def refdataThree = RefdataValue.lookupOrCreate(refdataCategory, 'three')

    def multiBind = [refdataTwo, refdataThree];

    when: 'Binder called with data'
      final TestEntity te = new TestEntity()
      def custpropMap = [
        "testRefdata" : [value: refdataOne],
        "testMultiRefdata": [value: multiBind]
      ];

      grailsWebDataBinder.bind te, new SimpleMapDataBindingSource ([
        custProps: custpropMap
      ])
      
    then:
      te.custProps.value?.size() ?: 0 == 2
      
    and: 'Values as expected'
      final CustomProperty single = te.custProps?.value?.find { it.definition.name == "testRefdata"}
      final CustomProperty multi = te.custProps?.value?.find { it.definition.name == "testMultiRefdata"}
      final Collection multiVals = multi?.value

      single?.value == refdataOne

      multiBind?.every {mt ->
        multiVals.any {mv -> mv == mt}
      }
  }

  // CustomPropertyContainer is already tested via the above, nesting is _not_ tested currently
}

@Entity
public class TestEntity implements MultiTenant<TestEntity>{
  CustomPropertyContainer custProps = new CustomPropertyContainer()
}

