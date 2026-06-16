package com.k_int.web.toolkit.custprops

import java.time.LocalDate

import javax.persistence.Entity

import com.k_int.web.toolkit.custprops.types.CustomPropertyContainer
import com.k_int.web.toolkit.refdata.RefdataCategory
import com.k_int.web.toolkit.refdata.RefdataValue

import grails.databinding.SimpleMapDataBindingSource
import grails.gorm.MultiTenant
import grails.gorm.multitenancy.CurrentTenant
import grails.gorm.transactions.Transactional
import grails.testing.mixin.integration.Integration
import grails.util.Environment
import grails.web.databinding.GrailsWebDataBinder
import spock.lang.Requires
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise


@Integration
@Stepwise
@CurrentTenant
@Requires({Environment.currentEnvironment.name == 'test-livedb'})
class CustomPropertiesSpec extends Specification {

  @Shared
  def grailsApplication
  
  @Autowired
  GrailsWebDataBinder grailsWebDataBinder

  private void addDefinitions(final List<Map<String,?>> propertyDefinitions) {
    for (Map definition : propertyDefinitions) {
      String type = definition.remove('type')
      boolean multi = definition.remove('multi')
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

      if (hasMulti) {
        if (multiTest) {
          assert multiTest.every {mt ->
            multiVals.any {mv -> mv == mt} // This in place of "contains" for byteArray comparison
          }
        } else {
          assert multiBind?.every {mb ->
            multiVals.any {mv -> mv == mb} // This in place of "contains" for byteArray comparison
          }
        }
      }
  
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
    
    when: "Refdata looked-up or created"
      String refdataOne = RefdataValue.lookupOrCreate(refdataCategory, 'one')?.id
      String refdataTwo = RefdataValue.lookupOrCreate(refdataCategory, 'two')?.id
      String refdataThree = RefdataValue.lookupOrCreate(refdataCategory, 'three')?.id

    and: 'Binder called with data'
      final TestEntity te = new TestEntity()
      def custpropMap = [
        "testRefdata" : [
          value: refdataOne
        ],
        "testMultiRefdata": [
          value: [refdataTwo, refdataThree]
        ]
      ];

      grailsWebDataBinder.bind te, new SimpleMapDataBindingSource ([
        custProps: custpropMap
      ])
      
    then:
      te.custProps.value?.size() ?: 0 == 2
    
    and: 'No errors'
      !te.hasErrors()
      
    and: 'Values as expected'
      final CustomProperty single = te.custProps?.value?.find { it.definition.name == "testRefdata"}
      final CustomProperty multi = te.custProps?.value?.find { it.definition.name == "testMultiRefdata"}
      final Collection multiVals = multi?.value

      single?.value?.id == refdataOne

      multi.value?.size() == 2
      
      def ids = multi.value.collect { it.id }
      
      ids.contains(refdataTwo)
      ids.contains(refdataThree)
  }

  /////////////////////////// Name validation ///////////////////////////

  @Transactional
  def "Name validation accepts valid name '#validName'" (String validName) {
    when: 'Creating a definition with a valid name'
      CustomPropertyDefinition cpd = CustomPropertyDefinition.forType('Text', [
        name: validName,
        label: "Label ${validName}",
        description: 'name-validation positive case',
        primary: true
      ])
      def saved = cpd.save(flush: true)

    then: 'Save succeeds and no errors are raised on name'
      saved != null
      !cpd.hasErrors()

    where:
      validName << [
        'plainName',
        'with_underscore',
        'startsWithLetterThenDigits1',
        '日本語Term',
        'Юніт_тест',
        'ÉlémentTest'
      ]
  }

  @Transactional
  def "Name validation rejects invalid name '#invalidName'" (String invalidName) {
    when: 'Creating a definition with an invalid name'
      CustomPropertyDefinition cpd = CustomPropertyDefinition.forType('Text', [
        name: invalidName,
        label: 'Label invalid case',
        description: 'name-validation negative case',
        primary: true
      ])
      def saved = cpd.save(flush: true)

    then: 'Save fails with a validation error on the name field'
      saved == null
      cpd.hasErrors()
      cpd.errors.getFieldError('name') != null

    where:
      invalidName << [
        '1startsWithDigit',
        'has.dot',
        'has space',
        'has-hyphen',
        'has(paren',
        'has,comma',
        'has/slash',
        'has#hash'
      ]
  }

  @Transactional
  def "Legacy row with a forbidden name can be updated without renaming" () {
    given: 'A row persisted bypassing validation, simulating data created before the constraint existed'
      CustomPropertyDefinition legacy = CustomPropertyDefinition.forType('Text', [
        name: 'legacy.unmodified.name',
        label: 'Legacy term',
        description: 'original description',
        primary: true
      ])
      legacy.save(flush: true, validate: false)
      String legacyId = legacy.id

    when: 'Reloading and updating only the description'
      CustomPropertyDefinition reloaded = CustomPropertyDefinition.get(legacyId)
      reloaded.description = 'updated description'
      def saved = reloaded.save(flush: true)

    then: 'Save succeeds because the validator is skipped when name is not dirty'
      saved != null
      !reloaded.hasErrors()
      reloaded.description == 'updated description'
      reloaded.name == 'legacy.unmodified.name'
  }

  @Transactional
  def "Legacy row name cannot be changed to another forbidden value" () {
    given: 'A row with a forbidden legacy name'
      CustomPropertyDefinition legacy = CustomPropertyDefinition.forType('Text', [
        name: 'legacy.rename.attempt',
        label: 'Legacy term rename attempt',
        description: 'desc',
        primary: true
      ])
      legacy.save(flush: true, validate: false)
      String legacyId = legacy.id

    when: 'Changing the name to another invalid value'
      CustomPropertyDefinition reloaded = CustomPropertyDefinition.get(legacyId)
      reloaded.name = 'still.invalid'
      def saved = reloaded.save(flush: true)

    then: 'Validation fires because name is dirty and the new value also fails the rule'
      saved == null
      reloaded.hasErrors()
      reloaded.errors.getFieldError('name') != null
  }

  @Transactional
  def "Legacy row name can be migrated to a valid value" () {
    given: 'A row with a forbidden legacy name'
      CustomPropertyDefinition legacy = CustomPropertyDefinition.forType('Text', [
        name: 'legacy.migrate.source',
        label: 'Legacy term migrate',
        description: 'desc',
        primary: true
      ])
      legacy.save(flush: true, validate: false)
      String legacyId = legacy.id

    when: 'Renaming to a compliant value'
      CustomPropertyDefinition reloaded = CustomPropertyDefinition.get(legacyId)
      reloaded.name = 'cleanedUpName'
      def saved = reloaded.save(flush: true)

    then: 'Save succeeds'
      saved != null
      !reloaded.hasErrors()
      reloaded.name == 'cleanedUpName'
  }

  // CustomPropertyContainer is already tested via the above, nesting is _not_ tested currently
}

@Entity
public class TestEntity implements MultiTenant<TestEntity>{
  CustomPropertyContainer custProps = new CustomPropertyContainer()
}
