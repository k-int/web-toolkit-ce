package com.k_int.web.toolkit


import java.time.LocalDate
import java.time.format.DateTimeFormatter

import org.grails.orm.hibernate.HibernateDatastore
import org.grails.orm.hibernate.cfg.Settings
import org.hibernate.MappingException
import org.hibernate.criterion.Restrictions
import org.hibernate.cfg.Environment
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.interceptor.DefaultTransactionAttribute

import com.k_int.web.toolkit.databinding.ExtendedWebDataBinder
import com.k_int.web.toolkit.query.JpaCriteriaQueryBackend
import com.k_int.web.toolkit.query.LookupQuerySpec
import com.k_int.web.toolkit.query.SimpleLookupQueryBackend
import com.k_int.web.toolkit.utils.DomainUtils

import grails.compiler.GrailsCompileStatic
import grails.persistence.Entity
import grails.test.hibernate.HibernateSpec
import grails.testing.services.ServiceUnitTest
import grails.web.databinding.DataBindingUtils
import spock.lang.Stepwise

@Stepwise
public class SimpleLookupServiceSpec extends HibernateSpec implements ServiceUnitTest<SimpleLookupService>{
  void setupSpec() {
		defineBeans {
			"${DataBindingUtils.DATA_BINDER_BEAN_NAME}"(ExtendedWebDataBinder, grailsApplication)
		}
    
    // Now create the ValueConverterService. Important to do this after the replacement of the binder above.
    mockArtefact(ValueConverterService)
    
    // Add the MappingContext created by this spec to the appContext as it is needed by the
    // tooling used within the SimpleLookupService
    HibernateDatastore ds = hibernateDatastore
    ConfigurableListableBeanFactory beanFactory = ((ConfigurableApplicationContext) applicationContext).getBeanFactory();
    if (beanFactory.containsSingleton(DomainUtils.BEAN_MAPPING_CONTEXT)) {
      beanFactory.destroySingleton(DomainUtils.BEAN_MAPPING_CONTEXT)
    }
    beanFactory.registerSingleton(DomainUtils.BEAN_MAPPING_CONTEXT, ds.getMappingContext())
    
    // Manually create a transaction to add the data for the spec.
    PlatformTransactionManager manager = transactionManager
    TransactionStatus transaction = manager.getTransaction(new DefaultTransactionAttribute())
    
    // Add some data
    Request r = new Request(name: 'Request 1')
    .addToChecklists(new CheckList(name: 'list_1')
      .addToItems(new CheckListItem(
        outcome: 'Yes',
        status: 'required'
      ))
    )
    r.number = 1
    r.date = LocalDate.now()
    r.save(failOnError: true)
    
    r = new Request(name: 'Request 2')
    .addToChecklists(new CheckList(name: 'list_2')
      .addToItems(new CheckListItem(
        outcome: 'no',
        status: 'not required'
      ))
      .addToItems(new CheckListItem(
        outcome: null,
        status: 'required'
      ))
    )
    r.number = 2
    r.date = LocalDate.now()
    r.save(failOnError: true)
    
    r = new Request(name: 'Request 3')
    .addToChecklists(new CheckList(name: 'list_3')
      .addToItems(new CheckListItem(
        outcome: 'unknown',
        status: 'required'
      ))
      .addToItems(new CheckListItem(
        outcome: 'yes',
        status: 'required'
      ))
    )
    .addToChecklists(new CheckList(name: 'list_4')
      .addToItems(new CheckListItem(
        outcome: null,
        status: 'not required'
      ))
    )
    r.number = 3
    r.date = LocalDate.now().plusDays(1) // tomorrow
    r.save(failOnError: true, flush: true)
      
    // Commit the transaction so we can use the data that was added.  
    manager.commit(transaction)
  }
  
  void 'Check Data' () {
    expect: "We have 3 Requests"
      Request.findAll()?.size() == 3
      
    and: 'We have 4 checklists'
      CheckList.findAll()?.size() == 4
      
    and: 'We have 6 check lists items'
      CheckListItem.findAll()?.size() == 6
  }

  void 'Lookup delegates query shaping to configured backend contract' () {
    given: 'A configured query backend mock'
      def backend = Mock(SimpleLookupQueryBackend)
      service.simpleLookupQueryBackend = backend

    when: 'Lookup is executed with filters and paging'
      def results = service.lookup(Request, null, 10, 1, [
        "checklists.items.outcome==yes"
      ], [], [])

    then: 'Backend contract is invoked with expected query spec'
      1 * backend.apply(_, {
        LookupQuerySpec spec ->
          spec.term == null &&
          spec.filters == ["checklists.items.outcome==yes"] &&
          spec.matchIn == [] &&
          spec.sorts == [] &&
          spec.rootEntityClass == Request
      })

    and: 'Lookup still returns a list-shaped result'
      results != null
      results instanceof List
  }

  void 'Lookup routes nested equality filter to selected JPA backend bean' () {
    given: 'Backend selector is set to jpa and a backend bean is provided'
      def jpaBackend = Mock(SimpleLookupQueryBackend)
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = jpaBackend
      service.queryBackend = 'jpa'

    when: 'Lookup executes a nested equality filter'
      def results = service.lookup(Request, null, 10, 1, [
        "checklists.items.outcome==yes"
      ], [], [])

    then: 'The selected backend receives the expected query spec'
      1 * jpaBackend.apply(_, {
        LookupQuerySpec spec ->
          spec.filters == ["checklists.items.outcome==yes"] &&
          spec.rootEntityClass == Request
      })

    and: 'A list-shaped result is still returned'
      results != null
      results instanceof List
  }

  void 'Lookup constructs JPA backend with AST parser gate disabled by default' () {
    given: 'JPA selection uses service-created backend and default AST gate'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
      service.queryBackend = 'jpa'
      service.jpaAstFilterParserEnabled = false
      Boolean capturedAstFlag = null
      service.metaClass.newJpaQueryBackend = { SimpleLookupQueryBackend legacyBackend, boolean useAstFilterParser ->
        capturedAstFlag = useAstFilterParser
        ([apply: { Object criteriaTarget, LookupQuerySpec spec -> }] as SimpleLookupQueryBackend)
      }

    when: 'Lookup resolves and constructs JPA backend'
      service.lookup(Request, null, 10, 1, [
        "name==Request 1"
      ], [], [])

    then: 'AST parser gate remains disabled by default'
      capturedAstFlag == false

    cleanup:
      GroovySystem.metaClassRegistry.removeMetaClass(SimpleLookupService)
      service.queryBackend = 'legacy'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
  }

  void 'Lookup constructs JPA backend with AST parser gate enabled when configured' () {
    given: 'JPA selection uses service-created backend and AST gate is enabled'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
      service.queryBackend = 'jpa'
      service.jpaAstFilterParserEnabled = true
      Boolean capturedAstFlag = null
      service.metaClass.newJpaQueryBackend = { SimpleLookupQueryBackend legacyBackend, boolean useAstFilterParser ->
        capturedAstFlag = useAstFilterParser
        ([apply: { Object criteriaTarget, LookupQuerySpec spec -> }] as SimpleLookupQueryBackend)
      }

    when: 'Lookup resolves and constructs JPA backend'
      service.lookup(Request, null, 10, 1, [
        "name==Request 1"
      ], [], [])

    then: 'AST parser gate is passed through to backend construction'
      capturedAstFlag == true

    cleanup:
      GroovySystem.metaClassRegistry.removeMetaClass(SimpleLookupService)
      service.queryBackend = 'legacy'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
  }

  void 'Lookup defaults to legacy backend bean when selector is not overridden' () {
    given: 'Default backend selector and a legacy backend bean'
      def legacyBackend = Mock(SimpleLookupQueryBackend)
      service.simpleLookupQueryBackend = null
      service.legacyCriteriaQueryBackend = legacyBackend
      service.queryBackend = null

    when: 'Lookup executes a nested equality filter'
      def results = service.lookup(Request, null, 10, 1, [
        "checklists.items.outcome==yes"
      ], [], [])

    then: 'The default routing selects legacy backend'
      1 * legacyBackend.apply(_, {
        LookupQuerySpec spec ->
          spec.filters == ["checklists.items.outcome==yes"] &&
          spec.rootEntityClass == Request
      })

    and: 'A list-shaped result is still returned'
      results != null
      results instanceof List

    cleanup:
      service.legacyCriteriaQueryBackend = null
  }

  void 'Lookup rejects legacy backend when legacy usage is disabled' () {
    given: 'Legacy backend selection with hard-disable flag'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
      service.queryBackend = 'legacy'
      service.allowLegacyQueryBackend = false

    when: 'Lookup is executed'
      service.lookup(Request, null, 10, 1, [
        "checklists.items.outcome==yes"
      ], [], [])

    then: 'Legacy backend is explicitly rejected'
      thrown(UnsupportedOperationException)

    cleanup:
      service.allowLegacyQueryBackend = true
      service.queryBackend = 'legacy'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
  }

  void 'JPA backend parity for nested equality filter' () {
    given: 'Legacy backend result baseline'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
      service.queryBackend = 'legacy'
      List<Request> legacyResults = service.lookup(Request, null, 10, 1, [
        "checklists.items.outcome==yes"
      ])

    when: 'Same filter is executed with JPA backend selector'
      service.queryBackend = 'jpa'
      List<Request> jpaResults = service.lookup(Request, null, 10, 1, [
        "checklists.items.outcome==yes"
      ])

    then: 'Result parity is preserved for this slice'
      legacyResults*.id == jpaResults*.id
      jpaResults.size() == 1
      jpaResults[0].name == 'Request 3'

    cleanup:
      service.queryBackend = 'legacy'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
  }

  void 'JPA backend parity for nested null-check filter' () {
    given: 'Legacy backend result baseline'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
      service.queryBackend = 'legacy'
      List<Request> legacyResults = service.lookup(Request, null, 10, 1, [
        "checklists.items.outcome isNull"
      ])

    when: 'Same filter is executed with JPA backend selector'
      service.queryBackend = 'jpa'
      List<Request> jpaResults = service.lookup(Request, null, 10, 1, [
        "checklists.items.outcome isNull"
      ])

    then: 'Result parity is preserved for null-check slice'
      legacyResults*.id == jpaResults*.id

    cleanup:
      service.queryBackend = 'legacy'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
  }

  void 'JPA backend parity for nested not-equal filter' () {
    given: 'Legacy backend result baseline'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
      service.queryBackend = 'legacy'
      List<Request> legacyResults = service.lookup(Request, null, 10, 1, [
        "checklists.items.outcome!=yes"
      ])

    when: 'Same filter is executed with JPA backend selector'
      service.queryBackend = 'jpa'
      List<Request> jpaResults = service.lookup(Request, null, 10, 1, [
        "checklists.items.outcome!=yes"
      ])

    then: 'Result parity is preserved for not-equal slice'
      legacyResults*.id == jpaResults*.id

    cleanup:
      service.queryBackend = 'legacy'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
  }

  void 'JPA backend parity for inline conjunction filter' () {
    given: 'Legacy backend result baseline'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
      service.queryBackend = 'legacy'
      List<Request> legacyResults = service.lookup(Request, null, 10, 1, [
        "checklists.items.outcome==unknown&&checklists.items.status==required"
      ])

    when: 'Same filter is executed with JPA backend selector'
      service.queryBackend = 'jpa'
      List<Request> jpaResults = service.lookup(Request, null, 10, 1, [
        "checklists.items.outcome==unknown&&checklists.items.status==required"
      ])

    then: 'Result parity is preserved for conjunction slice'
      legacyResults*.id == jpaResults*.id

    cleanup:
      service.queryBackend = 'legacy'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
  }

  void 'JPA backend parity for grouped item expression filter' () {
    given: 'Legacy backend result baseline'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
      service.queryBackend = 'legacy'
      List<Request> legacyResults = service.lookup(Request, null, 10, 1, [
        "(checklists.items.outcome==unknown&&checklists.items.status==required)||(checklists.items.outcome==yes&&checklists.items.status==required)"
      ])

    when: 'Same grouped filter is executed with JPA backend selector'
      service.queryBackend = 'jpa'
      List<Request> jpaResults = service.lookup(Request, null, 10, 1, [
        "(checklists.items.outcome==unknown&&checklists.items.status==required)||(checklists.items.outcome==yes&&checklists.items.status==required)"
      ])

    then: 'Grouped expression parity is preserved'
      legacyResults*.id == jpaResults*.id

    cleanup:
      service.queryBackend = 'legacy'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
  }

  void 'JPA backend AST parser parity for grouped item expression filter' () {
    given: 'Legacy backend baseline for grouped item predicates'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
      service.queryBackend = 'legacy'
      List<Request> legacyResults = service.lookup(Request, null, 10, 1, [
        "(checklists.items.outcome==unknown&&checklists.items.status==required)||(checklists.items.outcome==yes&&checklists.items.status==required)"
      ])

    when: 'JPA backend uses AST filter parser for the same grouped expression'
      service.jpaCriteriaQueryBackend = new JpaCriteriaQueryBackend(null, true)
      service.queryBackend = 'jpa'
      List<Request> jpaResults = service.lookup(Request, null, 10, 1, [
        "(checklists.items.outcome==unknown&&checklists.items.status==required)||(checklists.items.outcome==yes&&checklists.items.status==required)"
      ])

    then: 'AST path preserves parity with legacy results'
      legacyResults*.id == jpaResults*.id

    cleanup:
      service.queryBackend = 'legacy'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
  }

  void 'JPA backend AST parser parity for nested item not-equal filter' () {
    given: 'Legacy backend baseline for item not-equal'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
      service.queryBackend = 'legacy'
      List<Request> legacyResults = service.lookup(Request, null, 10, 1, [
        "checklists.items.outcome!=yes"
      ])

    when: 'JPA backend uses AST parser for same item not-equal filter'
      service.jpaCriteriaQueryBackend = new JpaCriteriaQueryBackend(null, true)
      service.queryBackend = 'jpa'
      List<Request> jpaResults = service.lookup(Request, null, 10, 1, [
        "checklists.items.outcome!=yes"
      ])

    then: 'AST path preserves item not-equal parity'
      legacyResults*.id == jpaResults*.id

    cleanup:
      service.queryBackend = 'legacy'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
  }

  void 'JPA backend AST parser parity with default JPA for item isEmpty special operator' () {
    given: 'Default JPA backend baseline for item isEmpty'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
      service.queryBackend = 'jpa'
      List<Request> defaultJpaResults = service.lookup(Request, null, 10, 1, [
        "checklists.items.outcome isEmpty"
      ])

    when: 'JPA backend uses AST parser for same item isEmpty filter'
      service.jpaCriteriaQueryBackend = new JpaCriteriaQueryBackend(null, true)
      List<Request> astJpaResults = service.lookup(Request, null, 10, 1, [
        "checklists.items.outcome isEmpty"
      ])

    then: 'AST path preserves default JPA item isEmpty semantics'
      defaultJpaResults*.id == astJpaResults*.id

    cleanup:
      service.queryBackend = 'legacy'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
  }

  void 'JPA backend AST parser parity with default JPA for item isNotEmpty special operator' () {
    given: 'Default JPA backend baseline for item isNotEmpty'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
      service.queryBackend = 'jpa'
      List<Request> defaultJpaResults = service.lookup(Request, null, 10, 1, [
        "checklists.items.outcome isNotEmpty"
      ])

    when: 'JPA backend uses AST parser for same item isNotEmpty filter'
      service.jpaCriteriaQueryBackend = new JpaCriteriaQueryBackend(null, true)
      List<Request> astJpaResults = service.lookup(Request, null, 10, 1, [
        "checklists.items.outcome isNotEmpty"
      ])

    then: 'AST path preserves default JPA item isNotEmpty semantics'
      defaultJpaResults*.id == astJpaResults*.id

    cleanup:
      service.queryBackend = 'legacy'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
  }

  void 'JPA backend parity for range filter' () {
    given: 'Legacy backend result baseline'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
      service.queryBackend = 'legacy'
      List<Request> legacyResults = service.lookup(Request, null, 10, 1, [
        "1<=number<4"
      ])

    when: 'Same range filter is executed with JPA backend selector'
      service.queryBackend = 'jpa'
      List<Request> jpaResults = service.lookup(Request, null, 10, 1, [
        "1<=number<4"
      ])

    then: 'Range parity is preserved'
      legacyResults*.id == jpaResults*.id

    cleanup:
      service.queryBackend = 'legacy'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
  }

  void 'JPA backend parity for multi-filter array semantics' () {
    given: 'Legacy backend baseline for multiple filters combined with AND'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
      service.queryBackend = 'legacy'
      List<Request> legacyResults = service.lookup(Request, null, 10, 1, [
        "checklists.items.outcome isNull",
        "checklists.items.status=i=required"
      ])

    when: 'Same multi-filter array is executed with JPA backend selector'
      service.queryBackend = 'jpa'
      List<Request> jpaResults = service.lookup(Request, null, 10, 1, [
        "checklists.items.outcome isNull",
        "checklists.items.status=i=required"
      ])

    then: 'Array semantics parity is preserved'
      legacyResults*.id == jpaResults*.id

    cleanup:
      service.queryBackend = 'legacy'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
  }

  void 'JPA backend parity for checklist-level predicate coverage' () {
    given: 'Legacy backend baseline with checklist and item predicates'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
      service.queryBackend = 'legacy'
      List<Request> legacyResults = service.lookup(Request, null, 10, 1, [
        "checklists.name==list_3",
        "checklists.items.outcome==unknown&&checklists.items.status==required"
      ])

    when: 'Same predicates are executed with JPA backend selector'
      service.queryBackend = 'jpa'
      List<Request> jpaResults = service.lookup(Request, null, 10, 1, [
        "checklists.name==list_3",
        "checklists.items.outcome==unknown&&checklists.items.status==required"
      ])

    then: 'Checklist-level predicate parity is preserved'
      legacyResults*.id == jpaResults*.id

    cleanup:
      service.queryBackend = 'legacy'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
  }

  void 'JPA backend parity for isSet special operator' () {
    given: 'Legacy backend baseline for isSet on nested item field'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
      service.queryBackend = 'legacy'
      List<Request> legacyResults = service.lookup(Request, null, 10, 1, [
        "checklists.items.outcome isSet"
      ])

    when: 'Same special operator filter is executed with JPA backend selector'
      service.queryBackend = 'jpa'
      List<Request> jpaResults = service.lookup(Request, null, 10, 1, [
        "checklists.items.outcome isSet"
      ])

    then: 'isSet parity is preserved'
      legacyResults*.id == jpaResults*.id

    cleanup:
      service.queryBackend = 'legacy'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
  }

  void 'JPA backend parity for isNotSet special operator' () {
    given: 'Legacy backend baseline for isNotSet on nested item field'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
      service.queryBackend = 'legacy'
      List<Request> legacyResults = service.lookup(Request, null, 10, 1, [
        "checklists.items.outcome isNotSet"
      ])

    when: 'Same special operator filter is executed with JPA backend selector'
      service.queryBackend = 'jpa'
      List<Request> jpaResults = service.lookup(Request, null, 10, 1, [
        "checklists.items.outcome isNotSet"
      ])

    then: 'isNotSet parity is preserved'
      legacyResults*.id == jpaResults*.id

    cleanup:
      service.queryBackend = 'legacy'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
  }

  void 'JPA backend parity for contains operator' () {
    given: 'Legacy backend baseline for contains on nested item field'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
      service.queryBackend = 'legacy'
      List<Request> legacyResults = service.lookup(Request, null, 10, 1, [
        "checklists.items.status=~required"
      ])

    when: 'Same contains filter is executed with JPA backend selector'
      service.queryBackend = 'jpa'
      List<Request> jpaResults = service.lookup(Request, null, 10, 1, [
        "checklists.items.status=~required"
      ])

    then: 'Contains parity is preserved'
      legacyResults*.id == jpaResults*.id

    cleanup:
      service.queryBackend = 'legacy'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
  }

  void 'JPA backend parity for not-contains operator' () {
    given: 'Legacy backend baseline for not-contains on nested item field'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
      service.queryBackend = 'legacy'
      List<Request> legacyResults = service.lookup(Request, null, 10, 1, [
        "checklists.items.outcome!~yes"
      ])

    when: 'Same not-contains filter is executed with JPA backend selector'
      service.queryBackend = 'jpa'
      List<Request> jpaResults = service.lookup(Request, null, 10, 1, [
        "checklists.items.outcome!~yes"
      ])

    then: 'Not-contains parity is preserved'
      legacyResults*.id == jpaResults*.id

    cleanup:
      service.queryBackend = 'legacy'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
  }

  void 'JPA backend parity for root-field equality predicate' () {
    given: 'Legacy backend baseline for root property equality'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
      service.queryBackend = 'legacy'
      List<Request> legacyResults = service.lookup(Request, null, 10, 1, [
        "number==2"
      ])

    when: 'Same root equality is executed with JPA backend selector'
      service.queryBackend = 'jpa'
      List<Request> jpaResults = service.lookup(Request, null, 10, 1, [
        "number==2"
      ])

    then: 'Root equality parity is preserved'
      legacyResults*.id == jpaResults*.id

    cleanup:
      service.queryBackend = 'legacy'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
  }

  void 'JPA backend parity for root-field not-equal predicate semantics' () {
    given: 'Legacy backend baseline for root not-equal operators'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
      service.queryBackend = 'legacy'
      LocalDate today = LocalDate.now()
      String todayIso = DateTimeFormatter.ISO_DATE.format(today)
      List<Request> legacyNumberNeqResults = service.lookup(Request, null, 10, 1, [
        "number!=2"
      ])
      List<Request> legacyDateNeqResults = service.lookup(Request, null, 10, 1, [
        "date!=${todayIso}"
      ])

    when: 'Same root not-equal operators are executed with JPA backend selector'
      service.queryBackend = 'jpa'
      List<Request> jpaNumberNeqResults = service.lookup(Request, null, 10, 1, [
        "number!=2"
      ])
      List<Request> jpaDateNeqResults = service.lookup(Request, null, 10, 1, [
        "date!=${todayIso}"
      ])

    then: 'Root not-equal parity is preserved'
      legacyNumberNeqResults*.id == jpaNumberNeqResults*.id
      legacyDateNeqResults*.id == jpaDateNeqResults*.id

    cleanup:
      service.queryBackend = 'legacy'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
  }

  void 'JPA backend parity for checklist-name not-equal predicate' () {
    given: 'Legacy backend baseline for checklist-level not-equal'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
      service.queryBackend = 'legacy'
      List<Request> legacyResults = service.lookup(Request, null, 10, 1, [
        "checklists.name!=list_3"
      ])

    when: 'Same checklist-level not-equal runs with JPA backend selector'
      service.queryBackend = 'jpa'
      List<Request> jpaResults = service.lookup(Request, null, 10, 1, [
        "checklists.name!=list_3"
      ])

    then: 'Checklist not-equal parity is preserved'
      legacyResults*.id == jpaResults*.id

    cleanup:
      service.queryBackend = 'legacy'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
  }

  void 'Legacy backend raises mapping exception for checklist-name isEmpty and isNotEmpty' () {
    given: 'Legacy backend selector'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
      service.queryBackend = 'legacy'

    when: 'Legacy backend evaluates checklist-name isEmpty'
      service.lookup(Request, null, 10, 1, [
        "checklists.name isEmpty"
      ])

    then: 'Legacy behavior is a mapping exception for this unsupported path/operator combination'
      thrown(MappingException)

    when: 'Legacy backend evaluates checklist-name isNotEmpty'
      service.lookup(Request, null, 10, 1, [
        "checklists.name isNotEmpty"
      ])

    then: 'Legacy behavior is also a mapping exception for isNotEmpty'
      thrown(MappingException)

    cleanup:
      service.queryBackend = 'legacy'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
  }

  void 'Strict JPA mode fails fast for checklist-name isEmpty and isNotEmpty' () {
    given: 'JPA backend is configured without fallback'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = new JpaCriteriaQueryBackend(null)
      service.queryBackend = 'jpa'

    when: 'Strict JPA backend evaluates checklist-name isEmpty'
      service.lookup(Request, null, 10, 1, [
        "checklists.name isEmpty"
      ])

    then: 'Unsupported operator/path fails fast under strict JPA mode'
      thrown(UnsupportedOperationException)

    when: 'Strict JPA backend evaluates checklist-name isNotEmpty'
      service.lookup(Request, null, 10, 1, [
        "checklists.name isNotEmpty"
      ])

    then: 'Unsupported operator/path fails fast under strict JPA mode'
      thrown(UnsupportedOperationException)

    cleanup:
      service.queryBackend = 'legacy'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
  }

  void 'Legacy root-name isEmpty and isNotEmpty remain unsupported' () {
    given: 'Legacy backend is active'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
      service.queryBackend = 'legacy'

    when: 'Legacy backend evaluates root-name isEmpty'
      service.lookup(Request, null, 10, 1, [
        "name isEmpty"
      ])

    then: 'Legacy behavior is a mapping exception for this unsupported operator'
      thrown(MappingException)

    when: 'Legacy backend evaluates root-name isNotEmpty'
      service.lookup(Request, null, 10, 1, [
        "name isNotEmpty"
      ])

    then: 'Legacy behavior is also a mapping exception for isNotEmpty'
      thrown(MappingException)

    cleanup:
      service.queryBackend = 'legacy'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
  }

  void 'Strict JPA mode fails fast for root-name isEmpty and isNotEmpty' () {
    given: 'JPA backend is configured without fallback'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = new JpaCriteriaQueryBackend(null)
      service.queryBackend = 'jpa'

    when: 'Strict JPA backend evaluates root-name isEmpty'
      service.lookup(Request, null, 10, 1, [
        "name isEmpty"
      ])

    then: 'Unsupported operator/path fails fast under strict JPA mode'
      thrown(UnsupportedOperationException)

    when: 'Strict JPA backend evaluates root-name isNotEmpty'
      service.lookup(Request, null, 10, 1, [
        "name isNotEmpty"
      ])

    then: 'Unsupported operator/path fails fast under strict JPA mode'
      thrown(UnsupportedOperationException)

    cleanup:
      service.queryBackend = 'legacy'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
  }

  void 'JPA backend parity for checklist-request-name equality predicates' () {
    given: 'Legacy backend baseline for checklist request-name equality and case-insensitive equality'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
      service.queryBackend = 'legacy'
      List<Request> legacyEqResults = service.lookup(Request, null, 10, 1, [
        "checklists.request.name==Request 2"
      ])
      List<Request> legacyIeqResults = service.lookup(Request, null, 10, 1, [
        "checklists.request.name=i=request 2"
      ])

    when: 'Same checklist request-name predicates run with JPA backend selector'
      service.queryBackend = 'jpa'
      List<Request> jpaEqResults = service.lookup(Request, null, 10, 1, [
        "checklists.request.name==Request 2"
      ])
      List<Request> jpaIeqResults = service.lookup(Request, null, 10, 1, [
        "checklists.request.name=i=request 2"
      ])

    then: 'Checklist request-name equality parity is preserved'
      legacyEqResults*.id == jpaEqResults*.id
      legacyIeqResults*.id == jpaIeqResults*.id

    cleanup:
      service.queryBackend = 'legacy'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
  }

  void 'JPA backend parity for checklist-request-name null and not-equal semantics' () {
    given: 'Legacy backend baseline for checklist request-name null and not-equal operators'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
      service.queryBackend = 'legacy'
      List<Request> legacyNeqResults = service.lookup(Request, null, 10, 1, [
        "checklists.request.name!=Request 2"
      ])
      List<Request> legacyNullResults = service.lookup(Request, null, 10, 1, [
        "checklists.request.name isNull"
      ])
      List<Request> legacyNotNullResults = service.lookup(Request, null, 10, 1, [
        "checklists.request.name isNotNull"
      ])
      List<Request> legacyIsSetResults = service.lookup(Request, null, 10, 1, [
        "checklists.request.name isSet"
      ])
      List<Request> legacyIsNotSetResults = service.lookup(Request, null, 10, 1, [
        "checklists.request.name isNotSet"
      ])

    when: 'Same checklist request-name operators run with JPA backend selector'
      service.queryBackend = 'jpa'
      List<Request> jpaNeqResults = service.lookup(Request, null, 10, 1, [
        "checklists.request.name!=Request 2"
      ])
      List<Request> jpaNullResults = service.lookup(Request, null, 10, 1, [
        "checklists.request.name isNull"
      ])
      List<Request> jpaNotNullResults = service.lookup(Request, null, 10, 1, [
        "checklists.request.name isNotNull"
      ])
      List<Request> jpaIsSetResults = service.lookup(Request, null, 10, 1, [
        "checklists.request.name isSet"
      ])
      List<Request> jpaIsNotSetResults = service.lookup(Request, null, 10, 1, [
        "checklists.request.name isNotSet"
      ])

    then: 'Checklist request-name null and not-equal parity is preserved'
      legacyNeqResults*.id == jpaNeqResults*.id
      legacyNullResults*.id == jpaNullResults*.id
      legacyNotNullResults*.id == jpaNotNullResults*.id
      legacyIsSetResults*.id == jpaIsSetResults*.id
      legacyIsNotSetResults*.id == jpaIsNotSetResults*.id

    cleanup:
      service.queryBackend = 'legacy'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
  }

  void 'JPA backend parity for checklist-request-name contains semantics' () {
    given: 'Legacy backend baseline for checklist request-name contains operators'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
      service.queryBackend = 'legacy'
      List<Request> legacyContainsResults = service.lookup(Request, null, 10, 1, [
        "checklists.request.name=~Request"
      ])
      List<Request> legacyNotContainsResults = service.lookup(Request, null, 10, 1, [
        "checklists.request.name!~3"
      ])

    when: 'Same checklist request-name contains operators run with JPA backend selector'
      service.queryBackend = 'jpa'
      List<Request> jpaContainsResults = service.lookup(Request, null, 10, 1, [
        "checklists.request.name=~Request"
      ])
      List<Request> jpaNotContainsResults = service.lookup(Request, null, 10, 1, [
        "checklists.request.name!~3"
      ])

    then: 'Checklist request-name contains parity is preserved'
      legacyContainsResults*.id == jpaContainsResults*.id
      legacyNotContainsResults*.id == jpaNotContainsResults*.id

    cleanup:
      service.queryBackend = 'legacy'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
  }

  void 'Legacy backend raises mapping exception for checklist-request-name isEmpty and isNotEmpty' () {
    given: 'Legacy backend selector'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
      service.queryBackend = 'legacy'

    when: 'Legacy backend evaluates checklist-request-name isEmpty'
      service.lookup(Request, null, 10, 1, [
        "checklists.request.name isEmpty"
      ])

    then: 'Legacy behavior is a mapping exception for this unsupported path/operator combination'
      thrown(MappingException)

    when: 'Legacy backend evaluates checklist-request-name isNotEmpty'
      service.lookup(Request, null, 10, 1, [
        "checklists.request.name isNotEmpty"
      ])

    then: 'Legacy behavior is also a mapping exception for isNotEmpty'
      thrown(MappingException)

    cleanup:
      service.queryBackend = 'legacy'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
  }

  void 'Strict JPA mode fails fast for checklist-request-name isEmpty and isNotEmpty' () {
    given: 'JPA backend is configured without fallback'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = new JpaCriteriaQueryBackend(null)
      service.queryBackend = 'jpa'

    when: 'Strict JPA backend evaluates checklist-request-name isEmpty'
      service.lookup(Request, null, 10, 1, [
        "checklists.request.name isEmpty"
      ])

    then: 'Unsupported operator/path fails fast under strict JPA mode'
      thrown(UnsupportedOperationException)

    when: 'Strict JPA backend evaluates checklist-request-name isNotEmpty'
      service.lookup(Request, null, 10, 1, [
        "checklists.request.name isNotEmpty"
      ])

    then: 'Unsupported operator/path fails fast under strict JPA mode'
      thrown(UnsupportedOperationException)

    cleanup:
      service.queryBackend = 'legacy'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
  }

  void 'JPA backend parity for checklist-request number/date comparison predicates' () {
    given: 'Legacy backend baseline for checklist request numeric/date predicates'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
      service.queryBackend = 'legacy'
      LocalDate today = LocalDate.now()
      String todayIso = DateTimeFormatter.ISO_DATE.format(today)
      List<Request> legacyEqResults = service.lookup(Request, null, 10, 1, [
        "checklists.request.number==2"
      ])
      List<Request> legacyCmpResults = service.lookup(Request, null, 10, 1, [
        "checklists.request.date>${todayIso}"
      ])
      List<Request> legacyRangeResults = service.lookup(Request, null, 10, 1, [
        "1<=checklists.request.number<3"
      ])

    when: 'Same checklist request numeric/date predicates run with JPA backend selector'
      service.queryBackend = 'jpa'
      List<Request> jpaEqResults = service.lookup(Request, null, 10, 1, [
        "checklists.request.number==2"
      ])
      List<Request> jpaCmpResults = service.lookup(Request, null, 10, 1, [
        "checklists.request.date>${todayIso}"
      ])
      List<Request> jpaRangeResults = service.lookup(Request, null, 10, 1, [
        "1<=checklists.request.number<3"
      ])

    then: 'Checklist request number/date parity is preserved'
      legacyEqResults*.id == jpaEqResults*.id
      legacyCmpResults*.id == jpaCmpResults*.id
      legacyRangeResults*.id == jpaRangeResults*.id

    cleanup:
      service.queryBackend = 'legacy'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
  }

  void 'JPA backend parity for checklist-request number/date null and not-equal semantics' () {
    given: 'Legacy backend baseline for checklist request number/date null and not-equal operators'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
      service.queryBackend = 'legacy'
      LocalDate today = LocalDate.now()
      String todayIso = DateTimeFormatter.ISO_DATE.format(today)
      List<Request> legacyNeqResults = service.lookup(Request, null, 10, 1, [
        "checklists.request.number!=2"
      ])
      List<Request> legacyNullResults = service.lookup(Request, null, 10, 1, [
        "checklists.request.date isNull"
      ])
      List<Request> legacyNotNullResults = service.lookup(Request, null, 10, 1, [
        "checklists.request.date isNotNull"
      ])
      List<Request> legacyIsSetResults = service.lookup(Request, null, 10, 1, [
        "checklists.request.number isSet"
      ])
      List<Request> legacyIsNotSetResults = service.lookup(Request, null, 10, 1, [
        "checklists.request.date isNotSet"
      ])
      List<Request> legacyDateNeqResults = service.lookup(Request, null, 10, 1, [
        "checklists.request.date!=${todayIso}"
      ])

    when: 'Same checklist request number/date operators run with JPA backend selector'
      service.queryBackend = 'jpa'
      List<Request> jpaNeqResults = service.lookup(Request, null, 10, 1, [
        "checklists.request.number!=2"
      ])
      List<Request> jpaNullResults = service.lookup(Request, null, 10, 1, [
        "checklists.request.date isNull"
      ])
      List<Request> jpaNotNullResults = service.lookup(Request, null, 10, 1, [
        "checklists.request.date isNotNull"
      ])
      List<Request> jpaIsSetResults = service.lookup(Request, null, 10, 1, [
        "checklists.request.number isSet"
      ])
      List<Request> jpaIsNotSetResults = service.lookup(Request, null, 10, 1, [
        "checklists.request.date isNotSet"
      ])
      List<Request> jpaDateNeqResults = service.lookup(Request, null, 10, 1, [
        "checklists.request.date!=${todayIso}"
      ])

    then: 'Checklist request number/date null and not-equal parity is preserved'
      legacyNeqResults*.id == jpaNeqResults*.id
      legacyNullResults*.id == jpaNullResults*.id
      legacyNotNullResults*.id == jpaNotNullResults*.id
      legacyIsSetResults*.id == jpaIsSetResults*.id
      legacyIsNotSetResults*.id == jpaIsNotSetResults*.id
      legacyDateNeqResults*.id == jpaDateNeqResults*.id

    cleanup:
      service.queryBackend = 'legacy'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
  }

  void 'JPA backend parity for checklist-request number/date contains semantics' () {
    given: 'Legacy backend baseline for checklist request numeric/date contains operators'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
      service.queryBackend = 'legacy'
      LocalDate today = LocalDate.now()
      String todayIso = DateTimeFormatter.ISO_DATE.format(today)
      List<Request> legacyNumberContainsResults = service.lookup(Request, null, 10, 1, [
        "checklists.request.number=~2"
      ])
      List<Request> legacyNumberNotContainsResults = service.lookup(Request, null, 10, 1, [
        "checklists.request.number!~2"
      ])
      List<Request> legacyDateContainsResults = service.lookup(Request, null, 10, 1, [
        "checklists.request.date=~${todayIso}"
      ])
      List<Request> legacyDateNotContainsResults = service.lookup(Request, null, 10, 1, [
        "checklists.request.date!~${todayIso}"
      ])

    when: 'Same checklist request numeric/date contains operators run with JPA backend selector'
      service.queryBackend = 'jpa'
      List<Request> jpaNumberContainsResults = service.lookup(Request, null, 10, 1, [
        "checklists.request.number=~2"
      ])
      List<Request> jpaNumberNotContainsResults = service.lookup(Request, null, 10, 1, [
        "checklists.request.number!~2"
      ])
      List<Request> jpaDateContainsResults = service.lookup(Request, null, 10, 1, [
        "checklists.request.date=~${todayIso}"
      ])
      List<Request> jpaDateNotContainsResults = service.lookup(Request, null, 10, 1, [
        "checklists.request.date!~${todayIso}"
      ])

    then: 'Checklist request numeric/date contains parity is preserved'
      legacyNumberContainsResults*.id == jpaNumberContainsResults*.id
      legacyNumberNotContainsResults*.id == jpaNumberNotContainsResults*.id
      legacyDateContainsResults*.id == jpaDateContainsResults*.id
      legacyDateNotContainsResults*.id == jpaDateNotContainsResults*.id

    cleanup:
      service.queryBackend = 'legacy'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
  }

  void 'JPA backend parity for root-name isSet special operator' () {
    given: 'Legacy backend baseline for root name isSet'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
      service.queryBackend = 'legacy'
      List<Request> legacyResults = service.lookup(Request, null, 10, 1, [
        "name isSet"
      ])

    when: 'Same filter is executed with JPA backend selector'
      service.queryBackend = 'jpa'
      List<Request> jpaResults = service.lookup(Request, null, 10, 1, [
        "name isSet"
      ])

    then: 'Root isSet parity is preserved'
      legacyResults*.id == jpaResults*.id

    cleanup:
      service.queryBackend = 'legacy'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
  }

  void 'JPA backend parity for checklist-name isSet special operator' () {
    given: 'Legacy backend baseline for checklist name isSet'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
      service.queryBackend = 'legacy'
      List<Request> legacyResults = service.lookup(Request, null, 10, 1, [
        "checklists.name isSet"
      ])

    when: 'Same filter is executed with JPA backend selector'
      service.queryBackend = 'jpa'
      List<Request> jpaResults = service.lookup(Request, null, 10, 1, [
        "checklists.name isSet"
      ])

    then: 'Checklist isSet parity is preserved'
      legacyResults*.id == jpaResults*.id

    cleanup:
      service.queryBackend = 'legacy'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
  }

  void 'JPA backend parity for root-name contains operator' () {
    given: 'Legacy backend baseline for root name contains'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
      service.queryBackend = 'legacy'
      List<Request> legacyResults = service.lookup(Request, null, 10, 1, [
        "name=~Request"
      ])

    when: 'Same root contains filter is executed with JPA backend selector'
      service.queryBackend = 'jpa'
      List<Request> jpaResults = service.lookup(Request, null, 10, 1, [
        "name=~Request"
      ])

    then: 'Root contains parity is preserved'
      legacyResults*.id == jpaResults*.id

    cleanup:
      service.queryBackend = 'legacy'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
  }

  void 'JPA backend parity for root-name not-contains operator' () {
    given: 'Legacy backend baseline for root name not-contains'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
      service.queryBackend = 'legacy'
      List<Request> legacyResults = service.lookup(Request, null, 10, 1, [
        "name!~3"
      ])

    when: 'Same root not-contains filter is executed with JPA backend selector'
      service.queryBackend = 'jpa'
      List<Request> jpaResults = service.lookup(Request, null, 10, 1, [
        "name!~3"
      ])

    then: 'Root not-contains parity is preserved'
      legacyResults*.id == jpaResults*.id

    cleanup:
      service.queryBackend = 'legacy'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
  }

  void 'JPA backend parity for mixed root-range and item predicate expression' () {
    given: 'Legacy backend baseline for mixed root and joined predicate conjunction'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
      service.queryBackend = 'legacy'
      List<Request> legacyResults = service.lookup(Request, null, 10, 1, [
        "1<=number<4&&checklists.items.status=i=required"
      ])

    when: 'Same mixed expression is executed with JPA backend selector'
      service.queryBackend = 'jpa'
      List<Request> jpaResults = service.lookup(Request, null, 10, 1, [
        "1<=number<4&&checklists.items.status=i=required"
      ])

    then: 'Mixed expression parity is preserved'
      legacyResults*.id == jpaResults*.id

    cleanup:
      service.queryBackend = 'legacy'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
  }

  void 'JPA backend AST parser parity for mixed root-range and item predicate expression' () {
    given: 'Legacy backend baseline for mixed root and item conjunction'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
      service.queryBackend = 'legacy'
      List<Request> legacyResults = service.lookup(Request, null, 10, 1, [
        "1<=number<4&&checklists.items.status=i=required"
      ])

    when: 'JPA backend uses AST parser for same mixed expression'
      service.jpaCriteriaQueryBackend = new JpaCriteriaQueryBackend(null, true)
      service.queryBackend = 'jpa'
      List<Request> jpaResults = service.lookup(Request, null, 10, 1, [
        "1<=number<4&&checklists.items.status=i=required"
      ])

    then: 'AST path preserves mixed-expression parity'
      legacyResults*.id == jpaResults*.id

    cleanup:
      service.queryBackend = 'legacy'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
  }

  void 'JPA backend parity for negated grouped item predicate expression' () {
    given: 'Legacy backend baseline for negated grouped item expression'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
      service.queryBackend = 'legacy'
      List<Request> legacyEmptyResults = service.lookup(Request, null, 10, 1, [
        "!(checklists.items.outcome==unknown&&checklists.items.status==required)",
        "(checklists.items.outcome=i=yes&&checklists.items.status==required)"
      ])

    when: 'Same negated grouped item expression runs with JPA backend selector'
      service.queryBackend = 'jpa'
      List<Request> jpaEmptyResults = service.lookup(Request, null, 10, 1, [
        "!(checklists.items.outcome==unknown&&checklists.items.status==required)",
        "(checklists.items.outcome=i=yes&&checklists.items.status==required)"
      ])

    then: 'Negated expression parity is preserved'
      legacyEmptyResults*.id == jpaEmptyResults*.id

    cleanup:
      service.queryBackend = 'legacy'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
  }

  void 'JPA backend AST parser parity for negated grouped item predicate expression' () {
    given: 'Legacy backend baseline for negated grouped item expression'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
      service.queryBackend = 'legacy'
      List<Request> legacyResults = service.lookup(Request, null, 10, 1, [
        "!(checklists.items.outcome==unknown&&checklists.items.status==required)",
        "(checklists.items.outcome=i=yes&&checklists.items.status==required)"
      ])

    when: 'JPA backend uses AST parser for same negated grouped expression'
      service.jpaCriteriaQueryBackend = new JpaCriteriaQueryBackend(null, true)
      service.queryBackend = 'jpa'
      List<Request> jpaResults = service.lookup(Request, null, 10, 1, [
        "!(checklists.items.outcome==unknown&&checklists.items.status==required)",
        "(checklists.items.outcome=i=yes&&checklists.items.status==required)"
      ])

    then: 'AST path preserves negated-group parity'
      legacyResults*.id == jpaResults*.id

    cleanup:
      service.queryBackend = 'legacy'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
  }

  void 'JPA backend parity for text search term and matchIn root field' () {
    given: 'Legacy backend baseline for text search with root-field matchIn'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
      service.queryBackend = 'legacy'
      List<Request> legacyResults = service.lookup(Request, 'request', 10, 1, [
        "0<number<3"
      ], ['name'])

    when: 'Same text search is executed with JPA backend selector'
      service.queryBackend = 'jpa'
      List<Request> jpaResults = service.lookup(Request, 'request', 10, 1, [
        "0<number<3"
      ], ['name'])

    then: 'Text-search parity is preserved'
      legacyResults*.id == jpaResults*.id

    cleanup:
      service.queryBackend = 'legacy'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
  }

  void 'JPA backend AST parser parity for text search term and matchIn root field' () {
    given: 'Legacy backend baseline for text search with root-field matchIn'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
      service.queryBackend = 'legacy'
      List<Request> legacyResults = service.lookup(Request, 'request', 10, 1, [
        "0<number<3"
      ], ['name'])

    when: 'JPA backend uses AST parser for same text-search request'
      service.jpaCriteriaQueryBackend = new JpaCriteriaQueryBackend(null, true)
      service.queryBackend = 'jpa'
      List<Request> jpaResults = service.lookup(Request, 'request', 10, 1, [
        "0<number<3"
      ], ['name'])

    then: 'AST path preserves text-search parity'
      legacyResults*.id == jpaResults*.id

    cleanup:
      service.queryBackend = 'legacy'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
  }

  void 'JPA backend does not invoke fallback backend for valid schema-driven matchIn path' () {
    given: 'A JPA backend configured with a fallback mock'
      def fallbackBackend = Mock(SimpleLookupQueryBackend)
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = new JpaCriteriaQueryBackend(fallbackBackend)
      service.queryBackend = 'jpa'

    when: 'Lookup uses a valid deep matchIn path resolved generically from domain metadata'
      List<Request> jpaResults = service.lookup(
        Request,
        'request',
        10,
        1,
        null,
        ['checklists.request.checklists.request.checklists.request.checklists.request.name']
      )

    then: 'JPA backend handles the path natively and fallback is not called'
      0 * fallbackBackend.apply(_, _)
      jpaResults != null
      jpaResults instanceof List

    cleanup:
      service.queryBackend = 'legacy'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
  }

  void 'JPA backend invokes fallback backend for unsupported terminal association matchIn path' () {
    given: 'A JPA backend configured with a fallback mock'
      def fallbackBackend = Mock(SimpleLookupQueryBackend)
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = new JpaCriteriaQueryBackend(fallbackBackend)
      service.queryBackend = 'jpa'

    when: 'Lookup uses a matchIn path that ends in an association instead of a scalar field'
      List<Request> jpaResults = service.lookup(
        Request,
        'request',
        10,
        1,
        null,
        ['checklists.request']
      )

    then: 'JPA backend delegates this unsupported path to fallback'
      1 * fallbackBackend.apply(_, _)
      jpaResults != null
      jpaResults instanceof List

    cleanup:
      service.queryBackend = 'legacy'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
  }

  void 'Strict JPA mode handles valid schema-driven matchIn path without fallback' () {
    given: 'JPA backend is configured without a fallback backend'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = new JpaCriteriaQueryBackend(null)
      service.queryBackend = 'jpa'

    when: 'Lookup uses a valid deep matchIn path'
      List<Request> jpaResults = service.lookup(
        Request,
        'request',
        10,
        1,
        null,
        ['checklists.request.checklists.request.checklists.request.checklists.request.name']
      )

    then: 'Query is handled natively by JPA backend'
      jpaResults != null
      jpaResults instanceof List
      jpaResults*.name.contains('Request 2')

    cleanup:
      service.queryBackend = 'legacy'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
  }

  void 'Strict JPA mode fails fast for unsupported terminal association matchIn path' () {
    given: 'JPA backend is configured without a fallback backend'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = new JpaCriteriaQueryBackend(null)
      service.queryBackend = 'jpa'

    when: 'Lookup uses an unsupported matchIn path ending in an association'
      service.lookup(
        Request,
        'request',
        10,
        1,
        null,
        ['checklists.request']
      )

    then: 'Backend fails fast instead of silently falling back'
      thrown(UnsupportedOperationException)

    cleanup:
      service.queryBackend = 'legacy'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
  }

  void 'JPA backend does not invoke fallback backend for deep schema-driven isNotNull filter path' () {
    given: 'A JPA backend configured with a fallback mock'
      def fallbackBackend = Mock(SimpleLookupQueryBackend)
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = new JpaCriteriaQueryBackend(fallbackBackend)
      service.queryBackend = 'jpa'

    when: 'Lookup uses a deep null-style filter path'
      List<Request> jpaResults = service.lookup(
        Request,
        null,
        10,
        1,
        ['checklists.request.checklists.request.checklists.request.checklists.request.name isNotNull'],
        [],
        []
      )

    then: 'JPA backend handles the filter natively and fallback is not called'
      0 * fallbackBackend.apply(_, _)
      jpaResults != null
      jpaResults instanceof List

    cleanup:
      service.queryBackend = 'legacy'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
  }

  void 'JPA backend does not invoke fallback backend for deep schema-driven equality filter path' () {
    given: 'A JPA backend configured with a fallback mock'
      def fallbackBackend = Mock(SimpleLookupQueryBackend)
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = new JpaCriteriaQueryBackend(fallbackBackend)
      service.queryBackend = 'jpa'

    when: 'Lookup uses a deep equality filter path'
      List<Request> jpaResults = service.lookup(
        Request,
        null,
        10,
        1,
        ['checklists.request.checklists.request.checklists.request.checklists.request.name==Request 2'],
        [],
        []
      )

    then: 'JPA backend handles the filter natively and fallback is not called'
      0 * fallbackBackend.apply(_, _)
      jpaResults != null
      jpaResults instanceof List

    cleanup:
      service.queryBackend = 'legacy'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
  }

  void 'JPA backend invokes fallback backend for unsupported contains operator on non-string filter path' () {
    given: 'A JPA backend configured with a fallback mock'
      def fallbackBackend = Mock(SimpleLookupQueryBackend)
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = new JpaCriteriaQueryBackend(fallbackBackend)
      service.queryBackend = 'jpa'

    when: 'Lookup uses contains operator on a non-string deep path'
      List<Request> jpaResults = service.lookup(
        Request,
        null,
        10,
        1,
        ['checklists.request.checklists.request.number=~2'],
        [],
        []
      )

    then: 'JPA backend delegates to fallback for unsupported type/operator combination'
      1 * fallbackBackend.apply(_, _)
      jpaResults != null
      jpaResults instanceof List

    cleanup:
      service.queryBackend = 'legacy'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
  }

  void 'JPA backend does not invoke fallback backend for deep schema-driven numeric comparison filter path' () {
    given: 'A JPA backend configured with a fallback mock'
      def fallbackBackend = Mock(SimpleLookupQueryBackend)
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = new JpaCriteriaQueryBackend(fallbackBackend)
      service.queryBackend = 'jpa'

    when: 'Lookup uses a deep numeric comparison filter path'
      List<Request> jpaResults = service.lookup(
        Request,
        null,
        10,
        1,
        ['checklists.request.checklists.request.checklists.request.checklists.request.number>1'],
        [],
        []
      )

    then: 'JPA backend handles the numeric comparison natively and fallback is not called'
      0 * fallbackBackend.apply(_, _)
      jpaResults != null
      jpaResults instanceof List

    cleanup:
      service.queryBackend = 'legacy'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
  }

  void 'JPA backend invokes fallback backend for unsupported comparison operator on string filter path' () {
    given: 'A JPA backend configured with a fallback mock'
      def fallbackBackend = Mock(SimpleLookupQueryBackend)
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = new JpaCriteriaQueryBackend(fallbackBackend)
      service.queryBackend = 'jpa'

    when: 'Lookup uses numeric comparator on a string deep path'
      List<Request> jpaResults = service.lookup(
        Request,
        null,
        10,
        1,
        ['checklists.request.checklists.request.checklists.request.checklists.request.name>Request 1'],
        [],
        []
      )

    then: 'JPA backend delegates to fallback for unsupported type/operator combination'
      1 * fallbackBackend.apply(_, _)
      jpaResults != null
      jpaResults instanceof List

    cleanup:
      service.queryBackend = 'legacy'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
  }

  void 'Strict JPA mode handles deep schema-driven numeric comparison filter path without fallback' () {
    given: 'JPA backend is configured without a fallback backend'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = new JpaCriteriaQueryBackend(null)
      service.queryBackend = 'jpa'

    when: 'Lookup uses a deep numeric comparison filter path'
      List<Request> jpaResults = service.lookup(
        Request,
        null,
        10,
        1,
        ['checklists.request.checklists.request.checklists.request.checklists.request.number>1'],
        [],
        []
      )

    then: 'Filter is handled natively by JPA backend'
      jpaResults != null
      jpaResults instanceof List
      jpaResults*.name.contains('Request 2')

    cleanup:
      service.queryBackend = 'legacy'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
  }

  void 'Strict JPA mode fails fast for unsupported comparison operator on string filter path' () {
    given: 'JPA backend is configured without a fallback backend'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = new JpaCriteriaQueryBackend(null)
      service.queryBackend = 'jpa'

    when: 'Lookup uses numeric comparator on a string deep path'
      service.lookup(
        Request,
        null,
        10,
        1,
        ['checklists.request.checklists.request.checklists.request.checklists.request.name>Request 1'],
        [],
        []
      )

    then: 'Backend fails fast instead of silently falling back'
      thrown(UnsupportedOperationException)

    cleanup:
      service.queryBackend = 'legacy'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
  }

  void 'JPA backend parity for quoted text search term and numeric filter' () {
    given: 'Legacy backend baseline for quoted text search with additional numeric filter'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
      service.queryBackend = 'legacy'
      List<Request> legacyResults = service.lookup(Request, '"request 2"', 10, 1, [
        "number>2"
      ], ['name'])

    when: 'Same quoted text search and filter are executed with JPA backend selector'
      service.queryBackend = 'jpa'
      List<Request> jpaResults = service.lookup(Request, '"request 2"', 10, 1, [
        "number>2"
      ], ['name'])

    then: 'Quoted-term search parity is preserved'
      legacyResults*.id == jpaResults*.id

    cleanup:
      service.queryBackend = 'legacy'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
  }

  void 'JPA backend parity for text search term and checklist-name matchIn field' () {
    given: 'Legacy backend baseline for checklist-name matchIn text search'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
      service.queryBackend = 'legacy'
      List<Request> legacyResults = service.lookup(Request, 'list 3', 10, 1, null, ['checklists.name'])

    when: 'Same checklist-name matchIn text search runs with JPA backend selector'
      service.queryBackend = 'jpa'
      List<Request> jpaResults = service.lookup(Request, 'list 3', 10, 1, null, ['checklists.name'])

    then: 'Checklist-name text-search parity is preserved'
      legacyResults*.id == jpaResults*.id

    cleanup:
      service.queryBackend = 'legacy'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
  }

  void 'JPA backend parity for text search term and item-status matchIn field with filter' () {
    given: 'Legacy backend baseline for item-status matchIn text search and checklist filter'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
      service.queryBackend = 'legacy'
      List<Request> legacyResults = service.lookup(Request, 'required', 10, 1, [
        "checklists.name==list_3"
      ], ['checklists.items.status'])

    when: 'Same item-status matchIn text search and checklist filter run with JPA backend selector'
      service.queryBackend = 'jpa'
      List<Request> jpaResults = service.lookup(Request, 'required', 10, 1, [
        "checklists.name==list_3"
      ], ['checklists.items.status'])

    then: 'Item-status text-search parity is preserved'
      legacyResults*.id == jpaResults*.id

    cleanup:
      service.queryBackend = 'legacy'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
  }

  void 'JPA backend parity for text search term and checklist-request-date matchIn field' () {
    given: 'Legacy backend baseline for checklist-request-date matchIn text search'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
      service.queryBackend = 'legacy'
      final String dateTerm = LocalDate.now().toString()
      List<Request> legacyResults = service.lookup(Request, dateTerm, 10, 1, null, ['checklists.request.date'])

    when: 'Same checklist-request-date matchIn text search runs with JPA backend selector'
      service.queryBackend = 'jpa'
      List<Request> jpaResults = service.lookup(Request, dateTerm, 10, 1, null, ['checklists.request.date'])

    then: 'Checklist-request-date text-search parity is preserved'
      legacyResults*.id == jpaResults*.id

    cleanup:
      service.queryBackend = 'legacy'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
  }

  void 'JPA backend parity for deep non-string item-checklist-request-date matchIn field' () {
    given: 'Legacy backend baseline for deep non-string date matchIn text search'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
      service.queryBackend = 'legacy'
      final String dateTerm = LocalDate.now().toString()
      List<Request> legacyResults = service.lookup(Request, dateTerm, 10, 1, null, ['checklists.items.checklist.request.date'])

    when: 'Same deep non-string date matchIn text search runs with JPA backend selector'
      service.queryBackend = 'jpa'
      List<Request> jpaResults = service.lookup(Request, dateTerm, 10, 1, null, ['checklists.items.checklist.request.date'])

    then: 'Deep non-string date text-search parity is preserved'
      legacyResults*.id == jpaResults*.id

    cleanup:
      service.queryBackend = 'legacy'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
  }

  void 'JPA backend parity for text search term and checklist-request-name matchIn field' () {
    given: 'Legacy backend baseline for checklist-request-name matchIn text search'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
      service.queryBackend = 'legacy'
      List<Request> legacyResults = service.lookup(Request, 'request 2', 10, 1, null, ['checklists.request.name'])

    when: 'Same checklist-request-name matchIn text search runs with JPA backend selector'
      service.queryBackend = 'jpa'
      List<Request> jpaResults = service.lookup(Request, 'request 2', 10, 1, null, ['checklists.request.name'])

    then: 'Checklist-request-name text-search parity is preserved'
      legacyResults*.id == jpaResults*.id

    cleanup:
      service.queryBackend = 'legacy'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
  }

  void 'JPA backend parity for text search term and checklist-request-number matchIn field' () {
    given: 'Legacy backend baseline for checklist-request-number matchIn text search'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
      service.queryBackend = 'legacy'
      List<Request> legacyResults = service.lookup(Request, '2', 10, 1, null, ['checklists.request.number'])

    when: 'Same checklist-request-number matchIn text search runs with JPA backend selector'
      service.queryBackend = 'jpa'
      List<Request> jpaResults = service.lookup(Request, '2', 10, 1, null, ['checklists.request.number'])

    then: 'Checklist-request-number text-search parity is preserved'
      legacyResults*.id == jpaResults*.id

    cleanup:
      service.queryBackend = 'legacy'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
  }

  void 'JPA backend parity for text search term and checklist-request-checklists-name matchIn field' () {
    given: 'Legacy backend baseline for recursive checklist-request-checklists-name matchIn text search'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
      service.queryBackend = 'legacy'
      List<Request> legacyResults = service.lookup(Request, 'list_3', 10, 1, null, ['checklists.request.checklists.name'])

    when: 'Same recursive checklist-request-checklists-name matchIn text search runs with JPA backend selector'
      service.queryBackend = 'jpa'
      List<Request> jpaResults = service.lookup(Request, 'list_3', 10, 1, null, ['checklists.request.checklists.name'])

    then: 'Recursive checklist-request-checklists-name text-search parity is preserved'
      legacyResults*.id == jpaResults*.id

    cleanup:
      service.queryBackend = 'legacy'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
  }

  void 'JPA backend parity for text search term and checklist-request-checklists-request-name matchIn field' () {
    given: 'Legacy backend baseline for recursive checklist-request-checklists-request-name matchIn text search'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
      service.queryBackend = 'legacy'
      List<Request> legacyResults = service.lookup(Request, 'request 2', 10, 1, null, ['checklists.request.checklists.request.name'])

    when: 'Same recursive checklist-request-checklists-request-name matchIn text search runs with JPA backend selector'
      service.queryBackend = 'jpa'
      List<Request> jpaResults = service.lookup(Request, 'request 2', 10, 1, null, ['checklists.request.checklists.request.name'])

    then: 'Recursive checklist-request-checklists-request-name text-search parity is preserved'
      legacyResults*.id == jpaResults*.id

    cleanup:
      service.queryBackend = 'legacy'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
  }

  void 'JPA backend parity for text search term and checklist-request-checklists-request-number matchIn field' () {
    given: 'Legacy backend baseline for recursive checklist-request-checklists-request-number matchIn text search'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
      service.queryBackend = 'legacy'
      List<Request> legacyResults = service.lookup(Request, '2', 10, 1, null, ['checklists.request.checklists.request.number'])

    when: 'Same recursive checklist-request-checklists-request-number matchIn text search runs with JPA backend selector'
      service.queryBackend = 'jpa'
      List<Request> jpaResults = service.lookup(Request, '2', 10, 1, null, ['checklists.request.checklists.request.number'])

    then: 'Recursive checklist-request-checklists-request-number text-search parity is preserved'
      legacyResults*.id == jpaResults*.id

    cleanup:
      service.queryBackend = 'legacy'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
  }

  void 'JPA backend parity for text search term and checklist-request-checklists-request-date matchIn field' () {
    given: 'Legacy backend baseline for recursive checklist-request-checklists-request-date matchIn text search'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
      service.queryBackend = 'legacy'
      String dateTerm = DateTimeFormatter.ISO_DATE.format(LocalDate.now())
      List<Request> legacyResults = service.lookup(Request, dateTerm, 10, 1, null, ['checklists.request.checklists.request.date'])

    when: 'Same recursive checklist-request-checklists-request-date matchIn text search runs with JPA backend selector'
      service.queryBackend = 'jpa'
      List<Request> jpaResults = service.lookup(Request, dateTerm, 10, 1, null, ['checklists.request.checklists.request.date'])

    then: 'Recursive checklist-request-checklists-request-date text-search parity is preserved'
      legacyResults*.id == jpaResults*.id

    cleanup:
      service.queryBackend = 'legacy'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
  }

  void 'JPA backend parity for text search term and checklist-request-checklists-request-checklists-name matchIn field' () {
    given: 'Legacy backend baseline for recursive checklist-request-checklists-request-checklists-name matchIn text search'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
      service.queryBackend = 'legacy'
      List<Request> legacyResults = service.lookup(Request, 'list_3', 10, 1, null, ['checklists.request.checklists.request.checklists.name'])

    when: 'Same recursive checklist-request-checklists-request-checklists-name matchIn text search runs with JPA backend selector'
      service.queryBackend = 'jpa'
      List<Request> jpaResults = service.lookup(Request, 'list_3', 10, 1, null, ['checklists.request.checklists.request.checklists.name'])

    then: 'Recursive checklist-request-checklists-request-checklists-name text-search parity is preserved'
      legacyResults*.id == jpaResults*.id

    cleanup:
      service.queryBackend = 'legacy'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
  }

  void 'JPA backend parity for text search term and checklist-request-checklists-request-checklists-request-name matchIn field' () {
    given: 'Legacy backend baseline for recursive checklist-request-checklists-request-checklists-request-name matchIn text search'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
      service.queryBackend = 'legacy'
      List<Request> legacyResults = service.lookup(Request, 'request 2', 10, 1, null, ['checklists.request.checklists.request.checklists.request.name'])

    when: 'Same recursive checklist-request-checklists-request-checklists-request-name matchIn text search runs with JPA backend selector'
      service.queryBackend = 'jpa'
      List<Request> jpaResults = service.lookup(Request, 'request 2', 10, 1, null, ['checklists.request.checklists.request.checklists.request.name'])

    then: 'Recursive checklist-request-checklists-request-checklists-request-name text-search parity is preserved'
      legacyResults*.id == jpaResults*.id

    cleanup:
      service.queryBackend = 'legacy'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
  }

  void 'JPA backend parity for text search term and checklist-request-checklists-request-checklists-request-number matchIn field' () {
    given: 'Legacy backend baseline for recursive checklist-request-checklists-request-checklists-request-number matchIn text search'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
      service.queryBackend = 'legacy'
      List<Request> legacyResults = service.lookup(Request, '2', 10, 1, null, ['checklists.request.checklists.request.checklists.request.number'])

    when: 'Same recursive checklist-request-checklists-request-checklists-request-number matchIn text search runs with JPA backend selector'
      service.queryBackend = 'jpa'
      List<Request> jpaResults = service.lookup(Request, '2', 10, 1, null, ['checklists.request.checklists.request.checklists.request.number'])

    then: 'Recursive checklist-request-checklists-request-checklists-request-number text-search parity is preserved'
      legacyResults*.id == jpaResults*.id

    cleanup:
      service.queryBackend = 'legacy'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
  }

  void 'JPA backend parity for text search term and checklist-request-checklists-request-checklists-request-date matchIn field' () {
    given: 'Legacy backend baseline for recursive checklist-request-checklists-request-checklists-request-date matchIn text search'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
      service.queryBackend = 'legacy'
      String dateTerm = DateTimeFormatter.ISO_DATE.format(LocalDate.now())
      List<Request> legacyResults = service.lookup(Request, dateTerm, 10, 1, null, ['checklists.request.checklists.request.checklists.request.date'])

    when: 'Same recursive checklist-request-checklists-request-checklists-request-date matchIn text search runs with JPA backend selector'
      service.queryBackend = 'jpa'
      List<Request> jpaResults = service.lookup(Request, dateTerm, 10, 1, null, ['checklists.request.checklists.request.checklists.request.date'])

    then: 'Recursive checklist-request-checklists-request-checklists-request-date text-search parity is preserved'
      legacyResults*.id == jpaResults*.id

    cleanup:
      service.queryBackend = 'legacy'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
  }

  void 'JPA backend parity for text search term and checklist-request-checklists-request-checklists-request-checklists-name matchIn field' () {
    given: 'Legacy backend baseline for recursive checklist-request-checklists-request-checklists-request-checklists-name matchIn text search'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
      service.queryBackend = 'legacy'
      List<Request> legacyResults = service.lookup(Request, 'list_3', 10, 1, null, ['checklists.request.checklists.request.checklists.request.checklists.name'])

    when: 'Same recursive checklist-request-checklists-request-checklists-request-checklists-name matchIn text search runs with JPA backend selector'
      service.queryBackend = 'jpa'
      List<Request> jpaResults = service.lookup(Request, 'list_3', 10, 1, null, ['checklists.request.checklists.request.checklists.request.checklists.name'])

    then: 'Recursive checklist-request-checklists-request-checklists-request-checklists-name text-search parity is preserved'
      legacyResults*.id == jpaResults*.id

    cleanup:
      service.queryBackend = 'legacy'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
  }

  void 'JPA backend parity for text search term and checklist-request-checklists-request-checklists-request-checklists-items-status matchIn field' () {
    given: 'Legacy backend baseline for recursive checklist-request-checklists-request-checklists-request-checklists-items-status matchIn text search'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
      service.queryBackend = 'legacy'
      List<Request> legacyResults = service.lookup(Request, 'required', 10, 1, null, ['checklists.request.checklists.request.checklists.request.checklists.items.status'])

    when: 'Same recursive checklist-request-checklists-request-checklists-request-checklists-items-status matchIn text search runs with JPA backend selector'
      service.queryBackend = 'jpa'
      List<Request> jpaResults = service.lookup(Request, 'required', 10, 1, null, ['checklists.request.checklists.request.checklists.request.checklists.items.status'])

    then: 'Recursive checklist-request-checklists-request-checklists-request-checklists-items-status text-search parity is preserved'
      legacyResults*.id == jpaResults*.id

    cleanup:
      service.queryBackend = 'legacy'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
  }

  void 'JPA backend parity for text search term and checklist-request-checklists-request-checklists-request-checklists-items-outcome matchIn field' () {
    given: 'Legacy backend baseline for recursive checklist-request-checklists-request-checklists-request-checklists-items-outcome matchIn text search'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
      service.queryBackend = 'legacy'
      List<Request> legacyResults = service.lookup(Request, 'unknown', 10, 1, null, ['checklists.request.checklists.request.checklists.request.checklists.items.outcome'])

    when: 'Same recursive checklist-request-checklists-request-checklists-request-checklists-items-outcome matchIn text search runs with JPA backend selector'
      service.queryBackend = 'jpa'
      List<Request> jpaResults = service.lookup(Request, 'unknown', 10, 1, null, ['checklists.request.checklists.request.checklists.request.checklists.items.outcome'])

    then: 'Recursive checklist-request-checklists-request-checklists-request-checklists-items-outcome text-search parity is preserved'
      legacyResults*.id == jpaResults*.id

    cleanup:
      service.queryBackend = 'legacy'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
  }

  void 'JPA backend parity for text search term and checklist-request-checklists-request-checklists-items-status matchIn field' () {
    given: 'Legacy backend baseline for recursive checklist-request-checklists-request-checklists-items-status matchIn text search'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
      service.queryBackend = 'legacy'
      List<Request> legacyResults = service.lookup(Request, 'required', 10, 1, null, ['checklists.request.checklists.request.checklists.items.status'])

    when: 'Same recursive checklist-request-checklists-request-checklists-items-status matchIn text search runs with JPA backend selector'
      service.queryBackend = 'jpa'
      List<Request> jpaResults = service.lookup(Request, 'required', 10, 1, null, ['checklists.request.checklists.request.checklists.items.status'])

    then: 'Recursive checklist-request-checklists-request-checklists-items-status text-search parity is preserved'
      legacyResults*.id == jpaResults*.id

    cleanup:
      service.queryBackend = 'legacy'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
  }

  void 'JPA backend parity for text search term and checklist-request-checklists-request-checklists-items-outcome matchIn field' () {
    given: 'Legacy backend baseline for recursive checklist-request-checklists-request-checklists-items-outcome matchIn text search'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
      service.queryBackend = 'legacy'
      List<Request> legacyResults = service.lookup(Request, 'unknown', 10, 1, null, ['checklists.request.checklists.request.checklists.items.outcome'])

    when: 'Same recursive checklist-request-checklists-request-checklists-items-outcome matchIn text search runs with JPA backend selector'
      service.queryBackend = 'jpa'
      List<Request> jpaResults = service.lookup(Request, 'unknown', 10, 1, null, ['checklists.request.checklists.request.checklists.items.outcome'])

    then: 'Recursive checklist-request-checklists-request-checklists-items-outcome text-search parity is preserved'
      legacyResults*.id == jpaResults*.id

    cleanup:
      service.queryBackend = 'legacy'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
  }

  void 'JPA backend parity for text search term and checklist-request-checklists-items-status matchIn field' () {
    given: 'Legacy backend baseline for recursive checklist-request-checklists-items-status matchIn text search'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
      service.queryBackend = 'legacy'
      List<Request> legacyResults = service.lookup(Request, 'required', 10, 1, null, ['checklists.request.checklists.items.status'])

    when: 'Same recursive checklist-request-checklists-items-status matchIn text search runs with JPA backend selector'
      service.queryBackend = 'jpa'
      List<Request> jpaResults = service.lookup(Request, 'required', 10, 1, null, ['checklists.request.checklists.items.status'])

    then: 'Recursive checklist-request-checklists-items-status text-search parity is preserved'
      legacyResults*.id == jpaResults*.id

    cleanup:
      service.queryBackend = 'legacy'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
  }

  void 'JPA backend parity for text search term and checklist-request-checklists-items-outcome matchIn field' () {
    given: 'Legacy backend baseline for recursive checklist-request-checklists-items-outcome matchIn text search'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
      service.queryBackend = 'legacy'
      List<Request> legacyResults = service.lookup(Request, 'unknown', 10, 1, null, ['checklists.request.checklists.items.outcome'])

    when: 'Same recursive checklist-request-checklists-items-outcome matchIn text search runs with JPA backend selector'
      service.queryBackend = 'jpa'
      List<Request> jpaResults = service.lookup(Request, 'unknown', 10, 1, null, ['checklists.request.checklists.items.outcome'])

    then: 'Recursive checklist-request-checklists-items-outcome text-search parity is preserved'
      legacyResults*.id == jpaResults*.id

    cleanup:
      service.queryBackend = 'legacy'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
  }

  void 'JPA backend parity for text search term and deep item-checklist-request-name matchIn field' () {
    given: 'Legacy backend baseline for deep item->checklist->request name matchIn text search'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
      service.queryBackend = 'legacy'
      List<Request> legacyResults = service.lookup(Request, 'request 2', 10, 1, null, ['checklists.items.checklist.request.name'])

    when: 'Same deep matchIn text search runs with JPA backend selector'
      service.queryBackend = 'jpa'
      List<Request> jpaResults = service.lookup(Request, 'request 2', 10, 1, null, ['checklists.items.checklist.request.name'])

    then: 'Deep item->checklist->request name text-search parity is preserved'
      legacyResults*.id == jpaResults*.id

    cleanup:
      service.queryBackend = 'legacy'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
  }

  void 'JPA backend parity for text search term and deep item-checklist-name matchIn field' () {
    given: 'Legacy backend baseline for deep item->checklist name matchIn text search'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
      service.queryBackend = 'legacy'
      List<Request> legacyResults = service.lookup(Request, 'list_3', 10, 1, null, ['checklists.items.checklist.name'])

    when: 'Same deep checklist-name matchIn text search runs with JPA backend selector'
      service.queryBackend = 'jpa'
      List<Request> jpaResults = service.lookup(Request, 'list_3', 10, 1, null, ['checklists.items.checklist.name'])

    then: 'Deep item->checklist name text-search parity is preserved'
      legacyResults*.id == jpaResults*.id

    cleanup:
      service.queryBackend = 'legacy'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
  }

  void 'JPA backend parity for text search term and deep item-checklist-request-number matchIn field' () {
    given: 'Legacy backend baseline for deep item->checklist->request number matchIn text search'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
      service.queryBackend = 'legacy'
      List<Request> legacyResults = service.lookup(Request, '2', 10, 1, null, ['checklists.items.checklist.request.number'])

    when: 'Same deep numeric matchIn text search runs with JPA backend selector'
      service.queryBackend = 'jpa'
      List<Request> jpaResults = service.lookup(Request, '2', 10, 1, null, ['checklists.items.checklist.request.number'])

    then: 'Deep item->checklist->request number text-search parity is preserved'
      legacyResults*.id == jpaResults*.id

    cleanup:
      service.queryBackend = 'legacy'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
  }

  void 'JPA backend parity for recursive checklist-name matchIn path' () {
    given: 'Legacy backend baseline for recursive deep checklist-name matchIn text search'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
      service.queryBackend = 'legacy'
      List<Request> legacyResults = service.lookup(Request, 'list_3', 10, 1, null, ['checklists.items.checklist.request.checklists.name'])

    when: 'Same recursive deep checklist-name matchIn text search runs with JPA backend selector'
      service.queryBackend = 'jpa'
      List<Request> jpaResults = service.lookup(Request, 'list_3', 10, 1, null, ['checklists.items.checklist.request.checklists.name'])

    then: 'Recursive checklist-name path text-search parity is preserved'
      legacyResults*.id == jpaResults*.id

    cleanup:
      service.queryBackend = 'legacy'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
  }

  void 'JPA backend parity for deep recursive checklist-request-name matchIn path' () {
    given: 'Legacy backend baseline for recursive deep checklist-request-name matchIn text search'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
      service.queryBackend = 'legacy'
      List<Request> legacyResults = service.lookup(Request, 'request 2', 10, 1, null, ['checklists.items.checklist.request.checklists.request.name'])

    when: 'Same recursive deep checklist-request-name matchIn text search runs with JPA backend selector'
      service.queryBackend = 'jpa'
      List<Request> jpaResults = service.lookup(Request, 'request 2', 10, 1, null, ['checklists.items.checklist.request.checklists.request.name'])

    then: 'Recursive checklist-request-name path text-search parity is preserved'
      legacyResults*.id == jpaResults*.id

    cleanup:
      service.queryBackend = 'legacy'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
  }

  void 'JPA backend parity for deep recursive checklist-request-number matchIn path' () {
    given: 'Legacy backend baseline for recursive deep checklist-request-number matchIn text search'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
      service.queryBackend = 'legacy'
      List<Request> legacyResults = service.lookup(Request, '2', 10, 1, null, ['checklists.items.checklist.request.checklists.request.number'])

    when: 'Same recursive deep checklist-request-number matchIn text search runs with JPA backend selector'
      service.queryBackend = 'jpa'
      List<Request> jpaResults = service.lookup(Request, '2', 10, 1, null, ['checklists.items.checklist.request.checklists.request.number'])

    then: 'Recursive checklist-request-number path text-search parity is preserved'
      legacyResults*.id == jpaResults*.id

    cleanup:
      service.queryBackend = 'legacy'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
  }

  void 'JPA backend parity for deep recursive checklist-request-date matchIn path' () {
    given: 'Legacy backend baseline for recursive deep checklist-request-date matchIn text search'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
      service.queryBackend = 'legacy'
      String dateTerm = DateTimeFormatter.ISO_DATE.format(LocalDate.now())
      List<Request> legacyResults = service.lookup(Request, dateTerm, 10, 1, null, ['checklists.items.checklist.request.checklists.request.date'])

    when: 'Same recursive deep checklist-request-date matchIn text search runs with JPA backend selector'
      service.queryBackend = 'jpa'
      List<Request> jpaResults = service.lookup(Request, dateTerm, 10, 1, null, ['checklists.items.checklist.request.checklists.request.date'])

    then: 'Recursive checklist-request-date path text-search parity is preserved'
      legacyResults*.id == jpaResults*.id

    cleanup:
      service.queryBackend = 'legacy'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
  }

  void 'JPA backend parity for deep recursive checklist-request-checklists-items-status matchIn path' () {
    given: 'Legacy backend baseline for recursive deep checklist-request-checklists-items-status matchIn text search'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
      service.queryBackend = 'legacy'
      List<Request> legacyResults = service.lookup(Request, 'required', 10, 1, null, ['checklists.items.checklist.request.checklists.request.checklists.items.status'])

    when: 'Same recursive deep checklist-request-checklists-items-status matchIn text search runs with JPA backend selector'
      service.queryBackend = 'jpa'
      List<Request> jpaResults = service.lookup(Request, 'required', 10, 1, null, ['checklists.items.checklist.request.checklists.request.checklists.items.status'])

    then: 'Recursive deep checklist-request-checklists-items-status path text-search parity is preserved'
      legacyResults*.id == jpaResults*.id

    cleanup:
      service.queryBackend = 'legacy'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
  }

  void 'JPA backend parity for deep recursive checklist-request-checklists-items-outcome matchIn path' () {
    given: 'Legacy backend baseline for recursive deep checklist-request-checklists-items-outcome matchIn text search'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
      service.queryBackend = 'legacy'
      List<Request> legacyResults = service.lookup(Request, 'unknown', 10, 1, null, ['checklists.items.checklist.request.checklists.request.checklists.items.outcome'])

    when: 'Same recursive deep checklist-request-checklists-items-outcome matchIn text search runs with JPA backend selector'
      service.queryBackend = 'jpa'
      List<Request> jpaResults = service.lookup(Request, 'unknown', 10, 1, null, ['checklists.items.checklist.request.checklists.request.checklists.items.outcome'])

    then: 'Recursive deep checklist-request-checklists-items-outcome path text-search parity is preserved'
      legacyResults*.id == jpaResults*.id

    cleanup:
      service.queryBackend = 'legacy'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
  }

  void 'JPA backend parity for deep recursive checklist-request-checklists-request-name matchIn path' () {
    given: 'Legacy backend baseline for recursive deep checklist-request-checklists-request-name matchIn text search'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
      service.queryBackend = 'legacy'
      List<Request> legacyResults = service.lookup(Request, 'request 2', 10, 1, null, ['checklists.items.checklist.request.checklists.request.checklists.request.name'])

    when: 'Same recursive deep checklist-request-checklists-request-name matchIn text search runs with JPA backend selector'
      service.queryBackend = 'jpa'
      List<Request> jpaResults = service.lookup(Request, 'request 2', 10, 1, null, ['checklists.items.checklist.request.checklists.request.checklists.request.name'])

    then: 'Recursive deep checklist-request-checklists-request-name path text-search parity is preserved'
      legacyResults*.id == jpaResults*.id

    cleanup:
      service.queryBackend = 'legacy'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
  }

  void 'JPA backend parity for deep recursive checklist-request-checklists-request-number matchIn path' () {
    given: 'Legacy backend baseline for recursive deep checklist-request-checklists-request-number matchIn text search'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
      service.queryBackend = 'legacy'
      List<Request> legacyResults = service.lookup(Request, '2', 10, 1, null, ['checklists.items.checklist.request.checklists.request.checklists.request.number'])

    when: 'Same recursive deep checklist-request-checklists-request-number matchIn text search runs with JPA backend selector'
      service.queryBackend = 'jpa'
      List<Request> jpaResults = service.lookup(Request, '2', 10, 1, null, ['checklists.items.checklist.request.checklists.request.checklists.request.number'])

    then: 'Recursive deep checklist-request-checklists-request-number path text-search parity is preserved'
      legacyResults*.id == jpaResults*.id

    cleanup:
      service.queryBackend = 'legacy'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
  }

  void 'JPA backend parity for deep recursive checklist-request-checklists-request-date matchIn path' () {
    given: 'Legacy backend baseline for recursive deep checklist-request-checklists-request-date matchIn text search'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
      service.queryBackend = 'legacy'
      String dateTerm = DateTimeFormatter.ISO_DATE.format(LocalDate.now())
      List<Request> legacyResults = service.lookup(Request, dateTerm, 10, 1, null, ['checklists.items.checklist.request.checklists.request.checklists.request.date'])

    when: 'Same recursive deep checklist-request-checklists-request-date matchIn text search runs with JPA backend selector'
      service.queryBackend = 'jpa'
      List<Request> jpaResults = service.lookup(Request, dateTerm, 10, 1, null, ['checklists.items.checklist.request.checklists.request.checklists.request.date'])

    then: 'Recursive deep checklist-request-checklists-request-date path text-search parity is preserved'
      legacyResults*.id == jpaResults*.id

    cleanup:
      service.queryBackend = 'legacy'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
  }

  void 'JPA backend parity for deep recursive checklist-request-checklists-request-checklists-name matchIn path' () {
    given: 'Legacy backend baseline for recursive deep checklist-request-checklists-request-checklists-name matchIn text search'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
      service.queryBackend = 'legacy'
      List<Request> legacyResults = service.lookup(Request, 'list_3', 10, 1, null, ['checklists.items.checklist.request.checklists.request.checklists.request.checklists.name'])

    when: 'Same recursive deep checklist-request-checklists-request-checklists-name matchIn text search runs with JPA backend selector'
      service.queryBackend = 'jpa'
      List<Request> jpaResults = service.lookup(Request, 'list_3', 10, 1, null, ['checklists.items.checklist.request.checklists.request.checklists.request.checklists.name'])

    then: 'Recursive deep checklist-request-checklists-request-checklists-name path text-search parity is preserved'
      legacyResults*.id == jpaResults*.id

    cleanup:
      service.queryBackend = 'legacy'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
  }

  void 'JPA backend parity for deep recursive checklist-request-checklists-request-checklists-items-status matchIn path' () {
    given: 'Legacy backend baseline for recursive deep checklist-request-checklists-request-checklists-items-status matchIn text search'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
      service.queryBackend = 'legacy'
      List<Request> legacyResults = service.lookup(Request, 'required', 10, 1, null, ['checklists.items.checklist.request.checklists.request.checklists.request.checklists.items.status'])

    when: 'Same recursive deep checklist-request-checklists-request-checklists-items-status matchIn text search runs with JPA backend selector'
      service.queryBackend = 'jpa'
      List<Request> jpaResults = service.lookup(Request, 'required', 10, 1, null, ['checklists.items.checklist.request.checklists.request.checklists.request.checklists.items.status'])

    then: 'Recursive deep checklist-request-checklists-request-checklists-items-status path text-search parity is preserved'
      legacyResults*.id == jpaResults*.id

    cleanup:
      service.queryBackend = 'legacy'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
  }

  void 'JPA backend parity for deep recursive checklist-request-checklists-request-checklists-items-outcome matchIn path' () {
    given: 'Legacy backend baseline for recursive deep checklist-request-checklists-request-checklists-items-outcome matchIn text search'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
      service.queryBackend = 'legacy'
      List<Request> legacyResults = service.lookup(Request, 'unknown', 10, 1, null, ['checklists.items.checklist.request.checklists.request.checklists.request.checklists.items.outcome'])

    when: 'Same recursive deep checklist-request-checklists-request-checklists-items-outcome matchIn text search runs with JPA backend selector'
      service.queryBackend = 'jpa'
      List<Request> jpaResults = service.lookup(Request, 'unknown', 10, 1, null, ['checklists.items.checklist.request.checklists.request.checklists.request.checklists.items.outcome'])

    then: 'Recursive deep checklist-request-checklists-request-checklists-items-outcome path text-search parity is preserved'
      legacyResults*.id == jpaResults*.id

    cleanup:
      service.queryBackend = 'legacy'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
  }

  void 'JPA backend parity for deep recursive checklist-items-status matchIn path' () {
    given: 'Legacy backend baseline for recursive deep checklist-items-status matchIn text search'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
      service.queryBackend = 'legacy'
      List<Request> legacyResults = service.lookup(Request, 'required', 10, 1, null, ['checklists.items.checklist.request.checklists.items.status'])

    when: 'Same recursive deep checklist-items-status matchIn text search runs with JPA backend selector'
      service.queryBackend = 'jpa'
      List<Request> jpaResults = service.lookup(Request, 'required', 10, 1, null, ['checklists.items.checklist.request.checklists.items.status'])

    then: 'Recursive checklist-items-status path text-search parity is preserved'
      legacyResults*.id == jpaResults*.id

    cleanup:
      service.queryBackend = 'legacy'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
  }

  void 'JPA backend parity for deep recursive checklist-items-outcome matchIn path' () {
    given: 'Legacy backend baseline for recursive deep checklist-items-outcome matchIn text search'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
      service.queryBackend = 'legacy'
      List<Request> legacyResults = service.lookup(Request, 'unknown', 10, 1, null, ['checklists.items.checklist.request.checklists.items.outcome'])

    when: 'Same recursive deep checklist-items-outcome matchIn text search runs with JPA backend selector'
      service.queryBackend = 'jpa'
      List<Request> jpaResults = service.lookup(Request, 'unknown', 10, 1, null, ['checklists.items.checklist.request.checklists.items.outcome'])

    then: 'Recursive checklist-items-outcome path text-search parity is preserved'
      legacyResults*.id == jpaResults*.id

    cleanup:
      service.queryBackend = 'legacy'
      service.simpleLookupQueryBackend = null
      service.jpaCriteriaQueryBackend = null
  }

  void 'Raw criteria alias chain resolves nested association property' () {
    when: 'A direct criteria query uses chained aliases for nested associations'
      List<Request> requests = Request.createCriteria().list {
        createAlias('checklists', 'cl')
        createAlias('cl.items', 'cli')
        eq('cli.outcome', 'yes')
      } as List<Request>

    then: 'Alias-based property filter resolves and returns expected result'
      requests.size() == 1
      requests[0].name == 'Request 3'
  }

  void 'Raw criteria add(Criterion) with alias chain resolves nested association property' () {
    when: 'A direct criteria query adds a Criterion after creating nested aliases'
      List<Request> requests = Request.createCriteria().list {
        createAlias('checklists', 'cl')
        createAlias('cl.items', 'cli')
        add(Restrictions.eq('cli.outcome', 'yes'))
      } as List<Request>

    then: 'Criterion-based alias filter resolves and returns expected result'
      requests.size() == 1
      requests[0].name == 'Request 3'
  }

  void 'Raw criteria using service-style alias names resolves nested association property' () {
    when: 'The alias names match SimpleLookupService naming convention'
      List<Request> requests = Request.createCriteria().list {
        createAlias('checklists', 'alias1x0')
        createAlias('alias1x0.items', 'alias1x1')
        add(Restrictions.eq('alias1x1.outcome', 'yes'))
      } as List<Request>

    then: 'Alias naming style itself is valid'
      requests.size() == 1
      requests[0].name == 'Request 3'
  }

  void 'Raw criteria with conjunction wrapper resolves nested association property' () {
    when: 'A single criterion is wrapped in a conjunction as in SimpleLookupService'
      List<Request> requests = Request.createCriteria().list {
        createAlias('checklists', 'alias1x0')
        createAlias('alias1x0.items', 'alias1x1')
        add(Restrictions.and(Restrictions.eq('alias1x1.outcome', 'yes')))
      } as List<Request>

    then: 'Conjunction wrapper does not break alias-based filtering'
      requests.size() == 1
      requests[0].name == 'Request 3'
  }

  void 'Raw criteria eqOrIsNull with alias chain resolves nested association property' () {
    when: 'A criterion uses eqOrIsNull as SimpleLookupService does for =='
      List<Request> requests = Request.createCriteria().list {
        createAlias('checklists', 'alias1x0')
        createAlias('alias1x0.items', 'alias1x1')
        add(Restrictions.eqOrIsNull('alias1x1.outcome', 'yes'))
      } as List<Request>

    then: 'eqOrIsNull does not break alias-based filtering'
      requests.size() == 1
      requests[0].name == 'Request 3'
  }

  void 'Raw paged criteria with alias chain resolves nested association property' () {
    when: 'A paged criteria query uses the same alias pattern as the service'
      def requests = Request.createCriteria().list([max: 10, offset: 0]) {
        createAlias('checklists', 'alias1x0')
        createAlias('alias1x0.items', 'alias1x1')
        add(Restrictions.eqOrIsNull('alias1x1.outcome', 'yes'))
      }

    then: 'Paged criteria preserves alias-based filtering'
      requests.size() == 1
      requests[0].name == 'Request 3'
      requests.totalCount == 1
  }
  
  void 'Find requests where check list item has outcome set to "yes"' () {
    
    when: 'Filter by value case-sensitively'
      List<Request> requests = service.lookup(Request, null, 10, 1, [
        "checklists.items.outcome==yes"
      ])
    
    then: '1 result'
      requests.size() == 1
    
    when: 'Filter by value case-insensitively'
      requests = service.lookup(Request, null, 10, 1, [
        "checklists.items.outcome=i=yes"
      ])
      
    then: '2 results'
      requests.size() == 2
  }
  
  void 'Find requests where check list item has outcome set to "no"' () {
    
    when: 'Filter by value case-sensitively'
      List<Request> requests = service.lookup(Request, null, 10, 1, [
        "checklists.items.outcome==no"
      ])
    
    then: '1 result'
      requests.size() == 1
  }
  
  void 'Find requests where check list item status is "not required"' () {
    
    when: 'Filter by value case-insensitively'
      List<Request> requests = service.lookup(Request, null, 10, 1, [
        "checklists.items.status=i=not required"
      ])
    
    then: '2 results'
      requests.size() == 2
  }
  
  void 'The check list item has status "required" and the outcome not set' () {
    
    when: 'Filter by value isNull'
      List<Request> requests = service.lookup(Request, null, 10, 1, [
        "checklists.items.outcome isNull"
      ])
    
    then: '2 results'
      requests.size() == 2
      
    when: 'Filter by value isNull and status "required"'
      requests = service.lookup(Request, null, 10, 1, [
        "checklists.items.outcome isNull",
        "checklists.items.status=i=required"
      ])
    
    then: '1 results'
      requests.size() == 1
  }

  void 'Test inline junctions' () {
    when: 'Filter using conjunction'
      List<Request> requests = service.lookup(Request, null, 10, 1, [
        "checklists.items.outcome==unknown&&checklists.items.status==required"
      ])
  
    then: '1 result'
      requests.size() == 1
      
      
    when: 'Filter using disjunction'
      requests = service.lookup(Request, null, 10, 1, [
        "checklists.items.outcome==unknown||checklists.items.status==required"
      ])
  
    then: '3 results'
      requests.size() == 3
      
    when: 'Filter required OR no outcome (null)'
      requests = service.lookup(Request, null, 10, 1, [
        "checklists.items.outcome isnull||checklists.items.status=i=REQUIRED"
      ])
  
    then: '3 results'
      requests.size() == 3
      
    when: 'Filter not required OR no outcome (null)'
      requests = service.lookup(Request, null, 10, 1, [
        "checklists.items.outcome isnull||checklists.items.status=i=NOT REQUIRED"
      ])
  
    then: '2 results'
      requests.size() == 2
  }
  
  void 'The check list named list_3 and an unknown, required item' () {
    when: 'Filter'
      List<Request> requests = service.lookup(Request, null, 10, 1, [
        "checklists.name==list_3",
        "checklists.items.outcome==unknown&&checklists.items.status==required"
      ])
  
    then: '1 result'
      requests.size() == 1
  }
  
  void 'The check list named list_3 with unknown, required item and a required yes item' () {
    when: 'Filter without groupings'
      List<Request> requests = service.lookup(Request, null, 10, 1, [
        "checklists.name==list_3",
        "checklists.items.outcome==unknown&&checklists.items.status==required",
        "checklists.items.outcome==yes&&checklists.items.status==required"
      ])
    
    then: "Should always fail as you can't have a 2 different values for the same filed on the same item"
    
      requests.size() == 0
      
    when: 'Filter with groupings'
      requests = service.lookup(Request, null, 10, 1, [
        "checklists.name==list_3",
        "(checklists.items.outcome==unknown&&checklists.items.status==required)",
        "(checklists.items.outcome=i=yes&&checklists.items.status==required)"
      ])
      
    then: "Single result with both statuses"
    
      requests.size() == 1
      requests[0].name =="Request 3"
      
    when: 'Filter with groupings single filter'
      requests = service.lookup(Request, null, 10, 1, [
        "checklists.name==list_3&&(checklists.items.outcome==unknown&&checklists.items.status==required)&&(checklists.items.outcome==yes&&checklists.items.status==required)"
      ])
    
    then: "Single result with both statuses"
    
      requests.size() == 1
      requests[0].name =="Request 3"
      
    when: 'Filter with groupings is negated'
      requests = service.lookup(Request, null, 10, 1, [
        "!(checklists.items.outcome==unknown&&checklists.items.status==required)",
        "(checklists.items.outcome=i=yes&&checklists.items.status==required)"
      ])
    
    then: "Single result"
    
      requests.size() == 1
      requests[0].name == "Request 1"
  }
  
  void 'Test Ranges' () {
    when: 'Filter'
      List<Request> requests = service.lookup(Request, null, 10, 1, [
        "0<number<3" //
      ])
    
    then: "Macth 2"
    
      requests.size() == 2
      
    when: 'Filter'
      requests = service.lookup(Request, null, 10, 1, [
        "1<=number<3"
      ])
    
    then: "Still 2"
    
      requests.size() == 2
      
    when: 'Filter'
      requests = service.lookup(Request, null, 10, 1, [
        "1<=number<=3"
      ])
    
    then: "3 results"
    
      requests.size() == 3
      
    when: 'Filter'
      requests = service.lookup(Request, null, 10, 1, [
        "1<=number<4"
      ])
    
    then: "3 results"
    
      requests.size() == 3
      
    when: 'Filter date'
      LocalDate now = LocalDate.now()
      DateTimeFormatter df = DateTimeFormatter.ISO_DATE
      requests = service.lookup(Request, null, 10, 1, [
        "date>${df.format(now)}"
      ])
    
    then: "1 result"
    
      requests.size() == 1
      
    when: 'Filter date'
      requests = service.lookup(Request, null, 10, 1, [
        "date>=${df.format(now)}"
      ])
    
    then: "3 results"
    
      requests.size() == 3
      
    when: 'Filter date range'
      requests = service.lookup(Request, null, 10, 1, [
        "${df.format(now)}<=date<=${df.format(now.plusDays(1))}"
      ])
    
    then: "3 result"
    
      requests.size() == 3
      
    when: 'Filter date range'
      requests = service.lookup(Request, null, 10, 1, [
        "${df.format(now)}<date<=${df.format(now.plusDays(1))}"
      ])
    
    then: "1 result"
    
      requests.size() == 1
      
    when: 'Filter date range is negated'
      requests = service.lookup(Request, null, 10, 1, [
        "!${df.format(now)}<date<=${df.format(now.plusDays(1))}"
      ])
    
    then: "2 result"
    
      requests.size() == 2
  }
  
  void 'Text searching and filtering' () {
    when: 'Filter'
      List<Request> requests = service.lookup(Request, 'request', 10, 1, [
        "0<number<3" //
      ],['name'])
    
    then: "Macth 2"
    
      requests.size() == 2
      
    when: 'Filter matching "request 2" in "name"'
      requests = service.lookup(Request, 'request 2', 10, 1, null, ['name'])
    
    then: "Matches 1"
    
      requests.size() == 1
      
    when: 'Filter matching "request 2" in "name" where the "number" is bigger than 2'
      requests = service.lookup(Request, '"request 2"', 10, 1, [
        "number>2"
      ],['name'])
    
    then: "Match 0"
    
      requests.size() == 0
      
    when: 'Filter for name "request 2" with quotes'
      requests = service.lookup(Request, '"request 2"', 10, 1, null, ['name'])
    
    then: "Match 1"
    
      requests.size() == 1
  }

  void 'Paged duplicate-root dedup preserves first-seen id order' () {
    given: 'A list containing duplicate root ids in encounter order'
      final List<LookupDedupProbe> rows = [
        new LookupDedupProbe(id: 3L, label: 'a'),
        new LookupDedupProbe(id: 1L, label: 'b'),
        new LookupDedupProbe(id: 3L, label: 'c'),
        new LookupDedupProbe(id: 2L, label: 'd'),
        new LookupDedupProbe(id: 1L, label: 'e')
      ]

    when: 'Service-level dedup is applied'
      final def method = SimpleLookupService.getDeclaredMethod('deduplicateEntityResultsById', List)
      method.accessible = true
      method.invoke(null, rows)

    then: 'Only first-seen id rows remain and order is preserved'
      rows*.id == [3L, 1L, 2L]
      rows*.label == ['a', 'b', 'd']
  }

  void 'Paged duplicate-root dedup is a no-op for rows without id property' () {
    given: 'Rows that do not expose an id property'
      final List<LookupNoIdProbe> rows = [
        new LookupNoIdProbe(label: 'a'),
        new LookupNoIdProbe(label: 'b'),
        new LookupNoIdProbe(label: 'a')
      ]
      final List<String> before = rows*.label

    when: 'Service-level dedup is applied'
      final def method = SimpleLookupService.getDeclaredMethod('deduplicateEntityResultsById', List)
      method.accessible = true
      method.invoke(null, rows)

    then: 'Rows are unchanged because id-based policy does not apply'
      rows*.label == before
  }
  
  
  @Override
  List<Class> getDomainClasses() { [Request, CheckList, CheckListItem] }
  
  @Override
  Map<String, Object> getConfiguration() {
    [
      (Settings.SETTING_DB_CREATE): 'create-drop',
      (Settings.SETTING_DATASOURCE + '.logSql'):  true,
      (Settings.SETTING_DATASOURCE + '.driverClassName'):  org.h2.Driver,
      (Settings.SETTING_DATASOURCE + '.dialect'):  org.hibernate.dialect.H2Dialect,
      (Settings.SETTING_DATASOURCE + '.username'):  'sa',
      (Settings.SETTING_DATASOURCE + '.password'):  '',
      (Settings.SETTING_DATASOURCE + '.url'):  'jdbc:h2:mem:testDb;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE;DB_CLOSE_DELAY=-1;MODE=LEGACY',
      
      (Environment.DIALECT): org.hibernate.dialect.H2Dialect,
      (Environment.HBM2DDL_AUTO): org.hibernate.tool.schema.Action.CREATE,
      (Settings.SETTING_MULTI_TENANCY_MODE): 'none'
      
    ] as Map<String, Object>
  }
}

@GrailsCompileStatic
@Entity
class CheckListItem {
  String outcome
  String status
  CheckList checklist
  
  static belongsTo = CheckList
  static mappedBy = [checklist: 'items']
  
  static constraints = {
    outcome   nullable: true,  blank: false
    status    nullable: false, blank: false
  }
}

@GrailsCompileStatic
@Entity
class CheckList {
  String name
  
  static belongsTo = Request
  Request request
  
  Set<CheckListItem> items = []
  
  static hasMany = [items: CheckListItem]
  static mappedBy = [request: 'checklists']
  
  static mapping = {
    name  nullable: false, blank: false
    items cascade: 'all-delete-orphan'
  }
}

@GrailsCompileStatic
@Entity
class Request {
  String name
  
  int number
  LocalDate date
  
  static constraints = {
    name  nullable: false, blank: false
  }
  static hasMany = [checklists: CheckList]
}

@GrailsCompileStatic
class LookupDedupProbe {
  Long id
  String label
}

@GrailsCompileStatic
class LookupNoIdProbe {
  String label
}
