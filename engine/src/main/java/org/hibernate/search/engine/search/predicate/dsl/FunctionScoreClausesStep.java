/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.search.engine.search.predicate.dsl;

import java.util.function.Function;

import org.hibernate.search.engine.search.predicate.SearchPredicate;

/**
 * The step in a "function score" predicate definition where score functions can be added.
 * <p>
 * Follows the same flow pattern as {@link BooleanPredicateClausesStep}: functions are clause methods,
 * config steps extend this step so you can break out to add another function at any point.
 *
 * @param <SR> Scope root type.
 * @param <S> The "self" type (the actual exposed type of this step).
 */
public interface FunctionScoreClausesStep<SR, S extends FunctionScoreClausesStep<SR, S>>
		extends PredicateFinalStep, PredicateScoreStep<S> {

	/**
	 * Add a field value factor function that reads a numeric field value to influence the score.
	 *
	 * @param fieldPath The path to the numeric field.
	 * @return The next step, where the function can be configured.
	 */
	FunctionScoreFieldValueFactorStep<SR, S> fieldValueFactor(String fieldPath);

	/**
	 * Add a standalone weight function that produces a constant score.
	 * <p>
	 * In Elasticsearch terms, this is the {@code weight} function: it produces a base score
	 * of 1.0 with a per-function weight equal to the given value.
	 *
	 * @param weight The weight value.
	 * @return The next step, where a filter can optionally be set.
	 */
	FunctionScoreWeightStep<SR, S> weight(float weight);

	/**
	 * Set the score mode, which defines how scores from multiple functions are combined.
	 *
	 * @return The next step, where the score mode can be selected.
	 */
	FunctionScoreScoreModeStep<S> scoreMode();

	/**
	 * Set the boost mode, which defines how the combined function score is merged
	 * with the original query score.
	 *
	 * @return The next step, where the boost mode can be selected.
	 */
	FunctionScoreBoostModeStep<S> boostMode();

}
