grammar SimpleLookupWtk;

@header {
  package com.k_int.web.toolkit.grammar;
}

/* Case insensitive fragments */
//fragment A       : [aA];
//fragment B       : [bB];
//fragment C       : [cC];
//fragment D       : [dD];
fragment E          : [eE];
//fragment F       : [fF];
//fragment G       : [gG];
//fragment H       : [hH];
fragment I          : [iI];
//fragment J       : [jJ];
//fragment K       : [kK];
fragment L          : [lL];
fragment M          : [mM];
fragment N          : [nN];
fragment O          : [oO];
fragment P          : [pP];
//fragment Q       : [qQ];
//fragment R       : [rR];
fragment S          : [sS];
fragment T          : [tT];
fragment U          : [uU];
//fragment V       : [vV];
//fragment W       : [wW];
//fragment X       : [xX];
fragment Y          : [yY];
//fragment Z       : [zZ];

fragment LOWERCASE  : [a-z];
fragment UPPERCASE  : [A-Z];
fragment DIGIT      : [0-9];

/** Operators **/

// Match any escaped character
ESCAPED_SPECIAL      : '\\' ~[\r\n\t];

NEGATED              : '!';
GROUP_START          : '(';
GROUP_END            : ')';

AND                  : '&&';
OR                   : '||';

IS                   : I S ;
NOT                  : N O T ;

NULL                 : N U L L;
EMPTY                : E M P T Y;
SET                  : S E T;

EQ                   : '=';
EQEQ                 : '==';
CIEQ                 : '=i=' ;
NEQ                  : '!=' | '<>' ;
GT                   : '>' ;
GE                   : '>=' ;
LT                   : '<' ;
LE                   : '<=' ;
NCONT                : '!~' ;
CONT                 : '=~' ;

ISNULL               : IS NULL;
ISNOTNULL            : IS NOT NULL;
ISSET                : IS SET;
ISNOTSET             : IS NOT SET;
ISEMPTY              : IS EMPTY;
ISNOTEMPTY           : IS NOT EMPTY;

WHITESPACE           : (' ' | '\t') ;
NEWLINE              : ('\r'? '\n' | '\r')+;
SUBJECT              : (LOWERCASE | UPPERCASE) ((LOWERCASE | UPPERCASE | DIGIT | '_' | '.')* (LOWERCASE | UPPERCASE | DIGIT))?;

// Capture any that haven't explicitly matched above
ANY                  :  .;

// Match any TOKEN except the single tokens that need escaping in regular values
value_exp
  : ~( NEGATED | GROUP_START | GROUP_END | GT | LT | EQ | '\\' )+?
;

operator
  : opToken=(GT | GE | LT | LE | EQ | EQEQ | CIEQ | NEQ | NCONT | CONT)
;

special_operator
  : opToken=(ISNULL | ISNOTNULL | ISSET | ISNOTSET | ISEMPTY | ISNOTEMPTY)
;

special_op_expr
  : subject=SUBJECT WHITESPACE+ special_operator;

range_expr
  : lhval=value_exp lhop=(GT | GE | LT | LE) subject=SUBJECT rhop=(GT | GE | LT | LE) rhval=value_exp;

standard_expr
  : subject=SUBJECT operator value=value_exp                                      #subjectFirstFilter
  | value=value_exp operator subject=SUBJECT                                      #valueFirstFilter
  | lhs=value_exp operator rhs=value_exp                                          #ambiguousFilter
;

filter_expr
    : NEGATED filter_expr                                                         #negatedExpression
    | WHITESPACE*? GROUP_START group_criteria=filter_expr GROUP_END WHITESPACE*?  #filterGroup
    | filter_expr (WHITESPACE*? AND WHITESPACE*? filter_expr)+                    #conjunctiveFilter
    | filter_expr (WHITESPACE*? OR WHITESPACE*? filter_expr)+                     #disjunctiveFilter
    | special_op_expr                                                             #specialFilter
    | range_expr                                                                  #rangeFilter
    | standard_expr                                                               #standardFilter
;


filters
  : filter_expr (NEWLINE filter_expr)* EOF
;

