package com.k_int.web.toolkit.grammar

import static groovy.transform.TypeCheckingMode.SKIP

import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.Token
import org.antlr.v4.runtime.tree.ErrorNode
import org.antlr.v4.runtime.tree.ParseTreeWalker
import org.antlr.v4.runtime.tree.TerminalNode
import org.hibernate.criterion.Conjunction
import org.hibernate.criterion.Criterion
import org.hibernate.criterion.DetachedCriteria
import org.hibernate.criterion.Property
import org.hibernate.criterion.Restrictions
import org.hibernate.criterion.Subqueries
import org.hibernate.sql.JoinType
import org.slf4j.Logger

import com.k_int.web.toolkit.ValueConverterService
import com.k_int.web.toolkit.SimpleLookupService.PropertyDef
import com.k_int.web.toolkit.grammar.SimpleLookupWtkParser.AmbiguousFilterContext
import com.k_int.web.toolkit.grammar.SimpleLookupWtkParser.ConjunctiveFilterContext
import com.k_int.web.toolkit.grammar.SimpleLookupWtkParser.DisjunctiveFilterContext
import com.k_int.web.toolkit.grammar.SimpleLookupWtkParser.FilterGroupContext
import com.k_int.web.toolkit.grammar.SimpleLookupWtkParser.FiltersContext
import com.k_int.web.toolkit.grammar.SimpleLookupWtkParser.NegatedExpressionContext
import com.k_int.web.toolkit.grammar.SimpleLookupWtkParser.OperatorContext
import com.k_int.web.toolkit.grammar.SimpleLookupWtkParser.RangeFilterContext
import com.k_int.web.toolkit.grammar.SimpleLookupWtkParser.Range_exprContext
import com.k_int.web.toolkit.grammar.SimpleLookupWtkParser.SpecialFilterContext
import com.k_int.web.toolkit.grammar.SimpleLookupWtkParser.Special_op_exprContext
import com.k_int.web.toolkit.grammar.SimpleLookupWtkParser.Special_operatorContext
import com.k_int.web.toolkit.grammar.SimpleLookupWtkParser.StandardFilterContext
import com.k_int.web.toolkit.grammar.SimpleLookupWtkParser.SubjectFirstFilterContext
import com.k_int.web.toolkit.grammar.SimpleLookupWtkParser.ValueFirstFilterContext
import com.k_int.web.toolkit.grammar.SimpleLookupWtkParser.Value_expContext
import com.k_int.web.toolkit.utils.DomainUtils
import com.k_int.web.toolkit.utils.DomainUtils.InternalPropertyDefinition

import groovy.transform.CompileStatic

import java.util.regex.Matcher
import java.util.regex.Pattern
import java.util.stream.Stream

@CompileStatic
class SimpleLookupServiceListenerWtk implements SimpleLookupWtkListener {
  private static final String REGEX_NONE_ESCAPED_PERCENTAGE = "([^\\\\])(%)"
  
  private int indent = 0
  
  private final Logger log;
  
  // Manage stacks of current contextual data for the criteria
  private int groupCounter = 1;
  private final Deque<String> aliasPrefixStack = new ArrayDeque<>()
  private final Deque<Map<String, String>> aliasesStack = new ArrayDeque<>();
  private final Deque<Deque<Criterion>> contextStacks = new ArrayDeque<>();
  private final Deque<DetachedCriteria> targetStack = new ArrayDeque<>();
  
  private final StringBuilder trace = new StringBuilder();
  private final String rootEntityName
  private final ValueConverterService valueConverterService
  
  public SimpleLookupServiceListenerWtk(final Logger log, final ValueConverterService valueConverterService, final DetachedCriteria rootTarget, final String rootEntityName, final Map<String, String> rootAliasNames) {
    this.log = log
    this.rootEntityName = rootEntityName
    this.targetStack.push(rootTarget)
    this.valueConverterService = valueConverterService
    aliasesStack.push(rootAliasNames) // Add an empty alias stack initially.
    contextStacks.push(new ArrayDeque<Criterion>()); // empty stack on the queue
    newAliasPrefix()
  }
  
  private DetachedCriteria getRootTarget() {
    targetStack.peek()
  }
  
  private void newAliasPrefix() {
    aliasPrefixStack.push("alias${groupCounter}x".toString())
  }
  
  public StringBuilder getTrace() {
    trace
  }
  
  private Deque<Criterion> getContextStack() {
    contextStacks.peek();
  }
  
  private void traceQuery ( final String text ) {
//    if (!log.traceEnabled) return
    trace.append "${(0..(indent)).collect { ' ' }.join( '' )}${text}\n"
  }
  
  private Map<String, String> getAliasMap() {
    // Return the map representing the current contexts aliases.
    aliasesStack.peek();
  }
  
  private InternalPropertyDefinition resolvePropertyType(final String propertyPath) {
    final def propDef = DomainUtils.resolveProperty(rootEntityName, propertyPath, true)
    propDef
  }
  
  private String checkAlias(final String dotNotationString, final boolean leftJoin) {
    
    log.debug ("Checking for ${dotNotationString}")
    
    Map<String, String> aliasMap = getAliasMap()
    
    String str = aliasMap[dotNotationString]
    if (!str) {

      log.debug "Full match not found..."
        
      // No alias found for exact match.
      // Start from the front and build up aliases.
      String[] props = dotNotationString.split("\\.")
      String propStr = "${props[0]}"
      String alias = aliasMap[propStr]
      String currentAlias = alias
      int counter = 1
      while (currentAlias && counter < props.length) {
        str = "${currentAlias}"
        String test = propStr + ".${props[counter]}"
        log.debug "Testing for ${test}"
        currentAlias = aliasMap[test]
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
          alias = "${aliasPrefixStack.peek()}${aliasMap.size()}"

          // Create the alias.
          log.debug ("Creating alias: ${aliasVal} -> ${alias}")
          rootTarget.createAlias(aliasVal, alias, (leftJoin ? JoinType.LEFT_OUTER_JOIN : JoinType.INNER_JOIN))

          // Add to the map.
          propStr = i>0 ? "${propStr}.${props[i]}" : "${props[i]}"
          aliasMap[propStr] = alias
          log.debug ("Added quick string: ${propStr} -> ${alias}")
        }
      }

      // Set the string to the alias we ended on.
      str = alias
    }

    str
  }
  
  private Map getAliasedProperty (final String propname, final boolean leftJoin = false) {
    
    log.debug "Looking for property ${propname}"
    
    // Split at the dot.
    String[] levels = propname.split("\\.")

    PropertyDef ret = new PropertyDef()
    ret.putAll([
      'alias' : levels.length > 1 ? checkAlias ( levels[0..(levels.length - 2)].join('.'), leftJoin) : '',
      'property' : levels[levels.length - 1]
    ])

    ret
  }
  

  @Override
  public void visitTerminal (TerminalNode node) {
//    log.info("visitTerminal")
  }

  @Override
  public void visitErrorNode (ErrorNode node) {
//    log.info("visitErrorNode")
  }

  @Override
  public void enterEveryRule (ParserRuleContext ctx) {
//    log.info("enterEveryRule")
  }

  @Override
  public void exitEveryRule (ParserRuleContext ctx) {
//    log.info("exitEveryRule")
  }

  @Override
  public void enterValue_exp (Value_expContext ctx) {
    // NOOP
  }

  @Override
  public void exitValue_exp (Value_expContext ctx) {
    // NOOP
  }

  @Override
  public void enterOperator (OperatorContext ctx) {
    // NOOP
    
  }

  @Override
  public void exitOperator (OperatorContext ctx) {
    // NOOP
  }

  @Override
  public void enterSpecial_op_expr (Special_op_exprContext ctx) {
    traceQuery("// Special op")

    // Add a criterion to the stack.
    final String subject = ctx.subject.text
    addCriterion(
      subject,
      resolvePropertyType(subject),
      ctx.special_operator().opToken)
    
  }

  @Override
  public void exitSpecial_op_expr (Special_op_exprContext ctx) {
    // NOOP
  }

  @Override
  public void enterRange_expr (Range_exprContext ctx) {
    traceQuery("// Range op")

    // Special case that translates to 2 criterion in a conjunction
    // Subject is the same for both
    final String subject = ctx.subject.text
    final InternalPropertyDefinition subjectTypeDef = resolvePropertyType(subject);
    
    // Add the right hand side as-is.
    addCriterion(
      subject,
      subjectTypeDef,
      ctx.rhop,
      ctx.rhval.text)
    
    // Add the lh op as a reverse as the subject is in between the 2 conditions
    addCriterion(
      subject,
      subjectTypeDef,
      ctx.lhop,
      ctx.lhval.text,
      true)
    
    // Create a conjunction from the last 2 criterion and add it to the stack.
    contextStack.push(
      Restrictions.conjunction(
        contextStack.poll(),
        contextStack.poll()
      )
    )
  }

  @Override
  public void exitRange_expr (Range_exprContext ctx) {
    // NOOP
    
  }
 

  private Criterion pushCriterion( final Criterion crit ) {    
    // add it
    contextStack.push(crit)
  }

  @Override
  public void enterAmbiguousFilter (AmbiguousFilterContext ctx) {
    // LHS <op> RHS
    // This filter is hit when we don't know whether the subject of the filter
    // is the on the left or right of the op.
    traceQuery("// Ambiguous filter")
    
    boolean inverted = false
    final String lhs = ctx.lhs.text
    final Token op = ctx.operator().opToken
    final String rhs = ctx.rhs.text
		
		final String trimmedLhs = lhs?.trim()
		final String trimmedRhs = rhs?.trim()
    
    // Resolve the type of the property should the lhs be the subject
    def typeDef = resolvePropertyType(trimmedLhs)
    if (typeDef == null) {
      
      log.debug "${trimmedLhs} is not a valid property path trying RHS"
      // assume rhs is the subject
      typeDef = resolvePropertyType(trimmedRhs)
      inverted = true
    }
    
    if (typeDef == null) {
      // log.error "Neither ${lhs} or ${rhs} are valid property paths."
      throw new IllegalStateException("Neither ${trimmedLhs} or ${trimmedRhs} are valid property paths.")
    }
    
    // Valid
    traceQuery("// Established subject is ${inverted ? 'RHS' : 'LHS'}")

    // Add a criterion to the stack.
    addCriterion(
      inverted ? trimmedRhs : trimmedLhs,
      typeDef,
      op,
      inverted ? lhs : rhs,
      inverted)
  }

  @Override
  public void exitAmbiguousFilter (AmbiguousFilterContext ctx) {
    // NOOP
    
  }

	// Because the criteriaImpl visibility is package, we need groovy here.
	// Skip the sattic compiler.
	@CompileStatic(SKIP)
	private String getEntityNameFromCriteria( DetachedCriteria dc ) {
		dc?.criteriaImpl?.entityOrClassName
	}
	
  @Override
  public void enterSubjectFirstFilter (SubjectFirstFilterContext ctx) {
    traceQuery("// Subject first query")
		
		// Add a criterion to the stack.
		final String subject = ctx.subject.text
		InternalPropertyDefinition propDef = resolvePropertyType(subject);

    addCriterion(
      subject,
      resolvePropertyType(subject),
      ctx.operator().opToken,
      ctx.value.text)
  }

  @Override
  public void exitSubjectFirstFilter (SubjectFirstFilterContext ctx) {
    // NOOP
    
  }

  @Override
  public void enterValueFirstFilter (ValueFirstFilterContext ctx) {
    
    traceQuery("// Value first query")

    // Add a criterion to the stack.
    final String subject = ctx.subject.text
    addCriterion(
      subject,
      resolvePropertyType(subject),
      ctx.operator().opToken,
      ctx.value.text)
  }

  @Override
  public void exitValueFirstFilter (ValueFirstFilterContext ctx) {
    // NOOP
  }

  @Override
  public void enterStandardFilter (StandardFilterContext ctx) {
    // filter for type <sub or value><op><sub or value>
  }

  @Override
  public void exitStandardFilter (StandardFilterContext ctx) {
		// NOOP
  }

  @Override
  public void enterConjunctiveFilter (ConjunctiveFilterContext ctx) {
    // NOOP
  }

  @Override
  public void exitConjunctiveFilter (ConjunctiveFilterContext ctx) {
    // OR the top 2 criterion
    contextStack.push( Restrictions.conjunction(contextStack.poll(), contextStack.poll()) )
  }	
	
	private void addCriterion ( final String subjectExpr, final InternalPropertyDefinition rootPropDef, final Token op, final String value = null, boolean invertOp = false ) {
		if (rootPropDef == null) {
      throw new SimpleLookupServiceException("Cannot add criterion for subject: ${subjectExpr}", 1, subjectExpr);
    }
		InternalPropertyDefinition propDef = rootPropDef
		final String partialPathBySubQuery = propDef.subQuery
		String subject = subjectExpr
		if (partialPathBySubQuery) {
			subject = subject.substring(partialPathBySubQuery.length() + 1)
			
			// Check if the class implements the method.
			def methods = propDef.owner.metaClass.respondsTo(propDef.owner, 'handleLookupViaSubquery', [String.class] as Object[])
			
			// Did we get a sub criteria?
			DetachedCriteria subRoot = methods?.get(0)?.invoke(propDef.owner, [subject] as Object[]) as DetachedCriteria
			
			// Bail early if no criteria
			if (!subRoot) return
			
			contextStacks.push(new ArrayDeque<>())
			
			// Similar to a grouping we shift the target context
			aliasesStack.push([:])
			targetStack.push (subRoot)
			newAliasPrefix()
			
			// Chop the first path.
			subject = subject.substring(subject.indexOf('.') + 1)
			
			// Resolve sub path from the type to shift resolution
			propDef = DomainUtils.resolveProperty(propDef.type, subject, true)
		}
		
		final Class<?> subjectType = propDef.type
		
		// Can't ilike on none-strings. So we should change back to eq.
		final boolean requiresConversion = !String.class.isAssignableFrom(subjectType)
		final String propertyName = getAliasedProperty(subject)
		final def compValue = (value && requiresConversion) ? valueConverterService.attemptConversion(subjectType, value) : value
		
		boolean negateWholeSubq = false;
		
		switch (op.type) {
			case SimpleLookupWtkParser.EQ :
			case SimpleLookupWtkParser.EQEQ :
				log.debug "equality filter"
				pushCriterion(Restrictions.eqOrIsNull(propertyName, compValue))
				break
			case SimpleLookupWtkParser.NEQ :
				log.debug "not equal filter"
				pushCriterion(Restrictions.neOrIsNotNull(propertyName, compValue))
				break
			case SimpleLookupWtkParser.GT :
				if (invertOp) {
					pushCriterion(Restrictions.lt(propertyName, compValue))
				} else {
					pushCriterion(Restrictions.gt(propertyName, compValue))
				}
				break
			case SimpleLookupWtkParser.GE :
				if (invertOp) {
					pushCriterion(Restrictions.le(propertyName, compValue))
				} else {
					pushCriterion(Restrictions.ge(propertyName, compValue))
				}
				break
			case SimpleLookupWtkParser.LT :
				if (invertOp) {
					pushCriterion(Restrictions.gt(propertyName, compValue))
				} else {
					pushCriterion(Restrictions.lt(propertyName, compValue))
				}
				break
			case SimpleLookupWtkParser.LE :
				if (invertOp) {
					pushCriterion(Restrictions.ge(propertyName, compValue))
				} else {
					pushCriterion(Restrictions.le(propertyName, compValue))
				}
				break
			case SimpleLookupWtkParser.NCONT :
				// does not contain
				
				if (!requiresConversion) {
					final String strVal = compValue as String
					pushCriterion(Restrictions.not(
						Restrictions.ilike(propertyName, '%' + (strVal?.replaceAll(REGEX_NONE_ESCAPED_PERCENTAGE, '$1\\$2')?:'') + '%')))
				} else {
					// Can't ilike against none strings
					pushCriterion(Restrictions.not(Restrictions.eq(propertyName, compValue)));
				}
				break
			case SimpleLookupWtkParser.CONT :
				// Contains.
				if (!requiresConversion) {
					final String strVal = compValue as String
					pushCriterion(Restrictions.ilike(propertyName, '%' + (strVal?.replaceAll(REGEX_NONE_ESCAPED_PERCENTAGE, '$1\\$2')?:'') + '%'))
				} else {
					// Can't ilike against none strings
					pushCriterion(Restrictions.eq(propertyName, compValue));
				}
				
				break
			case SimpleLookupWtkParser.CIEQ :
			
				if (!requiresConversion) {
					final String strVal = compValue as String
					pushCriterion(Restrictions.ilike(propertyName, (strVal?.replaceAll(REGEX_NONE_ESCAPED_PERCENTAGE, '$1\\$2')?:'')))
				} else {
					// Can't ilike against none strings
					pushCriterion(Restrictions.eq(propertyName, compValue));
				}
				
				break
				
			// Special operations
			case SimpleLookupWtkParser.ISNULL :
				pushCriterion(Restrictions.isNull(propertyName))
				break
			case SimpleLookupWtkParser.ISNOTNULL :
			case SimpleLookupWtkParser.ISSET:
				pushCriterion(Restrictions.isNotNull(propertyName))
				break
			case SimpleLookupWtkParser.ISNOTSET :
				// Because of the way sub queries are used in some cases we need to defer this
				// negation to negate the whole subquery when special cases, like customProperties are used. 
				negateWholeSubq = true
				
				pushCriterion(Restrictions.conjunction(Restrictions.isNotNull(propertyName)))
				break
			case SimpleLookupWtkParser.ISEMPTY :
				pushCriterion(Restrictions.isEmpty(propertyName))
				break
			case SimpleLookupWtkParser.ISNOTEMPTY :
				pushCriterion(Restrictions.isNotEmpty(propertyName))
				break
		}
		
		// Reinstate the previous stacks.
		if (partialPathBySubQuery) {
			// Add as a sub query...
			// Should be exactly 1 criterion in the stack
			Deque<Criterion> groupCriterionStack = contextStacks.poll()
			if (groupCriterionStack.size() != 1) throw new IllegalStateException('More than 1 criterion resulted from subquery component')
			
			// Pop the criteria with the aliases defined.
			final DetachedCriteria sq =
				targetStack.poll()
				.add(groupCriterionStack.poll())
				
			// Add as an in to the current stack. The implementation should
			// add the appropriate projection
			final String targetId = "${getAliasedProperty(partialPathBySubQuery)?.property}.id"
			contextStack.push(Subqueries.propertyIn(targetId, sq))
			
			// Drop alias stacks for GCing
			aliasesStack.poll() // No longer needed.
			aliasPrefixStack.poll() // No longer needed.
		}
		
		if (negateWholeSubq) {
			// We should wrap the head of the context stack in a not.
			Criterion head = contextStack.poll()
			contextStack.push(Restrictions.not(head))
		}
	}

  @Override
  public void enterFilterGroup (FilterGroupContext ctx) {
    contextStacks.push(new ArrayDeque<>())
    
    // New aliases for this group.
    aliasesStack.push([:])
    groupCounter ++;
    targetStack.push (
      DetachedCriteria.forEntityName(rootEntityName, "__sub_query_${groupCounter}")) 
    newAliasPrefix()
  }

  @Override
  public void exitFilterGroup (FilterGroupContext ctx) {
    // Should be exactly 1 criterion in the stack
    Deque<Criterion> groupCriterionStack = contextStacks.poll()
    if (groupCriterionStack.size() != 1) throw new IllegalStateException('More than 1 criterion resulted from group')
    
    // Pop the criteria with the aliases defined.
    final DetachedCriteria sq =
      targetStack.poll()
      .add(groupCriterionStack.poll())
      .setProjection(Property.forName("id"))
      
    // Add as an in to the current stack.
    contextStack.push(Subqueries.propertyIn("id", sq))
    
    
    // Drop alias stack
    aliasesStack.poll() // No longer needed.
    aliasPrefixStack.poll() // No longer needed.
  }

  @Override
  public void enterSpecialFilter (SpecialFilterContext ctx) {
    // NOOP
  }

  @Override
  public void exitSpecialFilter (SpecialFilterContext ctx) {
    // NOOP
  }

  @Override
  public void enterNegatedExpression (NegatedExpressionContext ctx) {
    // NOOP
  }

  @Override
  public void exitNegatedExpression (NegatedExpressionContext ctx) {
    // Just negate the last criterion
    contextStack.push( Restrictions.not(contextStack.poll()) )
  }

  @Override
  public void enterDisjunctiveFilter (DisjunctiveFilterContext ctx) {
    // NOOP
    
  }

  @Override
  public void exitDisjunctiveFilter (DisjunctiveFilterContext ctx) {
    // OR the top 2 criterion
    contextStack.push( Restrictions.disjunction(contextStack.poll(), contextStack.poll()) )    
  }

  @Override
  public void enterRangeFilter (RangeFilterContext ctx) {
    // NOOP
  }

  @Override
  public void exitRangeFilter (RangeFilterContext ctx) {
    // NOOP
  }

  @Override
  public void enterFilters (FiltersContext ctx) {
    log.info('Begin parsing filters')
  }

  @Override
  public void exitFilters (FiltersContext ctx) {
    log.info('Finished parsing filters')
    
    // Should be 0 or 1 criterion in the stack
    
    // If we have criterion in the stack we drain them
    // into a single AND and then re add as the whole
    // result of traversing the parse tree
    if (contextStack.size() > 1) {
      
      final Conjunction result = Restrictions.conjunction()
      Criterion c
      while ((c = contextStack.poll()) != null) {
        result.add(c)
      }

      contextStack.push(result)
    }
    
    // Else 0 or 1 item represents the result already, no need to join them
  }
  
  public Criterion getResult () {
    if (contextStacks.size() > 1 || contextStack.size() > 1) throw new IllegalStateException('Multiple criterion exist')

    contextStack.poll();
  }

  @Override
  public void enterSpecial_operator (Special_operatorContext ctx) {
    // NOOP
  }

  @Override
  public void exitSpecial_operator (Special_operatorContext ctx) {
    // NOOP
  }
}
