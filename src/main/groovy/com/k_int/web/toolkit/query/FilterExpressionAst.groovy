package com.k_int.web.toolkit.query

interface FilterExpressionAst {
}

final class FilterAndExpressionAst implements FilterExpressionAst {
  List<FilterExpressionAst> terms = []
}

final class FilterOrExpressionAst implements FilterExpressionAst {
  List<FilterExpressionAst> terms = []
}

final class FilterNotExpressionAst implements FilterExpressionAst {
  FilterExpressionAst term
}

final class FilterPredicateExpressionAst implements FilterExpressionAst {
  String expression
}

