package com.k_int.web.toolkit


import java.time.LocalDate
import java.time.format.DateTimeFormatter

import org.grails.orm.hibernate.HibernateDatastore
import org.grails.orm.hibernate.cfg.Settings
import org.hibernate.cfg.Environment
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.interceptor.DefaultTransactionAttribute

import com.k_int.web.toolkit.databinding.ExtendedWebDataBinder
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
