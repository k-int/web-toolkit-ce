package com.k_int.web.toolkit

import org.grails.datastore.gorm.GormStaticApi

import com.k_int.web.toolkit.query.JpaCriteriaQueryBackend
import com.k_int.web.toolkit.query.LegacyCriteriaQueryBackend
import com.k_int.web.toolkit.query.LookupQuerySpec
import com.k_int.web.toolkit.query.SimpleLookupQueryBackend
import com.k_int.web.toolkit.utils.GormUtils

import grails.gorm.multitenancy.Tenants
import grails.util.GrailsClassUtils
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Value

@Slf4j
class SimpleLookupService {
  
  String queryBackend = 'legacy'
  boolean allowLegacyQueryBackend = true
  @Value('${k_int.webToolkit.query.jpa.astFilterParserEnabled:false}')
  boolean jpaAstFilterParserEnabled = false
  ValueConverterService valueConverterService
  SimpleLookupQueryBackend simpleLookupQueryBackend
  SimpleLookupQueryBackend legacyCriteriaQueryBackend
  SimpleLookupQueryBackend jpaCriteriaQueryBackend

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

    final LookupQuerySpec querySpec = new LookupQuerySpec(
      term: term,
      matchIn: match_in ?: [],
      filters: filters ?: [],
      sorts: sorts ?: [],
      rootEntityClass: c
    )

    // Return our special wrapper class that should allow for forward iteration
    // through the results.
    new BatchedStreamIterator (c, chunkSizeSuggestion, Optional.ofNullable(tid), {
      // Change the delegate and execute.
      if (base) {
        base.setDelegate(delegate)
        base()
      }

      resolveQueryBackend().apply(delegate, querySpec)
    })
  }
  
  public def lookup (final Class c, final String term, final Integer perPage = 10, final Integer page = 1, final List filters = [], final List match_in = [], final List sorts = [], final Closure base = null) {

    // Results per page, cap at 1000 for safety here. This will probably be capped by the implementing controller to a lower value.
    int pageSize = Math.min(perPage, 1000)

    final LookupQuerySpec querySpec = new LookupQuerySpec(
      term: term,
      matchIn: match_in ?: [],
      filters: filters ?: [],
      sorts: sorts ?: [],
      rootEntityClass: c
    )

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

      resolveQueryBackend().apply(delegate, querySpec)
    })
  }

  public def lookupWithStats (final Class c, final String term, final Integer perPage = 10, final Integer page = 1, final List filters = [], final List match_in = [], final List sorts = [], final Map<String,Closure> extraStats = null, final Closure base = null) {
    // Results per page, cap at 1000 for safety here.This will probably be capped by the implementing controller to a lower value.
    int pageSize = Math.min(perPage, 1000)

    final LookupQuerySpec querySpec = new LookupQuerySpec(
      term: term,
      matchIn: match_in ?: [],
      filters: filters ?: [],
      sorts: sorts ?: [],
      rootEntityClass: c
    )
    
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
        
        resolveQueryBackend().apply(delegate, querySpec)
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

  private SimpleLookupQueryBackend resolveQueryBackend() {
    if (simpleLookupQueryBackend) {
      return simpleLookupQueryBackend
    }

    final String selectedBackend = (queryBackend ?: 'legacy').toLowerCase()

    final SimpleLookupQueryBackend legacyBackend =
      legacyCriteriaQueryBackend ?: new LegacyCriteriaQueryBackend(valueConverterService)

    if ('jpa' == selectedBackend) {
      return jpaCriteriaQueryBackend ?: newJpaQueryBackend(legacyBackend, jpaAstFilterParserEnabled)
    }

    if (!allowLegacyQueryBackend) {
      throw new UnsupportedOperationException('Legacy query backend is disabled. Configure queryBackend=jpa.')
    }

    log.warn("SimpleLookupService is using deprecated legacy query backend. Configure queryBackend='jpa' for Grails 7 readiness.")
    legacyBackend
  }

  protected SimpleLookupQueryBackend newJpaQueryBackend(
    final SimpleLookupQueryBackend legacyBackend,
    final boolean useAstFilterParser
  ) {
    new JpaCriteriaQueryBackend(legacyBackend, useAstFilterParser)
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

    def res
    if (methodPars) {
      res = c.createCriteria()."${method}" (methodPars, newCrit)
    } else {
      res = c.createCriteria()."${method}" (newCrit)
    }

    // Hibernate/Grails upgrades can surface duplicate root entities for joined OR
    // criteria in paged queries; collapse duplicates by entity id while preserving
    // the existing list container.
    if (method == 'list' && methodPars && (res instanceof List)) {
      deduplicateEntityResultsById(res as List)
    }
    
    res
  }

  private static void deduplicateEntityResultsById(final List results) {
    if (!results || results.size() < 2) return
    if (!results[0]?.metaClass?.hasProperty(results[0], 'id')) return

    final Set seenIds = new LinkedHashSet()
    final Iterator it = results.iterator()
    while (it.hasNext()) {
      final Object item = it.next()
      final Object idVal = item?.id
      if (seenIds.contains(idVal)) {
        it.remove()
      } else {
        seenIds.add(idVal)
      }
    }
  }
}
