package com.k_int.web.toolkit.query

import java.time.LocalDate

import com.k_int.web.toolkit.utils.DomainUtils
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.types.Association

class JpaCriteriaQueryBackend implements SimpleLookupQueryBackend {
  private final SimpleLookupQueryBackend fallbackBackend

  JpaCriteriaQueryBackend(final SimpleLookupQueryBackend fallbackBackend = null) {
    this.fallbackBackend = fallbackBackend
  }

  @Override
  void apply(final Object criteriaTarget, final LookupQuerySpec spec) {
    if (applySupportedFilterSlice(criteriaTarget, spec)) {
      return
    }

    if (fallbackBackend) {
      fallbackBackend.apply(criteriaTarget, spec)
      return
    }

    throw new UnsupportedOperationException('JpaCriteriaQueryBackend can only handle a limited equality slice without fallback.')
  }

  private boolean applySupportedFilterSlice(final Object criteriaTarget, final LookupQuerySpec spec) {
    if (!spec?.rootEntityClass) return false

    final ParseState state = new ParseState(nextParamIdx: 0, rootEntityClass: spec.rootEntityClass)
    final List<ParsedFilter> clauses = []

    if (spec?.filters) {
      final ParsedFilter parsedFilters = parseSupportedFilters(spec.filters as List, state)
      if (!parsedFilters) return false
      clauses << parsedFilters
    }

    final ParsedFilter textSearch = parseSupportedTextSearch(spec.term as String, spec.matchIn as List, spec.rootEntityClass, state)
    if (textSearch == UNSUPPORTED_SLICE) return false
    if (textSearch) {
      clauses << textSearch
    }

    if (!clauses) return false
    final ParsedFilter parsed = combineParsed(clauses, 'and')
    if (!parsed) return false

    final String whereClause = parsed.whereClause
    final Map<String, Object> params = parsed.params
    final String entityName = spec.rootEntityClass.name
    final String fromClause = parsed.usesItemJoin
      ? ' from ' + entityName + ' r join r.checklists cl join cl.items i '
      : parsed.usesChecklistJoin
        ? ' from ' + entityName + ' r join r.checklists cl '
      : ' from ' + entityName + ' r '
    final String query = 'select distinct r.id' + fromClause + 'where ' + whereClause
    final List ids = spec.rootEntityClass.executeQuery(
      query,
      params
    ) as List

    if (ids) {
      criteriaTarget.'in'('id', ids)
    } else {
      criteriaTarget.sqlRestriction('1 = 0')
    }

    if (spec.sorts) {
      applySimpleSorts(criteriaTarget, spec.sorts)
    }

    true
  }

  private ParsedFilter parseSupportedFilters(final List filters, final ParseState state) {
    final List<String> expressions = (filters ?: [])
      .collect { (it as String)?.trim() }
      .findAll { it }
    if (!expressions) return null

    final List<ParsedFilter> parsedParts = []
    for (String expression : expressions) {
      final ParsedFilter parsed = parseSupportedFilterExpression(expression, state)
      if (!parsed) return null
      parsedParts << parsed
    }
    combineParsed(parsedParts, 'and')
  }

  private ParsedFilter parseSupportedFilterExpression(final String filter, final ParseState state) {
    final ParsedFilter parsed = parseOrExpression(filter?.trim(), state)
    if (!parsed) return null
    parsed
  }

  private ParsedFilter parseOrExpression(final String expression, final ParseState state) {
    final String input = stripOuterParentheses(expression?.trim())
    if (!input) return null

    final List<String> parts = splitTopLevel(input, '||')
    if (parts.size() <= 1) return parseAndExpression(input, state)

    final List<ParsedFilter> nodes = []
    for (String part : parts) {
      final ParsedFilter parsed = parseAndExpression(part, state)
      if (!parsed) return null
      nodes << parsed
    }
    combineParsed(nodes, 'or')
  }

  private ParsedFilter parseAndExpression(final String expression, final ParseState state) {
    final String input = stripOuterParentheses(expression?.trim())
    if (!input) return null

    final List<String> parts = splitTopLevel(input, '&&')
    if (parts.size() <= 1) return parseUnaryExpression(input, state)

    final List<ParsedFilter> nodes = []
    for (String part : parts) {
      final ParsedFilter parsed = parseUnaryExpression(part, state)
      if (!parsed) return null
      nodes << parsed
    }
    combineParsed(nodes, 'and')
  }

  private ParsedFilter parseUnaryExpression(final String expression, final ParseState state) {
    final String input = stripOuterParentheses(expression?.trim())
    if (!input) return null

    if (input.startsWith('!')) {
      final ParsedFilter inner = parseOrExpression(input.substring(1).trim(), state)
      if (!inner) return null

      if (inner.usesItemJoin) {
        final String subqueryClause = rewriteJoinAliasesForSubquery(inner.whereClause, 'ncl', 'ni')
        return new ParsedFilter(
          whereClause: '(not exists (select 1 from r.checklists ncl join ncl.items ni where ' + subqueryClause + '))',
          params: inner.params,
          usesItemJoin: false,
          usesChecklistJoin: false
        )
      }

      if (inner.usesChecklistJoin) {
        final String subqueryClause = rewriteJoinAliasesForSubquery(inner.whereClause, 'ncl', null)
        return new ParsedFilter(
          whereClause: '(not exists (select 1 from r.checklists ncl where ' + subqueryClause + '))',
          params: inner.params,
          usesItemJoin: false,
          usesChecklistJoin: false
        )
      }

      return new ParsedFilter(
        whereClause: '(not (' + inner.whereClause + '))',
        params: inner.params,
        usesItemJoin: false,
        usesChecklistJoin: false
      )
    }

    parseAtomicPredicate(input, state)
  }

  private ParsedFilter parseAtomicPredicate(final String expression, final ParseState state) {
    final String input = stripOuterParentheses(expression?.trim())
    if (!input) return null

    final ParsedPredicate predicate = parseSupportedPredicate(input, state)
    if (!predicate) return null
    new ParsedFilter(
      whereClause: predicate.clause,
      params: predicate.params,
      usesItemJoin: predicate.usesItemJoin,
      usesChecklistJoin: predicate.usesChecklistJoin
    )
  }

  private ParsedPredicate parseSupportedPredicate(final String expression, final ParseState state) {
    final def isSetMatcher = (expression =~ /(?i)^checklists\.items\.(outcome|status)\s+isSet$/)
    if (isSetMatcher.matches()) {
      final String field = isSetMatcher[0][1].toLowerCase()
      return new ParsedPredicate(clause: "i.${field} is not null", params: [:], usesItemJoin: true, usesChecklistJoin: true)
    }

    final def isNotSetMatcher = (expression =~ /(?i)^checklists\.items\.(outcome|status)\s+isNotSet$/)
    if (isNotSetMatcher.matches()) {
      final String field = isNotSetMatcher[0][1].toLowerCase()
      return new ParsedPredicate(clause: "i.${field} is null", params: [:], usesItemJoin: true, usesChecklistJoin: true)
    }

    final def isEmptyMatcher = (expression =~ /(?i)^checklists\.items\.(outcome|status)\s+isEmpty$/)
    if (isEmptyMatcher.matches()) {
      final String field = isEmptyMatcher[0][1].toLowerCase()
      return new ParsedPredicate(clause: "i.${field} = ''", params: [:], usesItemJoin: true, usesChecklistJoin: true)
    }

    final def isNotEmptyMatcher = (expression =~ /(?i)^checklists\.items\.(outcome|status)\s+isNotEmpty$/)
    if (isNotEmptyMatcher.matches()) {
      final String field = isNotEmptyMatcher[0][1].toLowerCase()
      return new ParsedPredicate(
        clause: "(i.${field} is not null and i.${field} <> '')",
        params: [:],
        usesItemJoin: true,
        usesChecklistJoin: true
      )
    }

    final def nullMatcher = (expression =~ /(?i)^checklists\.items\.(outcome|status)\s+isNull$/)
    if (nullMatcher.matches()) {
      final String field = nullMatcher[0][1].toLowerCase()
      return new ParsedPredicate(clause: "i.${field} is null", params: [:], usesItemJoin: true, usesChecklistJoin: true)
    }

    final def notNullMatcher = (expression =~ /(?i)^checklists\.items\.(outcome|status)\s+isNotNull$/)
    if (notNullMatcher.matches()) {
      final String field = notNullMatcher[0][1].toLowerCase()
      return new ParsedPredicate(clause: "i.${field} is not null", params: [:], usesItemJoin: true, usesChecklistJoin: true)
    }

    final def eqMatcher = (expression =~ /^checklists\.items\.(outcome|status)==(.+)$/)
    if (eqMatcher.matches()) {
      final String field = eqMatcher[0][1].toLowerCase()
      final String paramName = nextParam(state)
      return new ParsedPredicate(clause: "i.${field} = :${paramName}", params: [(paramName): eqMatcher[0][2]], usesItemJoin: true, usesChecklistJoin: true)
    }

    final def ieqMatcher = (expression =~ /(?i)^checklists\.items\.(outcome|status)=i=(.+)$/)
    if (ieqMatcher.matches()) {
      final String field = ieqMatcher[0][1].toLowerCase()
      final String paramName = nextParam(state)
      final String value = (ieqMatcher[0][2] as String)?.toLowerCase()
      return new ParsedPredicate(clause: "lower(i.${field}) = :${paramName}", params: [(paramName): value], usesItemJoin: true, usesChecklistJoin: true)
    }

    final def neqMatcher = (expression =~ /^checklists\.items\.(outcome|status)!=(.+)$/)
    if (neqMatcher.matches()) {
      // Legacy criteria path uses neOrIsNotNull semantics.
      final String field = neqMatcher[0][1].toLowerCase()
      final String paramName = nextParam(state)
      return new ParsedPredicate(
        clause: "(i.${field} <> :${paramName} or i.${field} is not null)",
        params: [(paramName): neqMatcher[0][2]],
        usesItemJoin: true,
        usesChecklistJoin: true
      )
    }

    final def checklistIsEmptyMatcher = (expression =~ /(?i)^checklists\.name\s+isEmpty$/)
    if (checklistIsEmptyMatcher.matches()) {
      return new ParsedPredicate(
        clause: "cl.name = ''",
        params: [:],
        usesChecklistJoin: true
      )
    }

    final def checklistIsNotEmptyMatcher = (expression =~ /(?i)^checklists\.name\s+isNotEmpty$/)
    if (checklistIsNotEmptyMatcher.matches()) {
      return new ParsedPredicate(
        clause: "(cl.name is not null and cl.name <> '')",
        params: [:],
        usesChecklistJoin: true
      )
    }

    final def itemContainsMatcher = (expression =~ /^checklists\.items\.(outcome|status)=~(.+)$/)
    if (itemContainsMatcher.matches()) {
      final String field = itemContainsMatcher[0][1].toLowerCase()
      final String paramName = nextParam(state)
      final String value = '%' + ((itemContainsMatcher[0][2] as String)?.toLowerCase()) + '%'
      return new ParsedPredicate(
        clause: "lower(i.${field}) like :${paramName}",
        params: [(paramName): value],
        usesItemJoin: true,
        usesChecklistJoin: true
      )
    }

    final def itemNotContainsMatcher = (expression =~ /^checklists\.items\.(outcome|status)!~(.+)$/)
    if (itemNotContainsMatcher.matches()) {
      final String field = itemNotContainsMatcher[0][1].toLowerCase()
      final String paramName = nextParam(state)
      final String value = '%' + ((itemNotContainsMatcher[0][2] as String)?.toLowerCase()) + '%'
      return new ParsedPredicate(
        clause: "(not (lower(i.${field}) like :${paramName}))",
        params: [(paramName): value],
        usesItemJoin: true,
        usesChecklistJoin: true
      )
    }

    final def rootIsEmptyMatcher = (expression =~ /(?i)^(name)\s+isEmpty$/)
    if (rootIsEmptyMatcher.matches()) {
      return new ParsedPredicate(
        clause: "r.name = ''",
        params: [:],
        usesItemJoin: false,
        usesChecklistJoin: false
      )
    }

    final def rootIsNotEmptyMatcher = (expression =~ /(?i)^(name)\s+isNotEmpty$/)
    if (rootIsNotEmptyMatcher.matches()) {
      return new ParsedPredicate(
        clause: "(r.name is not null and r.name <> '')",
        params: [:],
        usesItemJoin: false,
        usesChecklistJoin: false
      )
    }

    final ParsedPredicate genericRangeComparison = parseGenericRangeComparisonPredicate(expression, state)
    if (genericRangeComparison) {
      return genericRangeComparison
    }

    final ParsedPredicate genericComparison = parseGenericComparisonPredicate(expression, state)
    if (genericComparison) {
      return genericComparison
    }

    final ParsedPredicate genericNullStyle = parseGenericNullStylePredicate(expression, state)
    if (genericNullStyle) {
      return genericNullStyle
    }

    null
  }

  private ParsedPredicate parseGenericRangeComparisonPredicate(final String expression, final ParseState state) {
    final def rangeMatcher = (expression =~ /^(.+?)(<=|<)([A-Za-z_]\w*(?:\.[A-Za-z_]\w*)*)(<=|<)(.+)$/)
    if (rangeMatcher.matches()) {
      final String leftValueRaw = rangeMatcher[0][1]?.trim()
      final String leftOp = rangeMatcher[0][2]
      final String propPath = rangeMatcher[0][3]
      final String rightOp = rangeMatcher[0][4]
      final String rightValueRaw = rangeMatcher[0][5]?.trim()

      final GenericPathResolution resolved = resolveGenericFilterPath(state.rootEntityClass, propPath)
      if (!resolved || !isComparableFilterType(resolved.terminalType)) return null

      final Object leftValue = coerceGenericComparableValue(resolved.terminalType, leftValueRaw)
      final Object rightValue = coerceGenericComparableValue(resolved.terminalType, rightValueRaw)
      if (leftValue == COERCE_FAILED || rightValue == COERCE_FAILED) return null

      final String lp = nextParam(state)
      final String rp = nextParam(state)
      final String leftComparator = (leftOp == '<') ? '>' : '>='
      final String rightComparator = (rightOp == '<') ? '<' : '<='

      return buildGenericFieldParamPredicate(
        resolved.parts,
        "(__FIELD__ ${leftComparator} :${lp} and __FIELD__ ${rightComparator} :${rp})",
        [(lp): leftValue, (rp): rightValue]
      )
    }

    final def cmpMatcher = (expression =~ /^([A-Za-z_]\w*(?:\.[A-Za-z_]\w*)*)(>=|<=|>|<)(.+)$/)
    if (cmpMatcher.matches()) {
      final String propPath = cmpMatcher[0][1]
      final String op = cmpMatcher[0][2]
      final String valueRaw = cmpMatcher[0][3]?.trim()

      final GenericPathResolution resolved = resolveGenericFilterPath(state.rootEntityClass, propPath)
      if (!resolved || !isComparableFilterType(resolved.terminalType)) return null

      final Object value = coerceGenericComparableValue(resolved.terminalType, valueRaw)
      if (value == COERCE_FAILED) return null

      final String paramName = nextParam(state)
      return buildGenericFieldParamPredicate(
        resolved.parts,
        "__FIELD__ ${op} :${paramName}",
        [(paramName): value]
      )
    }

    null
  }

  private ParsedPredicate parseGenericComparisonPredicate(final String expression, final ParseState state) {
    final def ieqMatcher = (expression =~ /(?i)^([A-Za-z_]\w*(?:\.[A-Za-z_]\w*)*)=i=(.+)$/)
    if (ieqMatcher.matches()) {
      final String propPath = ieqMatcher[0][1]
      final GenericPathResolution resolved = resolveGenericFilterPath(state.rootEntityClass, propPath)
      if (!resolved || !String.isAssignableFrom(resolved.terminalType)) return null
      final String paramName = nextParam(state)
      final String value = (ieqMatcher[0][2] as String)?.toLowerCase()
      return buildGenericFieldParamPredicate(resolved.parts, "lower(__FIELD__) = :${paramName}", [(paramName): value])
    }

    final def containsMatcher = (expression =~ /^([A-Za-z_]\w*(?:\.[A-Za-z_]\w*)*)=~(.+)$/)
    if (containsMatcher.matches()) {
      final String propPath = containsMatcher[0][1]
      final GenericPathResolution resolved = resolveGenericFilterPath(state.rootEntityClass, propPath)
      if (!resolved || !String.isAssignableFrom(resolved.terminalType)) return null
      final String paramName = nextParam(state)
      final String value = '%' + ((containsMatcher[0][2] as String)?.toLowerCase()) + '%'
      return buildGenericFieldParamPredicate(resolved.parts, "lower(__FIELD__) like :${paramName}", [(paramName): value])
    }

    final def notContainsMatcher = (expression =~ /^([A-Za-z_]\w*(?:\.[A-Za-z_]\w*)*)!~(.+)$/)
    if (notContainsMatcher.matches()) {
      final String propPath = notContainsMatcher[0][1]
      final GenericPathResolution resolved = resolveGenericFilterPath(state.rootEntityClass, propPath)
      if (!resolved || !String.isAssignableFrom(resolved.terminalType)) return null
      final String paramName = nextParam(state)
      final String value = '%' + ((notContainsMatcher[0][2] as String)?.toLowerCase()) + '%'
      return buildGenericFieldParamPredicate(resolved.parts, "(not (lower(__FIELD__) like :${paramName}))", [(paramName): value])
    }

    final def eqMatcher = (expression =~ /^([A-Za-z_]\w*(?:\.[A-Za-z_]\w*)*)==(.+)$/)
    if (eqMatcher.matches()) {
      final String propPath = eqMatcher[0][1]
      final GenericPathResolution resolved = resolveGenericFilterPath(state.rootEntityClass, propPath)
      if (!resolved) return null
      final Object value = coerceGenericFilterValue(resolved.terminalType, (eqMatcher[0][2] as String)?.trim())
      if (value == COERCE_FAILED) return null
      final String paramName = nextParam(state)
      return buildGenericFieldParamPredicate(resolved.parts, "__FIELD__ = :${paramName}", [(paramName): value])
    }

    final def neqMatcher = (expression =~ /^([A-Za-z_]\w*(?:\.[A-Za-z_]\w*)*)!=(.+)$/)
    if (neqMatcher.matches()) {
      final String propPath = neqMatcher[0][1]
      final GenericPathResolution resolved = resolveGenericFilterPath(state.rootEntityClass, propPath)
      if (!resolved) return null
      final Object value = coerceGenericFilterValue(resolved.terminalType, (neqMatcher[0][2] as String)?.trim())
      if (value == COERCE_FAILED) return null
      final String paramName = nextParam(state)
      return buildGenericFieldParamPredicate(resolved.parts, "__FIELD__ <> :${paramName}", [(paramName): value])
    }

    null
  }

  private ParsedPredicate parseGenericNullStylePredicate(final String expression, final ParseState state) {
    final def matcher = (expression =~ /(?i)^([A-Za-z_]\w*(?:\.[A-Za-z_]\w*)*)\s+(isNull|isNotNull|isSet|isNotSet)$/)
    if (!matcher.matches()) return null

    final String propPath = matcher[0][1]
    final String op = (matcher[0][2] as String)?.toLowerCase()
    final GenericPathResolution resolved = resolveGenericFilterPath(state.rootEntityClass, propPath)
    if (!resolved) return null

    final boolean wantsNull = ('isnull' == op || 'isnotset' == op)
    final String comparator = wantsNull ? ' is null' : ' is not null'
    final String clause = (resolved.parts.size() == 1)
      ? "r.${resolved.parts[0]}${comparator}"
      : buildExistsFieldPredicateClause(resolved.parts, comparator)

    new ParsedPredicate(
      clause: clause,
      params: [:],
      usesItemJoin: false,
      usesChecklistJoin: false
    )
  }

  private static String stripOuterParentheses(final String input) {
    if (!input) return input
    String out = input.trim()
    boolean changed = true
    while (changed && out.startsWith('(') && out.endsWith(')')) {
      changed = false
      int depth = 0
      boolean wraps = true
      for (int i = 0; i < out.length(); i++) {
        char c = out.charAt(i)
        if (c == '(') depth++
        else if (c == ')') depth--
        if (depth == 0 && i < out.length() - 1) {
          wraps = false
          break
        }
        if (depth < 0) return input
      }
      if (wraps && depth == 0) {
        out = out.substring(1, out.length() - 1).trim()
        changed = true
      }
    }
    out
  }

  private static List<String> splitTopLevel(final String input, final String operator) {
    final List<String> parts = []
    int depth = 0
    int start = 0
    int i = 0
    while (i < input.length()) {
      char c = input.charAt(i)
      if (c == '(') depth++
      else if (c == ')') depth--

      if (depth == 0 && i + operator.length() <= input.length() && input.substring(i, i + operator.length()) == operator) {
        parts << input.substring(start, i).trim()
        i += operator.length()
        start = i
        continue
      }
      i++
    }
    parts << input.substring(start).trim()
    parts
  }

  private ParsedFilter parseSupportedTextSearch(
    final String term,
    final List matchIn,
    final Class rootEntityClass,
    final ParseState state
  ) {
    if (!term) return null

    final List<String> props = (matchIn ?: [])
      .collect { (it as String)?.trim() }
      .findAll { it }
    if (!props) return null

    final List<String> splitTerms = term.split(/(?!\B"[^"]*)\s+(?![^"]*"\B)/)
      .collect { ((it ?: '') as String).replace('"', '') }
      .findAll { it }
    if (!splitTerms) return null

    final List<ParsedFilter> propertyClauses = []
    for (String prop : props) {
      final def propDef = DomainUtils.resolveProperty(rootEntityClass, prop, true)
      if (!propDef || !propDef.searchable) {
        continue
      }

      final ParsedFieldMatch fieldMatch = resolveSupportedMatchInField(rootEntityClass, prop, propDef.type as Class)
      if (!fieldMatch) {
        return UNSUPPORTED_SLICE
      }

      final List<ParsedFilter> termClauses = []
      for (String splitTerm : splitTerms) {
        final String paramName = nextParam(state)
        final Object coerced = coerceMatchInValue(splitTerm, fieldMatch.fieldType)
        if (coerced == COERCE_FAILED) {
          return UNSUPPORTED_SLICE
        }
        termClauses << new ParsedFilter(
          whereClause: resolveFieldMatchClause(fieldMatch, paramName),
          params: [(paramName): fieldMatch.isString ? ('%' + (((coerced ?: '') as String).toLowerCase()) + '%') : coerced],
          usesItemJoin: fieldMatch.usesItemJoin,
          usesChecklistJoin: fieldMatch.usesChecklistJoin
        )
      }

      final ParsedFilter propClause = combineParsed(termClauses, 'and')
      if (!propClause) {
        return UNSUPPORTED_SLICE
      }
      propertyClauses << propClause
    }

    if (!propertyClauses) {
      // Legacy path treats unresolved/non-searchable matchIn props as "no text clause".
      return null
    }

    combineParsed(propertyClauses, 'or')
  }

  private static ParsedFilter combineParsed(final List<ParsedFilter> nodes, final String joinOp) {
    if (!nodes) return null
    final Map<String, Object> params = [:]
    boolean usesItemJoin = false
    boolean usesChecklistJoin = false
    final List<String> clauses = []
    nodes.each { ParsedFilter node ->
      clauses << '(' + node.whereClause + ')'
      params.putAll(node.params)
      usesItemJoin = usesItemJoin || node.usesItemJoin
      usesChecklistJoin = usesChecklistJoin || node.usesChecklistJoin
    }
    new ParsedFilter(
      whereClause: clauses.join(" ${joinOp} "),
      params: params,
      usesItemJoin: usesItemJoin,
      usesChecklistJoin: usesChecklistJoin
    )
  }

  private static String rewriteJoinAliasesForSubquery(
    final String clause,
    final String checklistAlias,
    final String itemAlias
  ) {
    String rewritten = clause ?: ''
    if (checklistAlias) {
      rewritten = rewritten.replaceAll(/\bcl\./, checklistAlias + '.')
    }
    if (itemAlias) {
      rewritten = rewritten.replaceAll(/\bi\./, itemAlias + '.')
    }
    rewritten
  }

  private static String nextParam(final ParseState state) {
    final int idx = state.nextParamIdx
    state.nextParamIdx = idx + 1
    "p${idx}"
  }

  private ParsedFieldMatch resolveSupportedMatchInField(final Class rootEntityClass, final String prop, final Class type) {
    resolveGenericMatchInField(rootEntityClass, prop, type)
  }

  private ParsedFieldMatch resolveGenericMatchInField(final Class rootEntityClass, final String prop, final Class type) {
    if (!rootEntityClass || !prop || !type) return null
    if (!isSupportedMatchInType(type)) return null

    final List<String> parts = prop.tokenize('.')
    if (!parts) return null

    final PersistentEntity rootEntity = DomainUtils.resolveDomainClass(rootEntityClass)
    if (!rootEntity) return null

    PersistentEntity currentEntity = rootEntity
    for (int i = 0; i < parts.size() - 1; i++) {
      final String segment = parts[i]
      final PersistentProperty propDef = currentEntity.getPropertyByName(segment)
      if (!(propDef instanceof Association)) return null
      currentEntity = (propDef as Association).associatedEntity
      if (!currentEntity) return null
    }

    final String terminal = parts.last()
    final PersistentProperty terminalProp = currentEntity.getPropertyByName(terminal)
    if (!terminalProp || terminalProp instanceof Association) return null

    final boolean isString = String.isAssignableFrom(type)
    if (parts.size() == 1) {
      return new ParsedFieldMatch(
        aliasPath: "r.${terminal}",
        fieldType: type,
        isString: isString
      )
    }

    final String clauseTemplate = buildExistsMatchInClauseTemplate(parts, isString)
    return new ParsedFieldMatch(
      fieldType: type,
      isString: isString,
      customStringClauseTemplate: isString ? clauseTemplate : null,
      customNonStringClauseTemplate: isString ? null : clauseTemplate
    )
  }

  private static String buildExistsMatchInClauseTemplate(final List<String> parts, final boolean isString) {
    int aliasIdx = 0
    String currentAlias = "m${aliasIdx}"
    final StringBuilder out = new StringBuilder("(exists (select 1 from r.${parts[0]} ${currentAlias}")

    for (int i = 1; i < parts.size() - 1; i++) {
      aliasIdx++
      final String nextAlias = "m${aliasIdx}"
      out.append(" join ${currentAlias}.${parts[i]} ${nextAlias}")
      currentAlias = nextAlias
    }

    final String fieldExpr = "${currentAlias}.${parts.last()}"
    if (isString) {
      out.append(" where lower(${fieldExpr}) like :__PARAM__))")
    } else {
      out.append(" where ${fieldExpr} = :__PARAM__))")
    }
    out.toString()
  }

  private static String buildExistsFieldPredicateClause(final List<String> parts, final String fieldComparatorSuffix) {
    int aliasIdx = 0
    String currentAlias = "f${aliasIdx}"
    final StringBuilder out = new StringBuilder("(exists (select 1 from r.${parts[0]} ${currentAlias}")

    for (int i = 1; i < parts.size() - 1; i++) {
      aliasIdx++
      final String nextAlias = "f${aliasIdx}"
      out.append(" join ${currentAlias}.${parts[i]} ${nextAlias}")
      currentAlias = nextAlias
    }

    final String fieldExpr = "${currentAlias}.${parts.last()}"
    out.append(" where ${fieldExpr}${fieldComparatorSuffix}))")
    out.toString()
  }

  private static String buildExistsFieldParamPredicateClause(final List<String> parts, final String fieldClauseTemplate) {
    int aliasIdx = 0
    String currentAlias = "f${aliasIdx}"
    final StringBuilder out = new StringBuilder("(exists (select 1 from r.${parts[0]} ${currentAlias}")

    for (int i = 1; i < parts.size() - 1; i++) {
      aliasIdx++
      final String nextAlias = "f${aliasIdx}"
      out.append(" join ${currentAlias}.${parts[i]} ${nextAlias}")
      currentAlias = nextAlias
    }

    final String fieldExpr = "${currentAlias}.${parts.last()}"
    final String whereExpr = fieldClauseTemplate.replace('__FIELD__', fieldExpr)
    out.append(" where ${whereExpr}))")
    out.toString()
  }

  private static boolean isSupportedMatchInType(final Class type) {
    String.isAssignableFrom(type) ||
      Integer.isAssignableFrom(type) ||
      int == type ||
      LocalDate.isAssignableFrom(type)
  }

  private GenericPathResolution resolveGenericFilterPath(final Class rootEntityClass, final String propPath) {
    if (!rootEntityClass || !propPath) return null

    final List<String> parts = propPath.tokenize('.')
    if (!parts) return null

    final PersistentEntity rootEntity = DomainUtils.resolveDomainClass(rootEntityClass)
    if (!rootEntity) return null

    PersistentEntity currentEntity = rootEntity
    for (int i = 0; i < parts.size() - 1; i++) {
      final PersistentProperty propDef = currentEntity.getPropertyByName(parts[i])
      if (!(propDef instanceof Association)) return null
      currentEntity = (propDef as Association).associatedEntity
      if (!currentEntity) return null
    }

    final PersistentProperty terminalProp = currentEntity.getPropertyByName(parts.last())
    if (!terminalProp || terminalProp instanceof Association) return null

    new GenericPathResolution(parts: parts, terminalType: terminalProp.type)
  }

  private ParsedPredicate buildGenericFieldParamPredicate(
    final List<String> parts,
    final String fieldClauseTemplate,
    final Map<String, Object> params
  ) {
    final String clause = (parts.size() == 1)
      ? fieldClauseTemplate.replace('__FIELD__', "r.${parts[0]}")
      : buildExistsFieldParamPredicateClause(parts, fieldClauseTemplate)

    new ParsedPredicate(
      clause: clause,
      params: params,
      usesItemJoin: false,
      usesChecklistJoin: false
    )
  }

  private Object coerceGenericFilterValue(final Class type, final String raw) {
    if (String.isAssignableFrom(type)) {
      return raw
    }
    if (Integer.isAssignableFrom(type) || int == type) {
      try {
        return Integer.valueOf(raw)
      } catch (Exception ignored) {
        return COERCE_FAILED
      }
    }
    if (LocalDate.isAssignableFrom(type)) {
      try {
        return LocalDate.parse(raw)
      } catch (Exception ignored) {
        return COERCE_FAILED
      }
    }
    COERCE_FAILED
  }

  private Object coerceGenericComparableValue(final Class type, final String raw) {
    if (Integer.isAssignableFrom(type) || int == type) {
      try {
        return Integer.valueOf(raw)
      } catch (Exception ignored) {
        return COERCE_FAILED
      }
    }
    if (LocalDate.isAssignableFrom(type)) {
      try {
        return LocalDate.parse(raw)
      } catch (Exception ignored) {
        return COERCE_FAILED
      }
    }
    COERCE_FAILED
  }

  private static boolean isComparableFilterType(final Class type) {
    Integer.isAssignableFrom(type) ||
      int == type ||
      LocalDate.isAssignableFrom(type)
  }

  private Object coerceMatchInValue(final String raw, final Class type) {
    if (String.isAssignableFrom(type)) {
      return raw
    }
    if (Integer.isAssignableFrom(type) || int == type) {
      try {
        return Integer.valueOf(raw)
      } catch (Exception ignored) {
        return COERCE_FAILED
      }
    }
    if (LocalDate.isAssignableFrom(type)) {
      try {
        return LocalDate.parse(raw)
      } catch (Exception ignored) {
        return COERCE_FAILED
      }
    }
    COERCE_FAILED
  }

  private String resolveFieldMatchClause(final ParsedFieldMatch fieldMatch, final String paramName) {
    if (fieldMatch.isString && fieldMatch.customStringClauseTemplate) {
      return fieldMatch.customStringClauseTemplate.replace('__PARAM__', paramName)
    }
    if (!fieldMatch.isString && fieldMatch.customNonStringClauseTemplate) {
      return fieldMatch.customNonStringClauseTemplate.replace('__PARAM__', paramName)
    }
    fieldMatch.isString
      ? "lower(${fieldMatch.aliasPath}) like :${paramName}"
      : "${fieldMatch.aliasPath} = :${paramName}"
  }

  private void applySimpleSorts(final Object criteriaTarget, final List sorts) {
    sorts.each { String sort ->
      final String[] sortParts = sort.split(/;/)
      final String prop = sortParts[0]
      final String direction = (sortParts.length > 1 ? sortParts[1] : 'asc')?.toLowerCase() == 'desc' ? 'desc' : 'asc'
      criteriaTarget.order(prop, direction)
    }
  }

  private static final class ParsedFilter {
    String whereClause
    Map<String, Object> params = [:]
    boolean usesItemJoin = false
    boolean usesChecklistJoin = false
  }

  private static final class ParsedPredicate {
    String clause
    Map<String, Object> params = [:]
    boolean usesItemJoin = false
    boolean usesChecklistJoin = false
  }

  private static final class ParseState {
    int nextParamIdx = 0
    Class rootEntityClass
  }

  private static final ParsedFilter UNSUPPORTED_SLICE = new ParsedFilter(
    whereClause: '__UNSUPPORTED__',
    params: [:],
    usesItemJoin: false,
    usesChecklistJoin: false
  )

  private static final Object COERCE_FAILED = new Object()

  private static final class ParsedFieldMatch {
    String aliasPath
    Class fieldType
    boolean isString
    boolean usesItemJoin = false
    boolean usesChecklistJoin = false
    String customStringClauseTemplate
    String customNonStringClauseTemplate
  }

  private static final class GenericPathResolution {
    List<String> parts
    Class terminalType
  }
}
