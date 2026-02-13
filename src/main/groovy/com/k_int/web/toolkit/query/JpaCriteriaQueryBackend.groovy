package com.k_int.web.toolkit.query

import java.time.LocalDate

import com.k_int.web.toolkit.utils.DomainUtils
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.model.types.ToMany

class JpaCriteriaQueryBackend implements SimpleLookupQueryBackend {
  private final SimpleLookupQueryBackend fallbackBackend
  private final boolean useAstFilterParser

  private static final Map<AstOperator, String> AST_OPERATOR_SEMANTICS = [
    (AstOperator.RANGE_CHAIN): 'Comparable chained range bounds',
    (AstOperator.COMPARATOR): 'Comparable single-sided comparator',
    (AstOperator.EQ): 'Typed equality',
    (AstOperator.NEQ): 'Typed not-equal',
    (AstOperator.IEQ): 'Case-insensitive string equality',
    (AstOperator.CONTAINS): 'Case-insensitive string contains',
    (AstOperator.NOT_CONTAINS): 'Case-insensitive string not-contains',
    (AstOperator.NULL_STYLE): 'Null-style operator family',
    (AstOperator.ITEM_EMPTY): 'Legacy item empty semantics',
    (AstOperator.ITEM_NOT_EMPTY): 'Legacy item not-empty semantics',
    (AstOperator.ITEM_NEQ_WIDEN): 'Legacy item not-equal widening semantics'
  ].asImmutable()

  JpaCriteriaQueryBackend(final SimpleLookupQueryBackend fallbackBackend = null) {
    this(fallbackBackend, false)
  }

  JpaCriteriaQueryBackend(final SimpleLookupQueryBackend fallbackBackend, final boolean useAstFilterParser) {
    this.fallbackBackend = fallbackBackend
    this.useAstFilterParser = useAstFilterParser
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

    if (useAstFilterParser) {
      final ParsedFilter correlatedAnd = parseCorrelatedGenericAndFilters(expressions, state)
      if (correlatedAnd) {
        return correlatedAnd
      }

      final List<ParsedFilter> astParsedParts = []
      for (String expression : expressions) {
        final FilterExpressionAst ast = FilterExpressionAstParser.parse(expression)
        if (!ast) return null
        final ParsedFilter parsed = parseFilterExpressionAst(ast, state)
        if (!parsed) return null
        astParsedParts << parsed
      }
      return combineParsed(astParsedParts, 'and')
    }

    final ParsedFilter correlatedAnd = parseCorrelatedGenericAndFilters(expressions, state)
    if (correlatedAnd) {
      return correlatedAnd
    }

    final List<ParsedFilter> parsedParts = []
    for (String expression : expressions) {
      final ParsedFilter parsed = parseSupportedFilterExpression(expression, state)
      if (!parsed) return null
      parsedParts << parsed
    }
    combineParsed(parsedParts, 'and')
  }

  private ParsedFilter parseFilterExpressionAst(final FilterExpressionAst expressionAst, final ParseState state) {
    if (expressionAst instanceof FilterPredicateExpressionAst) {
      final String expression = (expressionAst as FilterPredicateExpressionAst).expression
      return parseAtomicPredicateViaAstOperatorTable(expression, state)
    }

    if (expressionAst instanceof FilterNotExpressionAst) {
      final ParsedFilter inner = parseFilterExpressionAst((expressionAst as FilterNotExpressionAst).term, state)
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

    if (expressionAst instanceof FilterAndExpressionAst) {
      final List<FilterExpressionAst> terms = (expressionAst as FilterAndExpressionAst).terms ?: []
      if (!terms) return null

      final List<String> predicateTerms = terms
        .findAll { it instanceof FilterPredicateExpressionAst }
        .collect { (it as FilterPredicateExpressionAst).expression }
      if (predicateTerms.size() == terms.size() && predicateTerms.size() > 1) {
        final ParsedFilter correlatedAnd = parseCorrelatedGenericAndFilters(predicateTerms, state)
        if (correlatedAnd) return correlatedAnd
      }

      final List<ParsedFilter> children = []
      for (FilterExpressionAst child : terms) {
        final ParsedFilter parsedChild = parseFilterExpressionAst(child, state)
        if (!parsedChild) return null
        children << parsedChild
      }
      return combineParsed(children, 'and')
    }

    if (expressionAst instanceof FilterOrExpressionAst) {
      final List<FilterExpressionAst> terms = (expressionAst as FilterOrExpressionAst).terms ?: []
      if (!terms) return null
      final List<ParsedFilter> children = []
      for (FilterExpressionAst child : terms) {
        final ParsedFilter parsedChild = parseFilterExpressionAst(child, state)
        if (!parsedChild) return null
        children << parsedChild
      }
      return combineParsed(children, 'or')
    }

    null
  }

  private ParsedFilter parseAtomicPredicateViaAstOperatorTable(final String expression, final ParseState state) {
    final AstAtomicExpression atomic = parseAstAtomicExpression(expression)
    if (!atomic) return null

    assert AST_OPERATOR_SEMANTICS.containsKey(atomic.operator)

    final ParsedPredicate predicate = buildPredicateForAstAtomicExpression(atomic, state)
    if (!predicate) return null

    new ParsedFilter(
      whereClause: predicate.clause,
      params: predicate.params,
      usesItemJoin: predicate.usesItemJoin,
      usesChecklistJoin: predicate.usesChecklistJoin
    )
  }

  private AstAtomicExpression parseAstAtomicExpression(final String expression) {
    final String input = stripOuterParentheses(expression?.trim())
    if (!input) return null

    final def itemEmptyMatcher = (input =~ /(?i)^checklists\.items\.(outcome|status)\s+isEmpty$/)
    if (itemEmptyMatcher.matches()) {
      return new AstAtomicExpression(operator: AstOperator.ITEM_EMPTY, path: "checklists.items.${itemEmptyMatcher[0][1]}")
    }

    final def itemNotEmptyMatcher = (input =~ /(?i)^checklists\.items\.(outcome|status)\s+isNotEmpty$/)
    if (itemNotEmptyMatcher.matches()) {
      return new AstAtomicExpression(operator: AstOperator.ITEM_NOT_EMPTY, path: "checklists.items.${itemNotEmptyMatcher[0][1]}")
    }

    final def itemNeqMatcher = (input =~ /^checklists\.items\.(outcome|status)!=(.+)$/)
    if (itemNeqMatcher.matches()) {
      return new AstAtomicExpression(
        operator: AstOperator.ITEM_NEQ_WIDEN,
        path: "checklists.items.${itemNeqMatcher[0][1]}",
        rhs: (itemNeqMatcher[0][2] as String)?.trim()
      )
    }

    final def rangeMatcher = (input =~ /^(.+?)(<=|<)([A-Za-z_]\w*(?:\.[A-Za-z_]\w*)*)(<=|<)(.+)$/)
    if (rangeMatcher.matches()) {
      return new AstAtomicExpression(
        operator: AstOperator.RANGE_CHAIN,
        path: rangeMatcher[0][3],
        lhs: (rangeMatcher[0][1] as String)?.trim(),
        rhs: (rangeMatcher[0][5] as String)?.trim(),
        leftOp: rangeMatcher[0][2],
        rightOp: rangeMatcher[0][4]
      )
    }

    final def cmpMatcher = (input =~ /^([A-Za-z_]\w*(?:\.[A-Za-z_]\w*)*)(>=|<=|>|<)(.+)$/)
    if (cmpMatcher.matches()) {
      return new AstAtomicExpression(
        operator: AstOperator.COMPARATOR,
        path: cmpMatcher[0][1],
        rhs: (cmpMatcher[0][3] as String)?.trim(),
        comparisonOp: cmpMatcher[0][2]
      )
    }

    final def nullMatcher = (input =~ /(?i)^([A-Za-z_]\w*(?:\.[A-Za-z_]\w*)*)\s+(isNull|isNotNull|isSet|isNotSet)$/)
    if (nullMatcher.matches()) {
      return new AstAtomicExpression(
        operator: AstOperator.NULL_STYLE,
        path: nullMatcher[0][1],
        nullStyleOp: ((nullMatcher[0][2] as String) ?: '').toLowerCase()
      )
    }

    final def ieqMatcher = (input =~ /(?i)^([A-Za-z_]\w*(?:\.[A-Za-z_]\w*)*)=i=(.+)$/)
    if (ieqMatcher.matches()) {
      return new AstAtomicExpression(operator: AstOperator.IEQ, path: ieqMatcher[0][1], rhs: (ieqMatcher[0][2] as String)?.trim())
    }

    final def containsMatcher = (input =~ /^([A-Za-z_]\w*(?:\.[A-Za-z_]\w*)*)=~(.+)$/)
    if (containsMatcher.matches()) {
      return new AstAtomicExpression(operator: AstOperator.CONTAINS, path: containsMatcher[0][1], rhs: (containsMatcher[0][2] as String)?.trim())
    }

    final def notContainsMatcher = (input =~ /^([A-Za-z_]\w*(?:\.[A-Za-z_]\w*)*)!~(.+)$/)
    if (notContainsMatcher.matches()) {
      return new AstAtomicExpression(operator: AstOperator.NOT_CONTAINS, path: notContainsMatcher[0][1], rhs: (notContainsMatcher[0][2] as String)?.trim())
    }

    final def eqMatcher = (input =~ /^([A-Za-z_]\w*(?:\.[A-Za-z_]\w*)*)==(.+)$/)
    if (eqMatcher.matches()) {
      return new AstAtomicExpression(operator: AstOperator.EQ, path: eqMatcher[0][1], rhs: (eqMatcher[0][2] as String)?.trim())
    }

    final def neqMatcher = (input =~ /^([A-Za-z_]\w*(?:\.[A-Za-z_]\w*)*)!=(.+)$/)
    if (neqMatcher.matches()) {
      return new AstAtomicExpression(operator: AstOperator.NEQ, path: neqMatcher[0][1], rhs: (neqMatcher[0][2] as String)?.trim())
    }

    null
  }

  private ParsedPredicate buildPredicateForAstAtomicExpression(final AstAtomicExpression atomic, final ParseState state) {
    switch (atomic.operator) {
      case AstOperator.ITEM_EMPTY:
        final String emptyField = atomic.path.tokenize('.').last().toLowerCase()
        return new ParsedPredicate(clause: "i.${emptyField} = ''", params: [:], usesItemJoin: true, usesChecklistJoin: true)
      case AstOperator.ITEM_NOT_EMPTY:
        final String notEmptyField = atomic.path.tokenize('.').last().toLowerCase()
        return new ParsedPredicate(
          clause: "(i.${notEmptyField} is not null and i.${notEmptyField} <> '')",
          params: [:],
          usesItemJoin: true,
          usesChecklistJoin: true
        )
      case AstOperator.ITEM_NEQ_WIDEN:
        final String neqField = atomic.path.tokenize('.').last().toLowerCase()
        final String neqParam = nextParam(state)
        return new ParsedPredicate(
          clause: "(i.${neqField} <> :${neqParam} or i.${neqField} is not null)",
          params: [(neqParam): atomic.rhs],
          usesItemJoin: true,
          usesChecklistJoin: true
        )
      case AstOperator.RANGE_CHAIN:
        final GenericPathResolution rangeResolved = resolveGenericFilterPath(state.rootEntityClass, atomic.path)
        if (!rangeResolved || !isComparableFilterType(rangeResolved.terminalType)) return null
        final Object leftValue = coerceGenericComparableValue(rangeResolved.terminalType, atomic.lhs)
        final Object rightValue = coerceGenericComparableValue(rangeResolved.terminalType, atomic.rhs)
        if (leftValue == COERCE_FAILED || rightValue == COERCE_FAILED) return null
        final String lp = nextParam(state)
        final String rp = nextParam(state)
        final String leftComparator = (atomic.leftOp == '<') ? '>' : '>='
        final String rightComparator = (atomic.rightOp == '<') ? '<' : '<='
        return buildGenericFieldParamPredicate(
          rangeResolved.parts,
          "(__FIELD__ ${leftComparator} :${lp} and __FIELD__ ${rightComparator} :${rp})",
          [(lp): leftValue, (rp): rightValue]
        )
      case AstOperator.COMPARATOR:
        final GenericPathResolution cmpResolved = resolveGenericFilterPath(state.rootEntityClass, atomic.path)
        if (!cmpResolved || !isComparableFilterType(cmpResolved.terminalType)) return null
        final Object cmpValue = coerceGenericComparableValue(cmpResolved.terminalType, atomic.rhs)
        if (cmpValue == COERCE_FAILED) return null
        final String cmpParam = nextParam(state)
        return buildGenericFieldParamPredicate(cmpResolved.parts, "__FIELD__ ${atomic.comparisonOp} :${cmpParam}", [(cmpParam): cmpValue])
      case AstOperator.NULL_STYLE:
        final GenericPathResolution nullResolved = resolveGenericFilterPath(state.rootEntityClass, atomic.path)
        if (!nullResolved) return null
        final String nullClause = (atomic.nullStyleOp == 'isnull' || atomic.nullStyleOp == 'isnotset') ? '__FIELD__ is null' : '__FIELD__ is not null'
        return buildGenericFieldParamPredicate(nullResolved.parts, nullClause, [:])
      case AstOperator.IEQ:
        final GenericPathResolution ieqResolved = resolveGenericFilterPath(state.rootEntityClass, atomic.path)
        if (!ieqResolved || !String.isAssignableFrom(ieqResolved.terminalType)) return null
        final String ieqParam = nextParam(state)
        return buildGenericFieldParamPredicate(ieqResolved.parts, "lower(__FIELD__) = :${ieqParam}", [(ieqParam): (atomic.rhs ?: '').toLowerCase()])
      case AstOperator.CONTAINS:
        final GenericPathResolution containsResolved = resolveGenericFilterPath(state.rootEntityClass, atomic.path)
        if (!containsResolved || !String.isAssignableFrom(containsResolved.terminalType)) return null
        final String containsParam = nextParam(state)
        return buildGenericFieldParamPredicate(containsResolved.parts, "lower(__FIELD__) like :${containsParam}", [(containsParam): '%' + ((atomic.rhs ?: '').toLowerCase()) + '%'])
      case AstOperator.NOT_CONTAINS:
        final GenericPathResolution notContainsResolved = resolveGenericFilterPath(state.rootEntityClass, atomic.path)
        if (!notContainsResolved || !String.isAssignableFrom(notContainsResolved.terminalType)) return null
        final String notContainsParam = nextParam(state)
        return buildGenericFieldParamPredicate(
          notContainsResolved.parts,
          "(not (lower(__FIELD__) like :${notContainsParam}))",
          [(notContainsParam): '%' + ((atomic.rhs ?: '').toLowerCase()) + '%']
        )
      case AstOperator.EQ:
        final GenericPathResolution eqResolved = resolveGenericFilterPath(state.rootEntityClass, atomic.path)
        if (!eqResolved) return null
        final Object eqValue = coerceGenericFilterValue(eqResolved.terminalType, atomic.rhs)
        if (eqValue == COERCE_FAILED) return null
        final String eqParam = nextParam(state)
        return buildGenericFieldParamPredicate(eqResolved.parts, "__FIELD__ = :${eqParam}", [(eqParam): eqValue])
      case AstOperator.NEQ:
        final GenericPathResolution neqResolved = resolveGenericFilterPath(state.rootEntityClass, atomic.path)
        if (!neqResolved) return null
        final Object neqValue = coerceGenericFilterValue(neqResolved.terminalType, atomic.rhs)
        if (neqValue == COERCE_FAILED) return null
        final String neqParam = nextParam(state)
        return buildGenericFieldParamPredicate(neqResolved.parts, "__FIELD__ <> :${neqParam}", [(neqParam): neqValue])
      default:
        return null
    }
  }

  private ParsedFilter parseCorrelatedGenericAndFilters(final List<String> expressions, final ParseState state) {
    if (!expressions || expressions.size() < 2) return null

    final List<CorrelatedAtomicPredicate> atoms = []
    for (String expression : expressions) {
      final CorrelatedAtomicPredicate parsed = parseCorrelatableGenericAtomicPredicate(expression, state)
      if (!parsed) return null
      atoms << parsed
    }

    final List<String> sharedPrefix = atoms[0].associationPrefix
    if (!sharedPrefix) return null
    if (!atoms.every { it.associationPrefix == sharedPrefix }) return null

    final String existsClause = buildCorrelatedExistsClause(sharedPrefix, atoms)
    final Map<String, Object> params = [:]
    atoms.each { params.putAll(it.params) }

    new ParsedFilter(
      whereClause: existsClause,
      params: params,
      usesItemJoin: false,
      usesChecklistJoin: false
    )
  }

  private CorrelatedAtomicPredicate parseCorrelatableGenericAtomicPredicate(final String expression, final ParseState state) {
    final String input = stripOuterParentheses(expression?.trim())
    if (!input) return null
    if (input.contains('&&') || input.contains('||') || input.startsWith('!') || input.contains('(') || input.contains(')')) {
      return null
    }

    final def rangeMatcher = (input =~ /^(.+?)(<=|<)([A-Za-z_]\w*(?:\.[A-Za-z_]\w*)*)(<=|<)(.+)$/)
    if (rangeMatcher.matches()) {
      final String leftValueRaw = rangeMatcher[0][1]?.trim()
      final String leftOp = rangeMatcher[0][2]
      final String propPath = rangeMatcher[0][3]
      final String rightOp = rangeMatcher[0][4]
      final String rightValueRaw = rangeMatcher[0][5]?.trim()

      final GenericPathResolution resolved = resolveGenericFilterPath(state.rootEntityClass, propPath)
      if (!resolved || !isComparableFilterType(resolved.terminalType)) return null

      final List<String> associationParts = resolved.parts.size() > 1 ? resolved.parts[0..-2] : []
      final AssociationPathResolution associationPath = resolveAssociationPath(state.rootEntityClass, associationParts)
      if (!associationPath?.containsToMany) return null

      final Object leftValue = coerceGenericComparableValue(resolved.terminalType, leftValueRaw)
      final Object rightValue = coerceGenericComparableValue(resolved.terminalType, rightValueRaw)
      if (leftValue == COERCE_FAILED || rightValue == COERCE_FAILED) return null

      final String lp = nextParam(state)
      final String rp = nextParam(state)
      final String leftComparator = (leftOp == '<') ? '>' : '>='
      final String rightComparator = (rightOp == '<') ? '<' : '<='
      return new CorrelatedAtomicPredicate(
        associationPrefix: associationPath.parts,
        terminalField: resolved.parts.last(),
        conditionTemplate: "(__FIELD__ ${leftComparator} :${lp} and __FIELD__ ${rightComparator} :${rp})",
        params: [(lp): leftValue, (rp): rightValue]
      )
    }

    final def cmpMatcher = (input =~ /^([A-Za-z_]\w*(?:\.[A-Za-z_]\w*)*)(>=|<=|>|<)(.+)$/)
    if (cmpMatcher.matches()) {
      final String propPath = cmpMatcher[0][1]
      final String op = cmpMatcher[0][2]
      final String valueRaw = cmpMatcher[0][3]?.trim()

      final GenericPathResolution resolved = resolveGenericFilterPath(state.rootEntityClass, propPath)
      if (!resolved || !isComparableFilterType(resolved.terminalType)) return null

      final List<String> associationParts = resolved.parts.size() > 1 ? resolved.parts[0..-2] : []
      final AssociationPathResolution associationPath = resolveAssociationPath(state.rootEntityClass, associationParts)
      if (!associationPath?.containsToMany) return null

      final Object value = coerceGenericComparableValue(resolved.terminalType, valueRaw)
      if (value == COERCE_FAILED) return null

      final String paramName = nextParam(state)
      return new CorrelatedAtomicPredicate(
        associationPrefix: associationPath.parts,
        terminalField: resolved.parts.last(),
        conditionTemplate: "__FIELD__ ${op} :${paramName}",
        params: [(paramName): value]
      )
    }

    final def nullStyleMatcher = (input =~ /(?i)^([A-Za-z_]\w*(?:\.[A-Za-z_]\w*)*)\s+(isNull|isNotNull|isSet|isNotSet)$/)
    if (nullStyleMatcher.matches()) {
      final String propPath = nullStyleMatcher[0][1]
      final String op = nullStyleMatcher[0][2]?.toLowerCase()
      final GenericPathResolution resolved = resolveGenericFilterPath(state.rootEntityClass, propPath)
      if (!resolved) return null

      final List<String> associationParts = resolved.parts.size() > 1 ? resolved.parts[0..-2] : []
      final AssociationPathResolution associationPath = resolveAssociationPath(state.rootEntityClass, associationParts)
      if (!associationPath?.containsToMany) return null

      final String clause = (op == 'isnull' || op == 'isnotset') ? '__FIELD__ is null' : '__FIELD__ is not null'
      return new CorrelatedAtomicPredicate(
        associationPrefix: associationPath.parts,
        terminalField: resolved.parts.last(),
        conditionTemplate: clause,
        params: [:]
      )
    }

    final def ieqMatcher = (input =~ /(?i)^([A-Za-z_]\w*(?:\.[A-Za-z_]\w*)*)=i=(.+)$/)
    if (ieqMatcher.matches()) {
      final String propPath = ieqMatcher[0][1]
      final GenericPathResolution resolved = resolveGenericFilterPath(state.rootEntityClass, propPath)
      if (!resolved || !String.isAssignableFrom(resolved.terminalType)) return null

      final List<String> associationParts = resolved.parts.size() > 1 ? resolved.parts[0..-2] : []
      final AssociationPathResolution associationPath = resolveAssociationPath(state.rootEntityClass, associationParts)
      if (!associationPath?.containsToMany) return null

      final String paramName = nextParam(state)
      final String value = (ieqMatcher[0][2] as String)?.toLowerCase()
      return new CorrelatedAtomicPredicate(
        associationPrefix: associationPath.parts,
        terminalField: resolved.parts.last(),
        conditionTemplate: "lower(__FIELD__) = :${paramName}",
        params: [(paramName): value]
      )
    }

    final def containsMatcher = (input =~ /^([A-Za-z_]\w*(?:\.[A-Za-z_]\w*)*)=~(.+)$/)
    if (containsMatcher.matches()) {
      final String propPath = containsMatcher[0][1]
      final GenericPathResolution resolved = resolveGenericFilterPath(state.rootEntityClass, propPath)
      if (!resolved || !String.isAssignableFrom(resolved.terminalType)) return null

      final List<String> associationParts = resolved.parts.size() > 1 ? resolved.parts[0..-2] : []
      final AssociationPathResolution associationPath = resolveAssociationPath(state.rootEntityClass, associationParts)
      if (!associationPath?.containsToMany) return null

      final String paramName = nextParam(state)
      final String value = '%' + ((containsMatcher[0][2] as String)?.toLowerCase()) + '%'
      return new CorrelatedAtomicPredicate(
        associationPrefix: associationPath.parts,
        terminalField: resolved.parts.last(),
        conditionTemplate: "lower(__FIELD__) like :${paramName}",
        params: [(paramName): value]
      )
    }

    final def notContainsMatcher = (input =~ /^([A-Za-z_]\w*(?:\.[A-Za-z_]\w*)*)!~(.+)$/)
    if (notContainsMatcher.matches()) {
      final String propPath = notContainsMatcher[0][1]
      final GenericPathResolution resolved = resolveGenericFilterPath(state.rootEntityClass, propPath)
      if (!resolved || !String.isAssignableFrom(resolved.terminalType)) return null

      final List<String> associationParts = resolved.parts.size() > 1 ? resolved.parts[0..-2] : []
      final AssociationPathResolution associationPath = resolveAssociationPath(state.rootEntityClass, associationParts)
      if (!associationPath?.containsToMany) return null

      final String paramName = nextParam(state)
      final String value = '%' + ((notContainsMatcher[0][2] as String)?.toLowerCase()) + '%'
      return new CorrelatedAtomicPredicate(
        associationPrefix: associationPath.parts,
        terminalField: resolved.parts.last(),
        conditionTemplate: "(not (lower(__FIELD__) like :${paramName}))",
        params: [(paramName): value]
      )
    }

    final def eqMatcher = (input =~ /^([A-Za-z_]\w*(?:\.[A-Za-z_]\w*)*)==(.+)$/)
    if (eqMatcher.matches()) {
      final String propPath = eqMatcher[0][1]
      final GenericPathResolution resolved = resolveGenericFilterPath(state.rootEntityClass, propPath)
      if (!resolved) return null

      final List<String> associationParts = resolved.parts.size() > 1 ? resolved.parts[0..-2] : []
      final AssociationPathResolution associationPath = resolveAssociationPath(state.rootEntityClass, associationParts)
      if (!associationPath?.containsToMany) return null

      final Object value = coerceGenericFilterValue(resolved.terminalType, (eqMatcher[0][2] as String)?.trim())
      if (value == COERCE_FAILED) return null

      final String paramName = nextParam(state)
      return new CorrelatedAtomicPredicate(
        associationPrefix: associationPath.parts,
        terminalField: resolved.parts.last(),
        conditionTemplate: "__FIELD__ = :${paramName}",
        params: [(paramName): value]
      )
    }

    null
  }

  private String buildCorrelatedExistsClause(final List<String> associationPrefix, final List<CorrelatedAtomicPredicate> atoms) {
    int aliasIdx = 0
    String currentAlias = "c${aliasIdx}"
    final StringBuilder out = new StringBuilder("(exists (select 1 from r.${associationPrefix[0]} ${currentAlias}")

    for (int i = 1; i < associationPrefix.size(); i++) {
      aliasIdx++
      final String nextAlias = "c${aliasIdx}"
      out.append(" join ${currentAlias}.${associationPrefix[i]} ${nextAlias}")
      currentAlias = nextAlias
    }

    final List<String> conditions = atoms.collect { CorrelatedAtomicPredicate atom ->
      final String fieldExpr = "${currentAlias}.${atom.terminalField}"
      atom.conditionTemplate.replace('__FIELD__', fieldExpr)
    }
    out.append(" where ${conditions.join(' and ')}))")
    out.toString()
  }

  private AssociationPathResolution resolveAssociationPath(final Class rootEntityClass, final List<String> associationParts) {
    if (!rootEntityClass || !associationParts) return null

    final PersistentEntity rootEntity = DomainUtils.resolveDomainClass(rootEntityClass)
    if (!rootEntity) return null

    PersistentEntity currentEntity = rootEntity
    boolean containsToMany = false
    for (String segment : associationParts) {
      final PersistentProperty propDef = currentEntity.getPropertyByName(segment)
      if (!(propDef instanceof Association)) return null
      if (propDef instanceof ToMany) containsToMany = true
      currentEntity = (propDef as Association).associatedEntity
      if (!currentEntity) return null
    }

    new AssociationPathResolution(parts: associationParts, containsToMany: containsToMany)
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

    final ParsedFilter correlatedAnd = parseCorrelatedGenericAndFilters(parts, state)
    if (correlatedAnd) {
      return correlatedAnd
    }

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
    // These item-field clauses intentionally stay join-based so multiple predicates
    // in the same request are correlated against the same checklist item row.
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

  private static final class AssociationPathResolution {
    List<String> parts
    boolean containsToMany
  }

  private static final class CorrelatedAtomicPredicate {
    List<String> associationPrefix
    String terminalField
    String conditionTemplate
    Map<String, Object> params = [:]
  }

  private static enum AstOperator {
    RANGE_CHAIN,
    COMPARATOR,
    EQ,
    NEQ,
    IEQ,
    CONTAINS,
    NOT_CONTAINS,
    NULL_STYLE,
    ITEM_EMPTY,
    ITEM_NOT_EMPTY,
    ITEM_NEQ_WIDEN
  }

  private static final class AstAtomicExpression {
    AstOperator operator
    String path
    String lhs
    String rhs
    String leftOp
    String rightOp
    String comparisonOp
    String nullStyleOp
  }
}
