package com.k_int.web.toolkit.query

import java.time.LocalDate

import com.k_int.web.toolkit.utils.DomainUtils

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

    final ParseState state = new ParseState(nextParamIdx: 0)
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

    final def checklistEqMatcher = (expression =~ /^checklists\.name==(.+)$/)
    if (checklistEqMatcher.matches()) {
      final String paramName = nextParam(state)
      return new ParsedPredicate(
        clause: "cl.name = :${paramName}",
        params: [(paramName): checklistEqMatcher[0][1]],
        usesChecklistJoin: true
      )
    }

    final def checklistIeqMatcher = (expression =~ /(?i)^checklists\.name=i=(.+)$/)
    if (checklistIeqMatcher.matches()) {
      final String paramName = nextParam(state)
      final String value = (checklistIeqMatcher[0][1] as String)?.toLowerCase()
      return new ParsedPredicate(
        clause: "lower(cl.name) = :${paramName}",
        params: [(paramName): value],
        usesChecklistJoin: true
      )
    }

    final def checklistContainsMatcher = (expression =~ /^checklists\.name=~(.+)$/)
    if (checklistContainsMatcher.matches()) {
      final String paramName = nextParam(state)
      final String value = '%' + ((checklistContainsMatcher[0][1] as String)?.toLowerCase()) + '%'
      return new ParsedPredicate(
        clause: "lower(cl.name) like :${paramName}",
        params: [(paramName): value],
        usesChecklistJoin: true
      )
    }

    final def checklistNotContainsMatcher = (expression =~ /^checklists\.name!~(.+)$/)
    if (checklistNotContainsMatcher.matches()) {
      final String paramName = nextParam(state)
      final String value = '%' + ((checklistNotContainsMatcher[0][1] as String)?.toLowerCase()) + '%'
      return new ParsedPredicate(
        clause: "(not (lower(cl.name) like :${paramName}))",
        params: [(paramName): value],
        usesChecklistJoin: true
      )
    }

    final def checklistNeqMatcher = (expression =~ /^checklists\.name!=(.+)$/)
    if (checklistNeqMatcher.matches()) {
      final String paramName = nextParam(state)
      return new ParsedPredicate(
        clause: "(cl.name <> :${paramName} or cl.name is not null)",
        params: [(paramName): checklistNeqMatcher[0][1]],
        usesChecklistJoin: true
      )
    }

    final def checklistNullMatcher = (expression =~ /(?i)^checklists\.name\s+isNull$/)
    if (checklistNullMatcher.matches()) {
      return new ParsedPredicate(
        clause: "cl.name is null",
        params: [:],
        usesChecklistJoin: true
      )
    }

    final def checklistNotNullMatcher = (expression =~ /(?i)^checklists\.name\s+isNotNull$/)
    if (checklistNotNullMatcher.matches()) {
      return new ParsedPredicate(
        clause: "cl.name is not null",
        params: [:],
        usesChecklistJoin: true
      )
    }

    final def checklistIsSetMatcher = (expression =~ /(?i)^checklists\.name\s+isSet$/)
    if (checklistIsSetMatcher.matches()) {
      return new ParsedPredicate(
        clause: "cl.name is not null",
        params: [:],
        usesChecklistJoin: true
      )
    }

    final def checklistIsNotSetMatcher = (expression =~ /(?i)^checklists\.name\s+isNotSet$/)
    if (checklistIsNotSetMatcher.matches()) {
      return new ParsedPredicate(
        clause: "cl.name is null",
        params: [:],
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

    final def rangeMatcher = (expression =~ /^(.+?)(<=|<)(number|date)(<=|<)(.+)$/)
    if (rangeMatcher.matches()) {
      final String leftValueRaw = rangeMatcher[0][1]?.trim()
      final String leftOp = rangeMatcher[0][2]
      final String field = rangeMatcher[0][3]
      final String rightOp = rangeMatcher[0][4]
      final String rightValueRaw = rangeMatcher[0][5]?.trim()

      final String lp = nextParam(state)
      final String rp = nextParam(state)
      final Object leftValue = convertRootValue(field, leftValueRaw)
      final Object rightValue = convertRootValue(field, rightValueRaw)
      if (leftValue == null || rightValue == null) return null

      final String leftComparator = (leftOp == '<') ? '>' : '>='
      final String rightComparator = (rightOp == '<') ? '<' : '<='
      return new ParsedPredicate(
        clause: "(r.${field} ${leftComparator} :${lp} and r.${field} ${rightComparator} :${rp})",
        params: [(lp): leftValue, (rp): rightValue],
        usesItemJoin: false
      )
    }

    final def rootCmpMatcher = (expression =~ /^(number|date)(>=|<=|>|<)(.+)$/)
    if (rootCmpMatcher.matches()) {
      final String field = rootCmpMatcher[0][1]
      final String op = rootCmpMatcher[0][2]
      final String valueRaw = rootCmpMatcher[0][3]?.trim()
      final Object value = convertRootValue(field, valueRaw)
      if (value == null) return null
      final String paramName = nextParam(state)
      return new ParsedPredicate(
        clause: "r.${field} ${op} :${paramName}",
        params: [(paramName): value],
        usesItemJoin: false
      )
    }

    final def rootEqMatcher = (expression =~ /^(name|number|date)==(.+)$/)
    if (rootEqMatcher.matches()) {
      final String field = rootEqMatcher[0][1]
      final String paramName = nextParam(state)
      final Object value = convertRootValue(field, rootEqMatcher[0][2]?.trim())
      if (value == null) return null
      return new ParsedPredicate(
        clause: "r.${field} = :${paramName}",
        params: [(paramName): value],
        usesItemJoin: false,
        usesChecklistJoin: false
      )
    }

    final def rootIeqMatcher = (expression =~ /(?i)^(name)=i=(.+)$/)
    if (rootIeqMatcher.matches()) {
      final String paramName = nextParam(state)
      final String value = (rootIeqMatcher[0][2] as String)?.toLowerCase()
      return new ParsedPredicate(
        clause: "lower(r.name) = :${paramName}",
        params: [(paramName): value],
        usesItemJoin: false,
        usesChecklistJoin: false
      )
    }

    final def rootContainsMatcher = (expression =~ /^(name)=~(.+)$/)
    if (rootContainsMatcher.matches()) {
      final String paramName = nextParam(state)
      final String value = '%' + ((rootContainsMatcher[0][2] as String)?.toLowerCase()) + '%'
      return new ParsedPredicate(
        clause: "lower(r.name) like :${paramName}",
        params: [(paramName): value],
        usesItemJoin: false,
        usesChecklistJoin: false
      )
    }

    final def rootNotContainsMatcher = (expression =~ /^(name)!~(.+)$/)
    if (rootNotContainsMatcher.matches()) {
      final String paramName = nextParam(state)
      final String value = '%' + ((rootNotContainsMatcher[0][2] as String)?.toLowerCase()) + '%'
      return new ParsedPredicate(
        clause: "(not (lower(r.name) like :${paramName}))",
        params: [(paramName): value],
        usesItemJoin: false,
        usesChecklistJoin: false
      )
    }

    final def rootNeqMatcher = (expression =~ /^(name|number|date)!=(.+)$/)
    if (rootNeqMatcher.matches()) {
      final String field = rootNeqMatcher[0][1]
      final String paramName = nextParam(state)
      final Object value = convertRootValue(field, rootNeqMatcher[0][2]?.trim())
      if (value == null) return null
      return new ParsedPredicate(
        clause: "(r.${field} <> :${paramName} or r.${field} is not null)",
        params: [(paramName): value],
        usesItemJoin: false,
        usesChecklistJoin: false
      )
    }

    final def rootNullMatcher = (expression =~ /(?i)^(name|number|date)\s+isNull$/)
    if (rootNullMatcher.matches()) {
      final String field = rootNullMatcher[0][1]
      return new ParsedPredicate(
        clause: "r.${field} is null",
        params: [:],
        usesItemJoin: false,
        usesChecklistJoin: false
      )
    }

    final def rootNotNullMatcher = (expression =~ /(?i)^(name|number|date)\s+isNotNull$/)
    if (rootNotNullMatcher.matches()) {
      final String field = rootNotNullMatcher[0][1]
      return new ParsedPredicate(
        clause: "r.${field} is not null",
        params: [:],
        usesItemJoin: false,
        usesChecklistJoin: false
      )
    }

    final def rootIsSetMatcher = (expression =~ /(?i)^(name|number|date)\s+isSet$/)
    if (rootIsSetMatcher.matches()) {
      final String field = rootIsSetMatcher[0][1]
      return new ParsedPredicate(
        clause: "r.${field} is not null",
        params: [:],
        usesItemJoin: false,
        usesChecklistJoin: false
      )
    }

    final def rootIsNotSetMatcher = (expression =~ /(?i)^(name|number|date)\s+isNotSet$/)
    if (rootIsNotSetMatcher.matches()) {
      final String field = rootIsNotSetMatcher[0][1]
      return new ParsedPredicate(
        clause: "r.${field} is null",
        params: [:],
        usesItemJoin: false,
        usesChecklistJoin: false
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

    null
  }

  private Object convertRootValue(final String field, final String valueRaw) {
    if ('name' == field) {
      return valueRaw
    }

    if ('number' == field) {
      try {
        return Integer.valueOf(valueRaw)
      } catch (Exception ignored) {
        return null
      }
    }

    if ('date' == field) {
      try {
        return LocalDate.parse(valueRaw)
      } catch (Exception ignored) {
        return null
      }
    }

    null
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

      final ParsedFieldMatch fieldMatch = resolveSupportedMatchInField(prop, propDef.type as Class)
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

  private ParsedFieldMatch resolveSupportedMatchInField(final String prop, final Class type) {
    if ('name' == prop) {
      if (!String.isAssignableFrom(type)) return null
      return new ParsedFieldMatch(aliasPath: 'r.name', fieldType: type, isString: true)
    }
    if ('number' == prop) {
      return new ParsedFieldMatch(aliasPath: 'r.number', fieldType: type, isString: false)
    }
    if ('date' == prop) {
      return new ParsedFieldMatch(aliasPath: 'r.date', fieldType: type, isString: false)
    }
    if ('checklists.name' == prop) {
      if (!String.isAssignableFrom(type)) return null
      return new ParsedFieldMatch(aliasPath: 'cl.name', fieldType: type, isString: true, usesChecklistJoin: true)
    }
    if ('checklists.request.name' == prop) {
      if (!String.isAssignableFrom(type)) return null
      return new ParsedFieldMatch(aliasPath: 'cl.request.name', fieldType: type, isString: true, usesChecklistJoin: true)
    }
    if ('checklists.request.number' == prop) {
      return new ParsedFieldMatch(aliasPath: 'cl.request.number', fieldType: type, isString: false, usesChecklistJoin: true)
    }
    if ('checklists.request.date' == prop) {
      return new ParsedFieldMatch(aliasPath: 'cl.request.date', fieldType: type, isString: false, usesChecklistJoin: true)
    }
    if ('checklists.items.checklist.request.name' == prop) {
      if (!String.isAssignableFrom(type)) return null
      return new ParsedFieldMatch(aliasPath: 'i.checklist.request.name', fieldType: type, isString: true, usesItemJoin: true, usesChecklistJoin: true)
    }
    if ('checklists.items.checklist.request.date' == prop) {
      return new ParsedFieldMatch(aliasPath: 'i.checklist.request.date', fieldType: type, isString: false, usesItemJoin: true, usesChecklistJoin: true)
    }
    if ('checklists.items.checklist.request.number' == prop) {
      return new ParsedFieldMatch(aliasPath: 'i.checklist.request.number', fieldType: type, isString: false, usesItemJoin: true, usesChecklistJoin: true)
    }
    if ('checklists.items.checklist.request.checklists.name' == prop) {
      if (!String.isAssignableFrom(type)) return null
      return new ParsedFieldMatch(
        fieldType: type,
        isString: true,
        customStringClauseTemplate: "(exists (select 1 from r.checklists cl0 join cl0.items i0 join i0.checklist ci0 join ci0.request r0 join r0.checklists scl where lower(scl.name) like :__PARAM__))"
      )
    }
    if ('checklists.items.outcome' == prop || 'checklists.items.status' == prop) {
      if (!String.isAssignableFrom(type)) return null
      final String field = prop.tokenize('.').last()
      return new ParsedFieldMatch(aliasPath: "i.${field}", fieldType: type, isString: true, usesItemJoin: true, usesChecklistJoin: true)
    }
    null
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
}
