package com.k_int.web.toolkit

import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.tree.ParseTreeWalker
import org.grails.datastore.gorm.GormStaticApi
import org.hibernate.criterion.*
import org.hibernate.criterion.CriteriaSpecification
import org.hibernate.sql.JoinType

import com.k_int.web.toolkit.grammar.SimpleLookupServiceListenerWtk
import com.k_int.web.toolkit.grammar.SimpleLookupWtkLexer
import com.k_int.web.toolkit.grammar.SimpleLookupWtkParser
import com.k_int.web.toolkit.grammar.SimpleLookupWtkParser.FiltersContext
import com.k_int.web.toolkit.utils.DomainUtils
import com.k_int.web.toolkit.utils.GormUtils

import grails.gorm.multitenancy.Tenants
import grails.util.GrailsClassUtils
import groovy.util.logging.Slf4j
import java.util.concurrent.Callable

@Slf4j
class SimpleLookupService {
  
  ValueConverterService valueConverterService

  public static class PropertyDef extends HashMap<String, String> {
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

  private Criterion parseFilterString ( final Object aliasTarget, final Map<String, String> aliasStack, String filterString, String indentation = null, final Class rootEntityClass = null ) {
    final CharStream input = CharStreams.fromString(filterString)
    final SimpleLookupWtkLexer lexer = new SimpleLookupWtkLexer(input);
    final CommonTokenStream tokens = new CommonTokenStream(lexer);
    final SimpleLookupWtkParser parser = new SimpleLookupWtkParser(tokens);
    final FiltersContext filters = parser.filters();
    
    log.info "Parse tree: ${filters.toStringTree(parser)}"
    
    // We have parsed the whole string and now we have the final context.
    
    // Create a listener to act on the tree.
    Class resolvedRootClass = rootEntityClass
    String rootEntityName = rootEntityClass?.name
    SimpleLookupServiceListenerWtk listener = new SimpleLookupServiceListenerWtk(
			log, valueConverterService, aliasTarget, resolvedRootClass, rootEntityName, aliasStack)
    ParseTreeWalker.DEFAULT.walk(listener, filters);
    
    Criterion result = listener.result
    
    return result
  }
  
  private Criterion parseFilters ( final Object aliasTarget, final Map<String, String> aliasStack, final Collection<String> filters, final Class rootEntityClass = null ) {
    // Jump out of the routine immediately if no filters or empty list
    if (!filters) return null
    
    // We parse the filters and build up the criteria.
		return parseFilterString (aliasTarget, aliasStack, filters.join('\n'), null, rootEntityClass)
  }
  
  private List<Criterion> getTextMatches (final Object criteriaTarget, final Map<String, String> aliasStack, final String term, final match_in, final Class rootEntityClass, MatchMode textMatching = MatchMode.ANYWHERE) {
    List<Criterion> textMatches = []
    if (term) {
      //First we split out the incoming query into multiple terms by whitespace, treating quoted chunks as a whole
      String[] splitTerm = term.split( /(?!\B"[^"]*)\s+(?![^"]*"\B)/ )
      // We have now turned something like: `Elvis "The King" Presley` into [Elvis, "The King", Presley]

      // Add a condition for each parameter we wish to search.
      for (String prop : match_in) {
        def propDef = DomainUtils.resolveProperty(rootEntityClass, prop, true)
        
        if (propDef) {
        
          if (propDef.searchable) {
            def propertyType = propDef.type
            def propName = getAliasedProperty(criteriaTarget, aliasStack, prop, true) as String
            
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

  private void applyLookupCriteria(final Object criteriaTarget, final String term, final match_in, final filters, final Class rootEntityClass) {
    Map<String, String> aliasStack = [:]
    List<Criterion> criterionList = []

    // Filters...
    final Criterion filterGroup = parseFilters(criteriaTarget, aliasStack, filters, rootEntityClass)
    if (filterGroup) {
      criterionList << filterGroup
    }

    // Text matching uses ilike ops for string property and eq for all others.
    List<Criterion> textMatches = getTextMatches(criteriaTarget, aliasStack, term, match_in, rootEntityClass)
    
    // Text searching should be Disjunctive across all properties specified.
    if (textMatches) criterionList << Restrictions.or(textMatches.toArray(new Criterion[textMatches.size()]))

    // Conjunction to ensure results returned match any text searches specified and ALL filters specified.
    if (criterionList) {
      addCriterionToTarget(criteriaTarget, Restrictions.and(criterionList.toArray(new Criterion[criterionList.size()])))
    }
  }

  private void addCriterionToTarget(final Object criteriaTarget, final Criterion criterion) {
    if (!criterion) return
    if (criteriaTarget?.metaClass?.respondsTo(criteriaTarget, 'add', Criterion)) {
      criteriaTarget.add(criterion)
    } else if (criteriaTarget?.metaClass?.hasProperty(criteriaTarget, 'criteria')) {
      criteriaTarget.criteria.add(criterion)
    }
  }

  private void setDistinctRoot(final Object criteriaTarget) {
    if (criteriaTarget?.metaClass?.respondsTo(criteriaTarget, 'resultTransformer', Object)) {
      criteriaTarget.resultTransformer(CriteriaSpecification.DISTINCT_ROOT_ENTITY)
    } else if (criteriaTarget?.metaClass?.respondsTo(criteriaTarget, 'setResultTransformer', Object)) {
      criteriaTarget.setResultTransformer(CriteriaSpecification.DISTINCT_ROOT_ENTITY)
    } else if (criteriaTarget?.metaClass?.hasProperty(criteriaTarget, 'criteria')) {
      criteriaTarget.criteria.setResultTransformer(CriteriaSpecification.DISTINCT_ROOT_ENTITY)
    }
  }

  private final class BatchedStreamIterator implements Iterator {
    
    private final Closure originalQuery
    private final int chunkMaxSize
    private final Class targetClass
		private final Optional<Serializable> tenantId
    
    private long totalResults = 0
    
    private List currentChunk = null
    private long position = -1
    
    private int currentChunkEnd = 0
    private int currentChunkPosition = -1
    
    private int offset = 0
    
    public BatchedStreamIterator (
        final Class targetClass,
        final int batchSizeSuggestion = 1000,
				final Optional<Serializable> tenantId,
        final Closure lookupQuery) {
      
      this.targetClass = targetClass
      this.originalQuery = lookupQuery
      this.chunkMaxSize = batchSizeSuggestion
			this.tenantId = tenantId
    }
		
		private <T> T doInSession( Closure<T> lookup ) {
			GormStaticApi<?> api = GormUtils.gormStaticApi(targetClass)
			
			T val = tenantId
				.map({ tenantId -> api.withTenant( tenantId, lookup ) })
				.orElseGet({ api.withTransaction(lookup) })
				
			val
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
			
			doInSession() {
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
		
		// Not Ideal to have to catch the exception but there isn't a better way ATM
		
		Serializable tid
		try {
			tid = Tenants.currentId()
		} catch( Exception e ) {
			tid = null;
		}

    // Return our special wrapper class that should allow for forward iteration
    // through the results.
    new BatchedStreamIterator (c, chunkSizeSuggestion, Optional.ofNullable(tid), {
      // Change the delegate and execute.
      if (base) {
        base.setDelegate(delegate)
        base()
      }

      // Add lookup.
      applyLookupCriteria(delegate, term, match_in, filters, c)
      setDistinctRoot(delegate)
      
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
      applyLookupCriteria(delegate, term, match_in, filters, c)
      setDistinctRoot(delegate)
      
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
        applyLookupCriteria(delegate, term, match_in, filters, c)
        setDistinctRoot(delegate)
        
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
