/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.search.engine.search.predicate.dsl;

/**
 * The final step in a "prefix" predicate definition, where optional parameters can be set.
 *
 * @param <S> The "self" type (the actual exposed type of this step).
 */
public interface PrefixPredicateOptionsStep<S extends PrefixPredicateOptionsStep<?>>
		extends PredicateFinalStep, PredicateScoreStep<S> {

}
