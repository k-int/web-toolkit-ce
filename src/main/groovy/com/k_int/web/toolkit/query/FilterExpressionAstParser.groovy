package com.k_int.web.toolkit.query

final class FilterExpressionAstParser {

  static FilterExpressionAst parse(final String rawExpression) {
    final String input = stripOuterParentheses(rawExpression?.trim())
    if (!input) return null
    parseOrExpression(input)
  }

  private static FilterExpressionAst parseOrExpression(final String expression) {
    final List<String> parts = splitTopLevel(expression, '||')
    if (parts.size() <= 1) return parseAndExpression(expression)

    final List<FilterExpressionAst> terms = parts.collect { parseAndExpression(stripOuterParentheses(it?.trim())) }
    if (terms.any { it == null }) return null
    new FilterOrExpressionAst(terms: terms)
  }

  private static FilterExpressionAst parseAndExpression(final String expression) {
    final List<String> parts = splitTopLevel(expression, '&&')
    if (parts.size() <= 1) return parseUnaryExpression(expression)

    final List<FilterExpressionAst> terms = parts.collect { parseUnaryExpression(stripOuterParentheses(it?.trim())) }
    if (terms.any { it == null }) return null
    new FilterAndExpressionAst(terms: terms)
  }

  private static FilterExpressionAst parseUnaryExpression(final String expression) {
    final String input = stripOuterParentheses(expression?.trim())
    if (!input) return null
    if (input.startsWith('!')) {
      final FilterExpressionAst term = parseOrExpression(stripOuterParentheses(input.substring(1)?.trim()))
      return term ? new FilterNotExpressionAst(term: term) : null
    }
    new FilterPredicateExpressionAst(expression: input)
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
}

