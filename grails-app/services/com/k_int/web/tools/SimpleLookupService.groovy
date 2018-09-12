package com.k_int.web.tools


import org.hibernate.criterion.*

import com.k_int.web.toolkit.utils.DomainUtils

import grails.util.GrailsClassUtils
import groovy.util.logging.Log4j

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
    
    log.debug ("Checking for ${dotNotationString}")
    
    def str = aliasStack[dotNotationString]
    if (!str) {

      log.debug "Full match not found..."
        
      // No alias found for exact match.
      // Start from the front and build up aliases.
      String[] props = dotNotationString.split("\\.")
      String propStr = "${props[0]}"
      String alias = aliasStack[propStr]
      String currentAlias = alias
      int counter = 1
      while (currentAlias && counter < props.length) {
        str = "${currentAlias}"
        String test = propStr + ".${props[counter]}"
        log.debug "Testing for ${test}"
        currentAlias = aliasStack[test]
        if (currentAlias) {
          alias = currentAlias
          propStr += test
        }
        counter ++
      }
      
      log.debug "...propStr: ${propStr}"
      log.debug "...alias: ${alias}"

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
    
    log.debug "Looking for property ${propname}"
    
    // Split at the dot.
    String[] levels = propname.split("\\.")

    PropertyDef ret = new PropertyDef()
    ret.putAll([
      'alias' : levels.length > 1 ? checkAlias ( target, aliasStack, levels[0..(levels.length - 2)].join('.')) : '',
      'property' : levels[levels.length - 1]
    ])

    ret
  }
  
  private static final String REGEX_OP = "^(.*?)(=i=|([!=<>]{1,2}))(.*?)\$"
  private static final String REGEX_BETWEEN = "^(.*?)([<>]=?)(.*?)([<>]=?)(.*?)\$"
  private static final String REGEX_NONE_ESCAPED_PERCENTAGE = "([^\\\\])(%)"
  
  private List getComparisonParts (final DetachedCriteria criteria, final Map<String, String> aliasStack, final String filterString, final String indentation = null ) {
    // Between first.
    final def results = []
    def matches = filterString =~ REGEX_BETWEEN
    if (matches.size() == 1) {
      
      log.debug('Special between style syntax. Splitting into 2 filters instead.')
      
      // Grab the match.
      def match = matches[0]
      
      // Build up 2 more filter strings instead of directly returning the parts here.
      // Match 3 is the center part and is the common part of the expression.
      results << parseFilterString(criteria, aliasStack, "${match[1]}${match[2]}${match[3]}", indentation)
      results << parseFilterString(criteria, aliasStack, "${match[3]}${match[4]}${match[5]}", indentation)
      
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
          case '=i=' :
            // Special case insensitive equal. We need to use ilike under the hood and remove
            // and we also need to remeber to escape % signs as they should be matched explicitly 
            results << 'ilike'
            break
            
          default : 
            log.debug "Unknown comparator '${match[2]}' ignoring expression."
        }
        
        if (results) {
          
          def m1 = results[0] == 'ilike' ? "${match[1]}".replaceAll(REGEX_NONE_ESCAPED_PERCENTAGE, '$1\\$2') : match[1]
          def m2 = results[0] == 'ilike' ? "${match[4]}".replaceAll(REGEX_NONE_ESCAPED_PERCENTAGE, '$1\\$2') : match[4]
          
          results << m1
          results << m2
        }
      }
    }
    
    if (!results) log.debug "Unknown expression ${filterString}"
    
    results
  }
  
  private String invertOp(final String op) {
    final String newOp = op
    switch (op) {
      case 'eq' :
        newOp = 'ne'
        break
      case 'ne' :
        newOp = 'eq'
        break
      case 'gt' :
        newOp = 'lt'
        break
      case 'ge' :
        newOp = 'le'
        break
      case 'lt' :
        newOp = 'gt'
        break
      case 'le' :
        newOp = 'ge'
        break
        
      default :
        log.debug "Unknown reverse op of ${op}. Retuning original."
    }
    
    newOp
  }
  
  private Criterion parseFilterString ( final DetachedCriteria criteria, final Map<String, String> aliasStack, String filterString, String indentation = null ) {
    
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
          def parts = getComparisonParts (criteria, aliasStack, filterString, indentation)
          log.debug "Got comparision parts ${parts}"
          if (parts) {
            
            def op = parts[0]
            if (Criterion.class.isAssignableFrom(op.class)){
              // Assume all parts are Criterion.
              // And them.              
              crit = Restrictions.and( parts as Criterion[] )
            } else {
            
              // Assume normal op.
              // part 1 or 2 could be the property name.
              // the other is the value.
              def prop = "${parts[1]}".trim()
              def value = parts[2]
              def propDef = DomainUtils.resolveProperty(criteria.criteriaImpl.entityOrClassName, prop)
              if (!propDef) {
                // Swap the values and retry.
                prop = "${parts[2]}".trim()
                propDef = DomainUtils.resolveProperty(criteria.criteriaImpl.entityOrClassName, prop)
                if (propDef) {
                  value = parts[1]
                  op = invertOp(op)
                }
              }
              
              if (propDef) {
                if (propDef.filter) {
                  def propertyType = propDef.type
                  def propName = getAliasedProperty(criteria, aliasStack, prop) as String
                  
                  // Can't ilike on none-strings. So we should change back to eq.
                  if (op == 'ilike' && !String.class.isAssignableFrom(propertyType)) {
                    op = 'eq'
                  }
                
                  log.trace ("${indentation}${op} ${prop}, ${value}")
                  def val = valueConverterService.attemptConversion(propertyType, value)
                  
                  log.debug ("Converted ${value} into ${val}")
                  crit = Restrictions."${op}" (propName, val)
                } else {
                  log.debug "Filter on ${parts} has been disallowed."
                }
              } else {
                log.debug "Could not process op def ${parts}"
              }
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
  
  private List<Criterion> getTextMatches (final DetachedCriteria criteria, final Map<String, String> aliasStack, final String term, final match_in, MatchMode textMatching = MatchMode.ANYWHERE) {
    List<Criterion> textMatches = []
    if (term) {
      // Add a condition for each parameter we wish to search.
      match_in.each { String prop ->
        def propDef = DomainUtils.resolveProperty(criteria.criteriaImpl.entityOrClassName, prop)
        
        if (propDef) {
        
          if (propDef.search) {
            def propertyType = propDef.type
            def propName = getAliasedProperty(criteria, aliasStack, prop) as String
            
            // We use equal unless the compared property is a String then we should use iLIKE
            def op = 'eq'
            if (String.class.isAssignableFrom(propertyType)) {
              textMatches << Restrictions.ilike("${propName}", "${term}", textMatching)
              log.debug ("Looking for term '${term}' in ${propName}" )
            } else {
              // Attempt to convert the value into one comparable with the target. 
              def val = valueConverterService.attemptConversion(propertyType, term)
              textMatches << Restrictions.eq("${propName}", val)
              log.debug ("Converted ${term} into ${val} as type '${propertyType}'")
            }
          } else {
            log.debug "Search on ${prop} has been disallowed."
          }
        } else {
          log.debug "Could not process ${prop}"
        }
      }
    }
    
    textMatches
  }
  
  private addSorts (final target, final sorts) {
    final Map<String, String> aliasStack = [:]
    
    sorts.each { String sort ->
      final String[] sortParts = sort.split(/;/)
      final String prop = sortParts[0]
      
      def propDef = DomainUtils.resolveProperty(target.targetClass, prop)
      if (propDef) {
        if (propDef.sort) {
        
          // Sort direction for this field.
          final String direction = (sortParts.length > 1 ? sortParts[1] : 'asc')?.toLowerCase() == 'desc' ? 'desc' : 'asc'
          
          def propName = getAliasedProperty(target, aliasStack, prop) as String
          if (propName) {
            target.addOrder(Order."${direction}"(propName))
            log.debug "Sort on ${propName} ${direction}."
          }
        } else {
          log.debug "Sort on ${prop} has been disallowed."
        }
      } else {
        log.debug "Could not process sort ${prop}"
      }
    }
  }

  private DetachedCriteria buildLookupCriteria(final DetachedCriteria criteria, final String term, final match_in, final filters) {

    Map<String, String> aliasStack = [:]
    List<Criterion> criterionList = []

    // Filters...
    parseFilters (criteria, aliasStack, filters)
    
    // Text matching uses ilike ops for string property and eq for all others.
    List<Criterion> textMatches = getTextMatches(criteria, aliasStack, term, match_in)
    
    // Text searching should be Disjunctive accross all properties specified.
    if (textMatches) criterionList << Restrictions.or(textMatches.toArray(new Criterion[textMatches.size()]))

    // Conjunction to ensure results returned match any text searches specified and ALL filters specified.
    if (criterionList) criteria.add(Restrictions.and(criterionList.toArray(new Criterion[criterionList.size()])))

    // Project distinct IDs so as to only include results once on multiple matches.
    criteria.setProjection(
      Projections.distinct(Projections.id())
    )

    criteria
  }

  public def lookup (final Class c, final String term, final Integer perPage = 10, final Integer page = 1, final List filters = [], final List match_in = [], final List sorts = [], final Closure base = null) {

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
      
      // Add any sorts.
      if (sorts) {
        addSorts (delegate, sorts)
      }
    })
  }

  public def lookupWithStats (final Class c, final String term, final Integer perPage = 10, final Integer page = 1, final List filters = [], final List match_in = [], final List sorts = [], final Map<String,Closure> extraStats = null, final Closure base = null) {

    Map aliasStack = [:]

    // Results per page, cap at 1000 for safety here. This will probably be capped by the implementing controller to a lower value.
    int pageSize = Math.min(perPage, 1000)
    
    def statMap = [
      'results'     : lookup(c, term, pageSize, page, filters, match_in, sorts, base),
      'pageSize'    : pageSize,
      'page'        : page ?: 1,
      'totalPages'  : 0,
      'meta'        : [:]
    ]
    
    statMap.totalRecords = statMap.results?.totalCount ?: 0
    statMap.totalPages = ((int)(statMap.totalRecords / pageSize) + (statMap.totalRecords % pageSize == 0 ? 0 : 1))
    
    // Add extra projection values to the map by re-executing the original query with our
    // extras piped in.
    if (statMap.totalRecords > 0 && extraStats) {
      
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
          extra.setDelegate(delegate)
          extra()
        }
        
        // Add any sorts.
        if (sorts) {
          addSorts (delegate, [:], sorts)
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

    statMap.total = statMap.totalRecords
    
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
      
      def basePropVal = GrailsClassUtils.getStaticPropertyValue(c, "lookupBase")
      Closure base = basePropVal && (basePropVal instanceof Closure) ? basePropVal : null
      base?.rehydrate(delegate, base?.owner, base?.thisObject)?.call()

      crit.setDelegate(delegate)
      
      // Always set to read only.
      readOnly true
      
      crit ()
    };

    if (methodPars) {

      c.createCriteria()."${method}" (methodPars, newCrit)
    } else {
      c.createCriteria()."${method}" (newCrit)
    }
  }
}
