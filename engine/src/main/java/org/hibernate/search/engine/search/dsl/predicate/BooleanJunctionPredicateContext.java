/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.engine.search.dsl.predicate;

import java.util.function.Consumer;
import java.util.function.Function;

import org.hibernate.search.engine.search.SearchPredicate;

/**
 * The context used when defining a boolean junction, allowing in particular to add clauses.
 * <p>
 * Different types of clauses have different effects, see below.
 *
 * <h3 id="must">"must" clauses</h3>
 * <p>
 * "must" clauses are required to match: if they don't match, then the boolean predicate will not match.
 * <p>
 * Matching "must" clauses are taken into account in score computation.
 *
 * <h3 id="mustnot">"must not" clauses</h3>
 * <p>
 * "must not" clauses are required to not match: if they don't match, then the boolean predicate will not match.
 * <p>
 * "must not" clauses are ignored from score computation.
 * <p>
 * "must not" clauses are
 *
 * <h3 id="filter">"filter" clauses</h3>
 * <p>
 * "filter" clauses are required to match: if they don't match, then the boolean predicate will not match.
 * <p>
 * "filter" clauses are ignored from score computation,
 * and so are any clauses of boolean predicates contained in the filter clause (even "match" or "should" clauses).
 *
 * <h3 id="should">"should" clauses</h3>
 * <p>
 * "should" clauses may optionally match, and are required to match depending on the context.
 * <ul>
 * <li>
 *     When there isn't any "must" clause nor any "filter" clause in the boolean predicate,
 *     and there is no <a href="MinimumShouldMatchContext.html#minimumshouldmatch">"minimumShouldMatch" constraint</a>,
 *     then at least one "should" clause is required to match.
 *     Simply put, in this case, the "should" clauses
 *     <strong>behave as if there was an "OR" operator between each of them</strong>.
 * </li>
 * <li>
 *     When there is at least one "must" clause or one "filter" clause in the boolean predicate,
 *     and there is no <a href="MinimumShouldMatchContext.html#minimumshouldmatch">"minimumShouldMatch" constraint</a>,
 *     then the "should" clauses are not required to match,
 *     and are simply used for scoring.
 * </li>
 * <li>
 *     When there is at least one <a href="MinimumShouldMatchContext.html#minimumshouldmatch">"minimumShouldMatch" constraint</a>,
 *     then the "should" clauses are required according to the "minimumShouldMatch" constraints.
 * </li>
 * </ul>
 * <p>
 * Matching "should" clauses are taken into account in score computation.
 */
public interface BooleanJunctionPredicateContext extends
		SearchPredicateScoreContext<BooleanJunctionPredicateContext>, SearchPredicateTerminalContext {

	/**
	 * Add a <a href="#must">"must" clause</a> based on a previously-built {@link SearchPredicate}.
	 *
	 * @param searchPredicate The predicate that must match.
	 * @return {@code this}, for method chaining.
	 */
	BooleanJunctionPredicateContext must(SearchPredicate searchPredicate);

	/**
	 * Add a <a href="#mustnot">"must not" clause</a> based on a previously-built {@link SearchPredicate}.
	 *
	 * @param searchPredicate The predicate that must not match.
	 * @return {@code this}, for method chaining.
	 */
	BooleanJunctionPredicateContext mustNot(SearchPredicate searchPredicate);

	/**
	 * Add a <a href="#should">"should" clause</a> based on a previously-built {@link SearchPredicate}.
	 *
	 * @param searchPredicate The predicate that should match.
	 * @return {@code this}, for method chaining.
	 */
	BooleanJunctionPredicateContext should(SearchPredicate searchPredicate);

	/**
	 * Add a <a href="#filter">"filter" clause</a> based on a previously-built {@link SearchPredicate}.
	 *
	 * @param searchPredicate The predicate that must match.
	 * @return {@code this}, for method chaining.
	 */
	BooleanJunctionPredicateContext filter(SearchPredicate searchPredicate);

	/*
	 * Syntactic sugar allowing to skip the toPredicate() call by passing a SearchPredicateTerminalContext
	 * directly.
	 */

	/**
	 * Add a <a href="#must">"must" clause</a> based on an almost-built {@link SearchPredicate}.
	 *
	 * @param terminalContext The terminal context allowing to retrieve a {@link SearchPredicate}.
	 * @return {@code this}, for method chaining.
	 */
	default BooleanJunctionPredicateContext must(SearchPredicateTerminalContext terminalContext) {
		return must( terminalContext.toPredicate() );
	}

	/**
	 * Add a <a href="#mustnot">"must not" clause</a> based on an almost-built {@link SearchPredicate}.
	 *
	 * @param terminalContext The terminal context allowing to retrieve a {@link SearchPredicate}.
	 * @return {@code this}, for method chaining.
	 */
	default BooleanJunctionPredicateContext mustNot(SearchPredicateTerminalContext terminalContext) {
		return mustNot( terminalContext.toPredicate() );
	}

	/**
	 * Add a <a href="#should">"should" clause</a> based on an almost-built {@link SearchPredicate}.
	 *
	 * @param terminalContext The terminal context allowing to retrieve a {@link SearchPredicate}.
	 * @return {@code this}, for method chaining.
	 */
	default BooleanJunctionPredicateContext should(SearchPredicateTerminalContext terminalContext) {
		return should( terminalContext.toPredicate() );
	}

	/**
	 * Add a <a href="#filter">"filter" clause</a> based on an almost-built {@link SearchPredicate}.
	 *
	 * @param terminalContext The terminal context allowing to retrieve a {@link SearchPredicate}.
	 * @return {@code this}, for method chaining.
	 */
	default BooleanJunctionPredicateContext filter(SearchPredicateTerminalContext terminalContext) {
		return filter( terminalContext.toPredicate() );
	}

	/*
	 * Alternative syntax taking advantage of lambdas,
	 * allowing the structure of the predicate building code to mirror the structure of predicates,
	 * even for complex predicate building requiring for example if/else statements.
	 */

	/**
	 * Add a <a href="#must">"must" clause</a> to be defined by the given function.
	 * <p>
	 * Best used with lambda expressions.
	 *
	 * @param clauseContributor A function that will use the DSL context passed in parameter to create a predicate,
	 * returning the resulting terminal context.
	 * Should generally be a lambda expression.
	 * @return {@code this}, for method chaining.
	 */
	BooleanJunctionPredicateContext must(
			Function<? super SearchPredicateFactoryContext, ? extends SearchPredicateTerminalContext> clauseContributor);

	/**
	 * Add a <a href="#mustnot">"must not" clause</a> to be defined by the given function.
	 * <p>
	 * Best used with lambda expressions.
	 *
	 * @param clauseContributor A function that will use the DSL context passed in parameter to create a predicate,
	 * returning the resulting terminal context.
	 * Should generally be a lambda expression.
	 * @return {@code this}, for method chaining.
	 */
	BooleanJunctionPredicateContext mustNot(
			Function<? super SearchPredicateFactoryContext, ? extends SearchPredicateTerminalContext> clauseContributor);

	/**
	 * Add a <a href="#should">"should" clause</a> to be defined by the given function.
	 * <p>
	 * Best used with lambda expressions.
	 *
	 * @param clauseContributor A function that will use the DSL context passed in parameter to create a predicate,
	 * returning the resulting terminal context.
	 * Should generally be a lambda expression.
	 * @return {@code this}, for method chaining.
	 */
	BooleanJunctionPredicateContext should(
			Function<? super SearchPredicateFactoryContext, ? extends SearchPredicateTerminalContext> clauseContributor);

	/**
	 * Add a <a href="#filter">"filter" clause</a> to be defined by the given function.
	 * <p>
	 * Best used with lambda expressions.
	 *
	 * @param clauseContributor A function that will use the DSL context passed in parameter to create a predicate,
	 * returning the resulting terminal context.
	 * Should generally be a lambda expression.
	 * @return {@code this}, for method chaining.
	 */
	BooleanJunctionPredicateContext filter(
			Function<? super SearchPredicateFactoryContext, ? extends SearchPredicateTerminalContext> clauseContributor);

	/*
	 * Options
	 */

	/**
	 * Add a default <a href="MinimumShouldMatchContext.html#minimumshouldmatch">"minimumShouldMatch" constraint</a>.
	 *
	 * @param matchingClausesNumber A definition of the number of "should" clauses that have to match.
	 * If positive, it is the number of clauses that have to match.
	 * See <a href="MinimumShouldMatchContext.html#minimumshouldmatch-minimum">Definition of the minimum</a>
	 * for details and possible values, in particular negative values.
	 * @return {@code this}, for method chaining.
	 */
	default BooleanJunctionPredicateContext minimumShouldMatchNumber(int matchingClausesNumber) {
		return minimumShouldMatch()
				.ifMoreThan( 0 ).thenRequireNumber( matchingClausesNumber )
				.end();
	}

	/**
	 * Add a default <a href="MinimumShouldMatchContext.html#minimumshouldmatch">"minimumShouldMatch" constraint</a>.
	 *
	 * @param matchingClausesPercent A definition of the number of "should" clauses that have to match, as a percentage.
	 * If positive, it is the percentage of the total number of "should" clauses that have to match.
	 * See <a href="MinimumShouldMatchContext.html#minimumshouldmatch-minimum">Definition of the minimum</a>
	 * for details and possible values, in particular negative values.
	 * @return {@code this}, for method chaining.
	 */
	default BooleanJunctionPredicateContext minimumShouldMatchPercent(int matchingClausesPercent) {
		return minimumShouldMatch()
				.ifMoreThan( 0 ).thenRequirePercent( matchingClausesPercent )
				.end();
	}

	/**
	 * Start defining the minimum number of "should" constraints that have to match
	 * in order for the boolean predicate to match.
	 * <p>
	 * See {@link MinimumShouldMatchContext}.
	 *
	 * @return A {@link MinimumShouldMatchContext} allowing to define constraints.
	 */
	MinimumShouldMatchContext<? extends BooleanJunctionPredicateContext> minimumShouldMatch();

	/**
	 * Start defining the minimum number of "should" constraints that have to match
	 * in order for the boolean predicate to match.
	 * <p>
	 * See {@link MinimumShouldMatchContext}.
	 *
	 * @param constraintContributor A consumer that will add constraints to the context passed in parameter.
	 * Should generally be a lambda expression.
	 * @return {@code this}, for method chaining.
	 */
	BooleanJunctionPredicateContext minimumShouldMatch(
			Consumer<? super MinimumShouldMatchContext<?>> constraintContributor);

}
