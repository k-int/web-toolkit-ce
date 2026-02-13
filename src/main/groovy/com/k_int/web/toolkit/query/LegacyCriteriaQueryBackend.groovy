package com.k_int.web.toolkit.query

import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.tree.ParseTreeWalker
import org.hibernate.criterion.Conjunction
import org.hibernate.criterion.CriteriaSpecification
import org.hibernate.criterion.Criterion
import org.hibernate.criterion.MatchMode
import org.hibernate.criterion.Order
import org.hibernate.criterion.Restrictions
import org.hibernate.sql.JoinType

import com.k_int.web.toolkit.ValueConverterService
import com.k_int.web.toolkit.grammar.SimpleLookupServiceListenerWtk
import com.k_int.web.toolkit.grammar.SimpleLookupWtkLexer
import com.k_int.web.toolkit.grammar.SimpleLookupWtkParser
import com.k_int.web.toolkit.grammar.SimpleLookupWtkParser.FiltersContext
import com.k_int.web.toolkit.utils.DomainUtils

import groovy.util.logging.Slf4j

@Slf4j
class LegacyCriteriaQueryBackend implements SimpleLookupQueryBackend {
  private final ValueConverterService valueConverterService

  private static final class PropertyDef extends HashMap<String, String> {
    @Override
    String toString () {
      String al = this.get('alias')
      "${al ? al + '.' : ''}${this.get('property')}".toString()
    }

    Object asType(Class type) {
      if (type == String) {
        return this.toString()
      }
      super.asType(type)
    }
  }

  LegacyCriteriaQueryBackend(final ValueConverterService valueConverterService) {
    this.valueConverterService = valueConverterService
  }

  @Override
  void apply(final Object criteriaTarget, final LookupQuerySpec spec) {
    final Map<String, String> aliasStack = [:]
    final List<Criterion> criterionList = []

    final Criterion filterGroup = parseFilters(criteriaTarget, aliasStack, spec.filters, spec.rootEntityClass)
    if (filterGroup) {
      criterionList << filterGroup
    }

    final List<Criterion> textMatches = getTextMatches(
      criteriaTarget, aliasStack, spec.term, spec.matchIn, spec.rootEntityClass)

    if (textMatches) {
      criterionList << Restrictions.or(textMatches.toArray(new Criterion[textMatches.size()]))
    }

    if (criterionList) {
      addCriterionToTarget(criteriaTarget, Restrictions.and(criterionList.toArray(new Criterion[criterionList.size()])))
    }

    setDistinctRoot(criteriaTarget)

    if (spec.sorts) {
      addSorts(criteriaTarget, spec.sorts)
    }
  }

  private static final String checkAlias(def target, final Map<String, String> aliasStack, String dotNotationString, boolean leftJoin) {
    log.debug ("Checking for ${dotNotationString}")

    def str = aliasStack[dotNotationString]
    if (!str) {
      log.debug "Full match not found..."

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

      if (counter <= props.length) {
        for (int i=(counter-1); i<props.length; i++) {
          String aliasVal = alias ? "${alias}.${props[i]}" : "${props[i]}"
          alias = "alias${aliasStack.size()}"

          log.debug ("Creating alias: ${aliasVal} -> ${alias}")
          target.criteria.createAlias(aliasVal, alias, (leftJoin ? JoinType.LEFT_OUTER_JOIN : JoinType.INNER_JOIN))

          propStr = i>0 ? "${propStr}.${props[i]}" : "${props[i]}"
          aliasStack[propStr] = alias
          log.debug ("Added quick string: ${propStr} -> ${alias}")
        }
      }

      str = alias
    }

    str
  }

  private static final Map getAliasedProperty (def target, final Map<String, String> aliasStack, final String propname, final boolean leftJoin = false) {
    log.debug "Looking for property ${propname}"

    String[] levels = propname.split("\\.")
    PropertyDef ret = new PropertyDef()
    ret.putAll([
      'alias' : levels.length > 1 ? checkAlias ( target, aliasStack, levels[0..(levels.length - 2)].join('.'), leftJoin) : '',
      'property' : levels[levels.length - 1]
    ])

    ret
  }

  private Criterion parseFilterString ( final Object aliasTarget, final Map<String, String> aliasStack, String filterString, String indentation = null, final Class rootEntityClass = null ) {
    final CharStream input = CharStreams.fromString(filterString)
    final SimpleLookupWtkLexer lexer = new SimpleLookupWtkLexer(input);
    final CommonTokenStream tokens = new CommonTokenStream(lexer);
    final SimpleLookupWtkParser parser = new SimpleLookupWtkParser(tokens);
    final FiltersContext filters = parser.filters();

    log.info "Parse tree: ${filters.toStringTree(parser)}"

    Class resolvedRootClass = rootEntityClass
    String rootEntityName = rootEntityClass?.name
    SimpleLookupServiceListenerWtk listener = new SimpleLookupServiceListenerWtk(
      log, valueConverterService, aliasTarget, resolvedRootClass, rootEntityName, aliasStack)
    ParseTreeWalker.DEFAULT.walk(listener, filters);

    Criterion result = listener.result
    result
  }

  private Criterion parseFilters ( final Object aliasTarget, final Map<String, String> aliasStack, final Collection<String> filters, final Class rootEntityClass = null ) {
    if (!filters) return null
    parseFilterString(aliasTarget, aliasStack, filters.join('\n'), null, rootEntityClass)
  }

  private List<Criterion> getTextMatches (final Object criteriaTarget, final Map<String, String> aliasStack, final String term, final match_in, final Class rootEntityClass, MatchMode textMatching = MatchMode.ANYWHERE) {
    List<Criterion> textMatches = []
    if (term) {
      String[] splitTerm = term.split( /(?!\B"[^"]*)\s+(?![^"]*"\B)/ )

      for (String prop : match_in) {
        def propDef = DomainUtils.resolveProperty(rootEntityClass, prop, true)

        if (propDef) {
          if (propDef.searchable) {
            def propertyType = propDef.type
            def propName = getAliasedProperty(criteriaTarget, aliasStack, prop, true) as String

            if (String.class.isAssignableFrom(propertyType)) {
              Conjunction termByTermRestrictions = Restrictions.conjunction()
              for (String t : splitTerm) {
                termByTermRestrictions.add(Restrictions.ilike("${propName}", "${t.replace("\"", "")}", textMatching))
                log.debug ("Looking for term '${t}' in ${propName}" )
              }
              textMatches << termByTermRestrictions
            } else {
              Conjunction termByTermRestrictions = Restrictions.conjunction()
              for (String t : splitTerm) {
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

  private void addSorts (final target, final sorts) {
    final Map<String, String> aliasStack = [:]

    sorts.each { String sort ->
      final String[] sortParts = sort.split(/;/)
      final String prop = sortParts[0]

      def propDef = DomainUtils.resolveProperty(target.targetClass, prop, true)
      if (propDef) {
        if (propDef.sortable) {
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
}
