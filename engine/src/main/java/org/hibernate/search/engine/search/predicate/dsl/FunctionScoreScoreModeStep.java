package org.hibernate.search.engine.search.predicate.dsl;

/**
 * The step in a "function score" predicate definition where the score mode can be selected.
 * <p>
 * The score mode defines how scores from multiple functions are combined with each other.
 *
 * @param <N> The type of the next step (returned by each option method).
 */
public interface FunctionScoreScoreModeStep<N> {

	N multiply();

	N sum();

	N avg();

	N first();

	N max();

	N min();

}
