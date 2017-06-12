package com.k_int.web.tools

import com.k_int.grails.tools.utils.DomainUtils
import grails.transaction.Transactional
import groovy.util.logging.Log4j

import org.hibernate.criterion.Criterion
import org.hibernate.criterion.DetachedCriteria
import org.hibernate.criterion.MatchMode
import org.hibernate.criterion.Projections
import org.hibernate.criterion.Restrictions
import org.hibernate.criterion.Subqueries

@Log4j
class SimpleLookupService {
  
  ValueConverterService valueConverterService

  private static class PropertyDef extends HashMap<String, String> {
    PropertyDef () {
      super()
    }
    
    @Override
    public String toString () {
      String al = this.get('alias')
      "${al ? al + '.' : ''}${this.get('property')}".toString()
    }
    
    public Object asType(Class type) {
      if (type == String) {
        return this.toString()
      }
      
      super.asType(type)
    }
  }

  private static final String checkAlias(def target, final Map<String, String> aliasStack, String dotNotationString) {
    def str = aliasStack[dotNotationString]
    if (!str) {

      // No alias found for exact match.
      // Start from the front and build up aliases.
      String[] props = dotNotationString.split("\\.")
      String propStr = "${props[0]}"
      String alias = aliasStack[propStr];
      int counter = 1
      while (alias && counter < props.length) {
        str = "${alias}"
        String test = propStr + ".${props[counter]}"

        alias = aliasStack[test]
        if (alias) {
          propStr += test
        }
        counter ++
      }

      // At this point we should have a dot notated alias string, where the aliases already been created for this query.
      // Any none existent aliases will need creating but also adding to the map for traversing.
      if (counter <= props.length) {
        // The counter denotes how many aliases were present, so we should start at the counter and create the missing
        // aliases.
        propStr = null
        for (int i=(counter-1); i<props.length; i++) {
          String aliasVal = alias ? "${alias}.${props[i]}" : "${props[i]}"
          alias = "alias${aliasStack.size()}"

          // Create the alias.
          log.debug ("Creating alias: ${aliasVal} ->  ${alias}")
          target.criteria.createAlias(aliasVal, alias)

          // Add to the map.
          propStr = propStr ? "${propStr}.${props[i]}" : "${props[i]}"
          aliasStack[propStr] = alias
          log.debug ("Added quick string: ${propStr} -> ${alias}")
        }
      }

      // Set the string to the alias we ended on.
      str = alias
    }

    str
  }

  private static final Map getAliasedProperty (def target, final Map<String, String> aliasStack, final String propname) {
    // Split at the dot.
    String[] levels = propname.split("\\.")

    PropertyDef ret = new PropertyDef()
    ret.putAll([
      'alias' : levels.length > 1 ? checkAlias ( target, aliasStack, levels[0..(levels.length - 2)].join('.')) : '',
      'property' : levels[levels.length - 1]
    ])

    ret
  }
  
  private static final String REGEX_OP = "^([A-Za-z0-9\\.]+)([\\=\\!\\<\\>]{1,2})(.+)\$"
  private static final String REGEX_BETWEEN = "^(.+)(\\<\\=?)([A-Za-z0-9\\.]+)(\\<\\=?)(.+)\$"
  
  private static List getComparisonParts ( String filterString ) {
    // Between first.
    def results = []
    def matches = filterString =~ REGEX_BETWEEN
    if (matches.size() == 1) {
      def match = matches[0]
      results << 'between'
      results << match[3]
      results << match[1]
      results << (match[2] == '<=')
      results << match[5]
      results << (match[4] == '<=')
      
    } else {
      matches = filterString =~ REGEX_OP
      if (matches.size() == 1) {
        def match = matches[0]
        
        switch (match[2]) {
          case '=' :
          case '==' :
            results << 'eq'
            break
          case '!=' :
          case '<>' :
            results << 'ne'
            break
          case '>' :
            results << 'gt'
            break
          case '>=' :
            results << 'ge'
            break
          case '<' :
            results << 'lt'
            break
          case '<=' :
            results << 'le'
            break
            
          default : 
            log.debug "Unknown comparator '${match[2]}' ignoring expression."
        }
        
        if (results) {
          results << match[1]
          results << match[3]
        }
      }
    }
    
    if (!results) log.debug "Unknown expression ${filterString}"
    
    results
  }
  
  private Criterion parseFilterString ( final DetachedCriteria criteria, final Map<String, String> aliasStack, String filterString, String indentation = null ) {
//    def recurse = { String fStr, def o, def d, def to ->
//      // Now call out to this parent method again and with the same owner, delegate and thisObject
//      parseFilterString.rehydrate(o, d, to).call(fStr)
//    }
    
    Criterion crit = null
    
    if (indentation == null) {
      indentation = ''
    } else {
      indentation += '  '
    }
    
    // First check for logical operators like AND / OR
    String[] entries = filterString.split (/\&\&/)
    if (entries.length > 1) {
      // Start an and.
      log.trace ("${indentation}and {")
      
      List<Criterion> criterionList = []
      
      // Now call out to this method again with each entry.
      entries.each { 
        criterionList << parseFilterString (criteria, aliasStack, it, indentation)
      }
      crit = Restrictions.and( criterionList as Criterion[] )
      log.trace ("${indentation}}")
    } else {
      entries = filterString.split (/\|\|/)
      if (entries.length > 1) {
        // Start an OR.
        log.trace ("${indentation}or {")
        
        List<Criterion> criterionList = []
        
        // Now call out to this method again with each entry.
        entries.each { 
          criterionList << parseFilterString (criteria, aliasStack, it, indentation)
        }
        
        crit = Restrictions.or( criterionList as Criterion[] )
        log.trace ("${indentation}}")
      } else {
        // Check for negation.
        if (filterString.startsWith("!")) {
          
          log.trace ("${indentation}not {")
          crit = Restrictions.not( parseFilterString (criteria, aliasStack, filterString.substring(1), indentation) )
          log.trace ("${indentation}}")
          
        } else {
          // Then check for comparator type i.e. gt, eq, lt etc..
          def parts = getComparisonParts filterString
          
          if (parts) {
            def op = parts[0]
            def propertyType = DomainUtils.resolveProperty(criteria.criteriaImpl.entityOrClassName, parts[1]).type
            def propName = getAliasedProperty(criteria, aliasStack, parts[1]) as String
            
            if (op == 'between') {
              def gtop = parts[3] ? 'ge' : 'gt'
              def ltop = parts[5] ? 'le' : 'lt'
              
              List<Criterion> criterionList = []
              
              // Because we allow for our between to be inclusive, or not we use to operations anded
              // instead of the between method
              log.trace ("${indentation}and {")
                
              log.trace ("${indentation}${ltop} ${parts[1]}, ${parts[2]}")
              def from = valueConverterService.attemptConversion(propertyType, parts[2])
              criterionList << Restrictions."${gtop}" (propName, from)
              
              log.trace ("${indentation}${ltop} ${parts[1]}, ${parts[4]}")
              def to = valueConverterService.attemptConversion(propertyType, parts[4])
              criterionList << Restrictions."${ltop}" (propName, to)
              
              log.trace ("${indentation}}")
              crit = Restrictions.and( criterionList as Criterion[] )
              
            } else {
              log.trace ("${indentation}${op} ${parts[1]}, ${parts[2]}")
              def val = valueConverterService.attemptConversion(propertyType, parts[2])
              crit = Restrictions."${op}" (propName, val)
            }
          }
        }
      }
    }
    
    crit
  }
  
  private void parseFilters ( final DetachedCriteria criteria, final Map<String, String> aliasStack, final Collection<String> filters ) {
    
    // We parse the filters and build up the criteria.
    filters?.each {
      def filterGroup = parseFilterString (criteria, aliasStack, it)
      if (filterGroup) {
        criteria.add( filterGroup )
      }
    }
  }

  private DetachedCriteria buildLookupCriteria(final DetachedCriteria criteria, final String term, final match_in, final filters) {

    Map<String, String> aliasStack = [:]
    List<Criterion> criterionList = []

    // Filters...
    parseFilters (criteria, aliasStack, filters)
//    if (filters) {
//
//      filters.eachWithIndex { String filter, idx ->
//        String[] parts =  filter.split("\\=")
//
//        if ( parts.length == 2 && parts[0].length() > 0 && parts[1].length() > 0 ) {
//
//          // The prop name.
//          String propname = parts[0]
//          String op = "eq"
//
//          if (propname.startsWith("!")) {
//            propname = propname.substring(1)
//            op = "ne"
//          }
//
//          def prop = getAliasedProperty(criteria, aliasStack, propname)
//
//          log.debug ("Testing  ${prop as String} ${op == 'eq' ? '=' : '!='} ${parts[1]}")
//          criterionList << Restrictions."${op}" ( "${prop.toString()}", prop['property'] == 'id' ? parts[1].toLong() : parts[1] )
//        }
//      }
//    }

    if (term) {
      // Add a condition for each parameter we wish to search.
      List<Criterion> textMatches = []
      match_in.each { String param_name ->
        switch (param_name) {
          case "id" :
            textMatches << Restrictions.eq ("${param_name}", term.toLong())
            break;
          // Like for strings.
          default :
            textMatches << Restrictions.ilike("${param_name}", "${term}", MatchMode.ANYWHERE)
        }
      }
      if (textMatches) criterionList << Restrictions.or(textMatches.toArray(new Criterion[textMatches.size()]))
    }

    // Add the list as the AND criterion.
    if (criterionList) criteria.add(Restrictions.and(criterionList.toArray(new Criterion[criterionList.size()])))

    criteria.setProjection(
        Projections.distinct(Projections.id())
        )

    criteria
  }

  @Transactional(readOnly=true)
  public def lookup (final Class c, final String term, final Integer perPage = 10, final Integer page = 1, final List filters = [], final List match_in = [], final Closure base = null) {

    // Results per page, cap at 1000 for safety here. This will probably be capped by the implementing controller to a lower value.
    int pageSize = Math.min(perPage, 1000)

    // If we have a page then we should add a max and offset.
    def query_params = ["max": (pageSize)]
    if (page) {
      query_params["offset"] = ((page - 1) * pageSize)
    }

    doList (c, query_params, {
      // Change the delegate and execute.
      if (base) {
        base.setDelegate(delegate)
        base()
      }

      // Add lookup.
      criteria.add(
          Subqueries.propertyIn(
          'id',
          buildLookupCriteria(DetachedCriteria.forClass(targetClass, 'uniques'), term, match_in, filters)
          )
          )
    })
  }

  @Transactional(readOnly=true)
  public def lookupWithStats (final Class c, final String term, final Integer perPage = 10, final Integer page = 1, final List filters = [], final List match_in = [], final Map<String,Closure> extraStats = null, final Closure base = null) {

    Map aliasStack = [:]

    // Results per page, cap at 1000 for safety here. This will probably be capped by the implementing controller to a lower value.
    int pageSize = Math.min(perPage, 1000)
    
    // Stats is now an array containing the projections we asked for.
    // Positions 0 and 1 contain the total count and id respectively.
    def statMap = [
      'results'     : lookup(c, term, pageSize, page, filters, match_in, base),
      'pageSize'    : pageSize,
      'page'        : page ?: 1,
      'totalPages'  : 0,
      'meta'        : [:]
    ]
    
    statMap.total = statMap.results?.totalCount ?: 0
    statMap.totalPages = ((int)(statMap.total / pageSize) + (statMap.total % pageSize == 0 ? 0 : 1))
    
    // Add extra projection values to the map by re-executing the original query with our
    // extras piped in.
    if (statMap.total > 0 && extraStats) {
      
      final Closure query = { Closure extra ->
  
        // Add lookup.
        criteria.add(
          Subqueries.propertyIn(
            'id',
            buildLookupCriteria(DetachedCriteria.forClass(targetClass), term, match_in, filters)
          )
        )
        
        // Change the delegate and execute.
        if (base) {
          base.setDelegate(delegate)
          base()
        }
        
        // Change the delegate and execute.
        if (extra) {
          extra.rehydrate(delegate, extra.owner, thisObject).call()
        }
      }
      
      extraStats.each { String k, Closure v ->
        def stats = doGet(c, query.curry(v))
        
        if (stats && stats.class.isArray()) {
          k.split(/\,/).eachWithIndex { String projName, i ->
            statMap['meta'][projName] = stats[i] ?: 0
          }
        } else {
          statMap['meta'][k] = stats ?: 0
        }
      }
    }

    statMap
  }

  protected def doGet(final Class c, final Map methodPars = [:], final Closure crit) {
    doMethod (c, "get", methodPars, crit)
  }

  protected def doList(final Class c, final Map methodPars = null, final Closure crit) {
    doMethod (c, "list", methodPars, crit)
  }

  private synchronized def doMethod (final Class c, final String method, final Map methodPars = null, final Closure crit) {
    final Closure newCrit = {
      Closure base = null //GrailsClassUtils.getStaticPropertyValue(c, "lookupBase")
      base?.rehydrate(delegate, base?.owner, base?.thisObject)?.call()

      crit.setDelegate(delegate)
      crit ()
    };

    if (methodPars) {

      c.createCriteria()."${method}" (methodPars, newCrit)
    } else {
      c.createCriteria()."${method}" (newCrit)
    }
  }
}
