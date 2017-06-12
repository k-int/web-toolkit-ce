package web.toolkit

import static org.springframework.http.HttpStatus.*
import grails.core.GrailsApplication
import grails.transaction.Transactional
import grails.util.GrailsClassUtils
import grails.util.GrailsNameUtils

import org.grails.core.DefaultGrailsDomainClass

import com.k_int.grails.tools.refdata.RefdataValue
import com.k_int.grails.tools.rules.RulePropertySource
import com.k_int.grails.tools.rules.RulesService
import com.k_int.grails.tools.utils.DomainUtils


class RefController {

  static responseFormats = ['json', 'xml']

  GrailsApplication grailsApplication
  RulesService rulesService
  SimpleLookupService simpleLookupService
  
  @Transactional(readOnly = true)
  def checkRules (String domain, long id, String ruleType) {

    // Try and locate the Domain class.
    DefaultGrailsDomainClass target = DomainUtils.findDomainClass (domain)

    if (!(target && id && ruleType )) {
      return notFound()
    }

    RulePropertySource source = target.clazz.read(id)

    // Run the rules.
    respond  (source ? rulesService.runRules(ruleType, source) :  [:] )
  }

  @Transactional(readOnly = true)
  def get (String domain, String prop, String search) {

    // Try and locate the Domain class.
    DefaultGrailsDomainClass target = DomainUtils.findDomainClass (domain)

    if (!(target && prop)) {
      return notFound()
    }

    // Resolve the property and classes we need.
    def propDef
    try {
      propDef = DomainUtils.resolveProperty(target, prop)
    } catch (Throwable t) { propDef = null }
    if (!propDef) return notFound()
    
    Set vals = []
    switch ( propDef['type'] ) {
      case {GrailsClassUtils.isAssignableOrConvertibleFrom(RefdataValue.class, it)} :
      // Refdata have a special method bolted onto the class.
        String upperName = GrailsNameUtils.getClassName(propDef['prop'])
        vals = propDef['owner']."all${upperName}Values" () as Set
        break

      default:
        vals = lookup ( propDef['type'], search ) as Set
    }

    respond vals
  }

  def blank(String domain, String prop) {

    // Try and locate the Domain class.
    DefaultGrailsDomainClass target = DomainUtils.findDomainClass (domain)

    if (!(target && prop)) {
      return notFound();
    }

    // Resolve the property and classes we need.
    def propDef
    try {
      propDef = DomainUtils.resolveProperty(target, prop)
    } catch (Throwable t) { propDef = null }
    if (!propDef) return notFound()

    // The object to return
    def obj
    
    // Class
    Class<?> theClass = propDef['type'];
    if (theClass?.metaClass.getMetaMethod("createBlank")) {
      // The class declares a createBlank method...
      obj = theClass.createBlank(propDef)
    } else {
      // Simply create an instance.
      obj = propDef['type']?.newInstance()
    }
    
    respond obj
  }

  protected def lookup (Class c, String term) {
    
    return simpleLookupService.lookup(c, term, Math.min(params.int('perPage') ?: 100, 100), params.int("page"), params.list("filters"), params.list("match"))
  }

  private notFound () {
    // Not found response.
    render (status : NOT_FOUND)
    return
  }
}


