package com.k_int.web.toolkit


import org.hibernate.criterion.*
import org.hibernate.sql.JoinType

import com.k_int.web.toolkit.utils.DomainUtils
import com.k_int.web.toolkit.utils.DomainUtils.InternalPropertyDefinition
import grails.util.GrailsClassUtils
import groovy.util.logging.Slf4j

@Slf4j
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

  private static final String checkAlias(def target, final Map<String, String> aliasStack, String dotNotationString, boolean leftJoin) {
    
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
          propStr = test
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
        for (int i=(counter-1); i<props.length; i++) {
          String aliasVal = alias ? "${alias}.${props[i]}" : "${props[i]}"
          alias = "alias${aliasStack.size()}"

          // Create the alias.
          log.debug ("Creating alias: ${aliasVal} -> ${alias}")
          target.criteria.createAlias(aliasVal, alias, (leftJoin ? JoinType.LEFT_OUTER_JOIN : JoinType.INNER_JOIN))

          // Add to the map.
          propStr = i>0 ? "${propStr}.${props[i]}" : "${props[i]}"
          aliasStack[propStr] = alias
          log.debug ("Added quick string: ${propStr} -> ${alias}")
        }
      }

      // Set the string to the alias we ended on.
      str = alias
    }

    str
  }

  private static final Map getAliasedProperty (def target, final Map<String, String> aliasStack, final String propname, final boolean leftJoin = false) {
    
    log.debug "Looking for property ${propname}"
    
    // Split at the dot.
    String[] levels = propname.split("\\.")

    PropertyDef ret = new PropertyDef()
    ret.putAll([
      'alias' : levels.length > 1 ? checkAlias ( target, aliasStack, levels[0..(levels.length - 2)].join('.'), leftJoin) : '',
      'property' : levels[levels.length - 1]
    ])

    ret
  }
  
  private static final String REGEX_SPECIAL_OP = "(?i)^(.*?)\\s+(is)(Not)?(Null|Empty|Set)\\s*\$"
  private static final String REGEX_OP = "^(.*?)([=!]~|=i=|([!=<>]{1,2}))(.*?)\$"
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
      matches = filterString =~ REGEX_SPECIAL_OP
      if (matches.size() == 1) {
        log.debug('Single op like isEmpty or isNotNull.')
        
        def match = matches[0]
        final String rootOp = match[4]?.trim()?.toLowerCase()?.capitalize()
        final String negated = match[3]?.trim()?.toLowerCase()?.capitalize() ?: ''
        
        String op = "${match[2]?.trim()?.toLowerCase()}${negated}${rootOp}"
        
        if (rootOp == 'Set') {
          log.debug "Special 2 part operation ${op}"
          
          // Add the criterion directly.
          if (negated) {
            // Actually specially maps to NOT ( isNotNull ), which is not the same as isNull
            // and hence this special case. Let's use our string parsing to build up the query so as to
            // take care of joins.
            
            final String critString = "!${match[1]} isNotNull"
            log.debug "Alias of ${critString}"
            
            Criterion c = parseFilterString(criteria, aliasStack, "!${match[1]} isNotNull", indentation)
            if (c) {
              results << c
            }
          } else {
            log.debug "Alias of ${match[1]} isNotNull"
            
            // Just return the strings in the normal manner.
            results << 'isNotNull'
            results << match[1]
          }
          
        } else {
          log.debug "Normal 2 part operation ${op}"
          results << op
          results << match[1]
        }
        
      } else {
        
        boolean negate = false
      
        matches = filterString =~ REGEX_OP
        if (matches.size() == 1) {
          def match = matches[0]
          
          switch (match[2]) {
            case '=' :
            case '==' :
              results << 'eqOrIsNull'
              break
            case '!=' :
            case '<>' :
              results << 'neOrIsNotNull'
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
            case '!~' :
              negate = true
            case '=~' :
              // Contains and not contains should use ilike with wilds.
               
            case '=i=' :
              // Special case insensitive equal. We need to use ilike under the hood and remove
              // and we also need to remeber to escape % signs as they should be matched explicitly 
              results << 'ilike'
              break
              
            default : 
              log.debug "Unknown comparator '${match[2]}' ignoring expression."
          }
          
          if (results) {
            
            // Default to raw values.
            def m1 = match[1]
            def m2 = match[4]
            
            if (results[0] == 'ilike') {
              m1 = "${m1}".replaceAll(REGEX_NONE_ESCAPED_PERCENTAGE, '$1\\$2')
              m2 = "${m2}".replaceAll(REGEX_NONE_ESCAPED_PERCENTAGE, '$1\\$2')
            
              if (['!~','=~'].contains(match[2])) {
                // Only the LHS can be the property name so the value must be the RHS (match 4).
                m2 = "%${m2}%"
              }
            }
            
            results << (m1.trim() == 'NULL' ? null : m1)
            results << (m2.trim() == 'NULL' ? null : m2)
            
            if (negate == true) {
              results << true
            }
          }
        }
      }
    }
    
    if (!results) log.debug "Unknown expression ${filterString}"
    
    results
  }
  
  private String invertOp(final String op) {
    String newOp = op
    switch (op) {
      case 'eqOrIsNull' :
      case 'neOrIsNotNull' :
        log.debug "Reverse of op ${op} is still ${op}"
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
  
  /**
   * @TODO: Need to make this more efficient by reusing/extending the existing subqueries.
   */
  private DetachedCriteria handleSubquery (InternalPropertyDefinition propDef, final String expr, final String indentation) {
    // Should produce a new IN clause. The target of the criteria should be propDef.type, with the
    // return denoting ids for matched propDef.owner
    
    final String partialPath = propDef.subQuery
    String newExp = expr.substring(partialPath.length() + 1)
    
    DetachedCriteria dc = propDef.owner.handleLookupViaSubquery(newExp)
    if (dc) {
      newExp = newExp.substring(newExp.indexOf('.') + 1)
      
      def filterGroup = parseFilterString( dc, [:], newExp, indentation)
      if (filterGroup) {
        dc.add( filterGroup )
        return dc
      }
    }
    
    null
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
      
      if (criterionList) {
        crit = Restrictions.and( criterionList as Criterion[] )
        
      } else {
        log.trace ("${indentation}  // Failed to evaluate criteria. Ignore this AND")
      }
      log.trace ("${indentation}}")
    } else {
      entries = filterString.split (/\|\|/)
      if (entries.length > 1) {
        
        // Start an OR.
        log.trace ("${indentation}or {")
        List<Criterion> criterionList = []
        
        // Now call out to this method again with each entry.
        entries.each {
          
          // If the value is blank we should not add to thee list.
          Criterion c = parseFilterString (criteria, aliasStack, it, indentation)
          if (c) {
            criterionList << c
          }
        }
        
        if (criterionList) {
          crit = Restrictions.or( criterionList as Criterion[] )
        } else {
          log.trace ("${indentation}  // Failed to evaluate criteria. Ignore this OR")
        }
        log.trace ("${indentation}}")
      } else {
        // Check for negation.
        if (filterString.startsWith("!")) {
          
          log.trace ("${indentation}not {")
          crit = Restrictions.not( parseFilterString (criteria, aliasStack, filterString.substring(1), indentation) )
          Criterion c = parseFilterString (criteria, aliasStack, filterString.substring(1), indentation)
          
          if (c) {
            crit = Restrictions.not( c )
          } else {
            log.trace ("${indentation}  // Failed to evaluate criteria. Ignore this NOT")
          }
          log.trace ("${indentation}}")
          
          if (c) log.debug "Negated whole filter entry"
        } else {
          // Then check for comparator type i.e. gt, eq, lt etc..
          List parts = getComparisonParts (criteria, aliasStack, filterString, indentation)
          log.debug "Got comparision parts ${parts}"
          if (parts) {
            
            def op = parts[0]
            if (Criterion.class.isAssignableFrom(op.class)){
              // Assume all parts are Criterion.
              // And them.              
              crit = Restrictions.and( parts as Criterion[] )
            } else if (parts.size() > 1) {
            
              // Assume normal op.
              // part 1 or 2 could be the property name and, the other is the value.
              def prop = parts[1] ? "${parts[1]}".trim() : null
              def propDef = prop ? DomainUtils.resolveProperty(criteria.criteriaImpl.entityOrClassName, prop, true) : null
              def value = null
              if (parts.size() > 2 && parts.size() <= 4) {
              
                value = parts[2]
                if (!propDef) {
                  // Swap the values and retry.
                  prop = parts[2] ? "${parts[2]}".trim() : null
                  propDef = prop ? DomainUtils.resolveProperty(criteria.criteriaImpl.entityOrClassName, prop, true) : null
                  if (propDef) {
                    value = parts[1]
                    op = invertOp(op)
                  }
                }
              }
              
              if (propDef) {
                if (propDef.filterable) {
                  // If subquery we should call out now.
                  if (propDef.subQuery) {
                    
                    // Call the subquery method on the target.
                    String propName = getAliasedProperty(criteria, aliasStack, propDef.subQuery) as String
                    DetachedCriteria dc = handleSubquery (propDef, filterString, indentation)
                    if (dc) {
                      crit = Subqueries.propertyIn(
                        propName + '.id',
                        handleSubquery (propDef, filterString, indentation)
                      )
                    }
                  } else {
                  
                    def propertyType = propDef.type
                    def propName = getAliasedProperty(criteria, aliasStack, prop) as String
                    
                    if (parts.size() == 2) {
                      
                      log.trace ("${indentation}${op} ${prop}")
                      crit = Restrictions."${op}" (propName)
                      
                    } else {
                    
                      // Can't ilike on none-strings. So we should change back to eq.
                      if (op == 'ilike' && !String.class.isAssignableFrom(propertyType)) {
                        op = 'eqOrIsNull'
                      }
                      
                      log.trace ("${indentation}${op} ${prop}, ${value}")
                      def val = value ? valueConverterService.attemptConversion(propertyType, value) : null
                      
                      log.debug ("Converted ${value} into ${val}")
                      crit = Restrictions."${op}" (propName, val)
                      
                      // The 4th part would be negation for the comparitor. Wrap in a not.
                      if (parts.size() == 4 && parts[3] == true) {
                        log.debug ("Negating the filter")
                        crit = Restrictions.not(crit)
                      }
                    }
                  }
                } else {
                  log.debug "Filter on ${parts} has been disallowed."
                }
              } else {
                log.debug "Could not process op def ${parts}"
              }
            } else {
              log.debug "Could not process op def ${parts}"
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
      //First we split out the incoming query into multiple terms by whitespace, treating quoted chunks as a whole
      String[] splitTerm = term.split( /(?!\B"[^"]*)\s+(?![^"]*"\B)/ )
      // We have now turned something like: `Elvis "The King" Presley` into [Elvis, "The King", Presley]

      // Add a condition for each parameter we wish to search.
      for (String prop : match_in) {
        def propDef = DomainUtils.resolveProperty(criteria.criteriaImpl.entityOrClassName, prop, true)
        
        if (propDef) {
        
          if (propDef.searchable) {
            def propertyType = propDef.type
            def propName = getAliasedProperty(criteria, aliasStack, prop, true) as String
            
            // We use equal unless the compared property is a String then we should use iLIKE
            def op = 'eq'
            if (String.class.isAssignableFrom(propertyType)) {
              // Create a conjunction to AND all the split terms together
              Conjunction termByTermRestrictions = Restrictions.conjunction();
              for (String t : splitTerm) {
                // Remember to replace any leftover quotes with empty space
                termByTermRestrictions.add(Restrictions.ilike("${propName}", "${t.replace("\"", "")}", textMatching))
                log.debug ("Looking for term '${t}' in ${propName}" )
              }
              textMatches << termByTermRestrictions
            } else {
              // Create a conjunction to AND all the split terms together
              Conjunction termByTermRestrictions = Restrictions.conjunction();
              for (String t : splitTerm) {
                // Attempt to convert the value into one comparable with the target.
                def val = valueConverterService.attemptConversion(propertyType, t.replace("\"", ""))
                termByTermRestrictions.add(Restrictions.eq("${propName}", val))
                log.debug ("Converted ${t} into ${val} as type '${propertyType}'")
              }
              textMatches << termByTermRestrictions
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
      
      def propDef = DomainUtils.resolveProperty(target.targetClass, prop, true)
      if (propDef) {
        if (propDef.sortable) {
        
          // Sort direction for this field.
          final String direction = (sortParts.length > 1 ? sortParts[1] : 'asc')?.toLowerCase() == 'desc' ? 'desc' : 'asc'
          
          def propName = getAliasedProperty(target, aliasStack, prop, true) as String
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
    
    // Text searching should be Disjunctive across all properties specified.
    if (textMatches) criterionList << Restrictions.or(textMatches.toArray(new Criterion[textMatches.size()]))

    // Conjunction to ensure results returned match any text searches specified and ALL filters specified.
    if (criterionList) criteria.add(Restrictions.and(criterionList.toArray(new Criterion[criterionList.size()])))

    // Project distinct IDs so as to only include results once on multiple matches.
    criteria.setProjection(
      Projections.distinct(Projections.id())
    )

    criteria
  }

  private final class BatchedStreamIterator implements Iterator {
    
    private final Closure originalQuery
    private final int chunkMaxSize
    private final Class targetClass
    
    private long totalResults = 0
    
    private List currentChunk = null
    private long position = -1
    
    private int currentChunkEnd = 0
    private int currentChunkPosition = -1
    
    private int offset = 0
    
    public BatchedStreamIterator (
        final Class targetClass,
        final int batchSizeSuggestion = 1000,
        final Closure lookupQuery) {
      
      this.targetClass = targetClass
      this.originalQuery = lookupQuery
      this.chunkMaxSize = batchSizeSuggestion
    }
    
    private void fetch() {      
      if (position > -1) {
        // If we still have results do nothing.
        if (currentChunkPosition < currentChunkEnd) return
        
        // Increase the offset.
        offset += (currentChunkEnd + 1)
      } else {
        offset = 0 // First page.
      }
      
      def res = doList (targetClass, ['offset': offset, 'max': chunkMaxSize], {
        // Ensure the owner isn't changed. And run.
        originalQuery.rehydrate(delegate, owner.originalQuery, thisObject).call()
        
        // Cache to help speed up future calls.
        readOnly true
        cache true
      })
      
      // Every time we fetch results we should ensure the total is updated to
      // the current understanding. It's possible for the results-size to change
      // while we chunk things up but that is an acceptable caveat here.
      totalResults = res?.totalCount ?: 0
      
      // Update the chunk vars.
      currentChunk = res
      currentChunkPosition = -1
      currentChunkEnd = currentChunk.size() - 1
    }
    
    // Initialize the values.
    boolean initialized = false
    private void ensureInit() {
      if (!initialized) {
        
        // We should grab the first page of results
        fetch()
        initialized = true
      }
    }

    @Override
    public boolean hasNext () {
      
      // Ensure we have initialized with an intial run at least.
      ensureInit()
      
      // Easiest case is the current chunk has data beyond our pointer.
      if (currentChunkPosition < currentChunkEnd) return true;
      
      // Current chunk exhausted but there are more to fetch.
      // Because of the volatile nature of the query results changing over time,
      // we should fetch the next chunk first to prevent a false positive.
      if (position < totalResults) {
        fetch()
        
        // Providing we got results.
        return (currentChunkEnd > -1)   
      }
      
      // Default is no more results.
      false
    }

    @Override
    public Object next () {
      
      if (!hasNext()) {
        throw new NoSuchElementException("Item chunks exhausted.")
      }
      
      // Otherwise forward pointers
      position++
      currentChunkPosition++
      
      // Return the element.
      
      return currentChunk.get(currentChunkPosition)
    }
    
  }
  
  public Iterator lookupAsBatchedStream (
    final Class c, final String term, final Integer chunkSize = 10, final List filters = [], final List match_in = [], final List sorts = [], final Closure base = null) {
    
    // Results per page, cap at 1000 for safety here.
    final int chunkSizeSuggestion = Math.min(chunkSize, 1000)

    // Return our special wrapper class that should allow for forward iteration
    // through the results.
    new BatchedStreamIterator (c, chunkSizeSuggestion, {
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

    // Results per page, cap at 1000 for safety here.This will probably be capped by the implementing controller to a lower value.
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
          addSorts (delegate, sorts)
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
