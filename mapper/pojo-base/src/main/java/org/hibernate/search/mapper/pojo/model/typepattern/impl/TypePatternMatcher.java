/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.search.mapper.pojo.model.typepattern.impl;

import org.hibernate.search.mapper.pojo.model.spi.PojoTypeModel;

/**
 * A pattern-matching implementation for generic types.
 * <p>
 * For example, such a pattern could be described with the expression {@code Collection<?>},
 * which would only match against {@code Collection} and its subclasses.
 */
public interface TypePatternMatcher {

	/**
	 * Attempts to match a given type against this pattern,
	 * and return the result as a {@code boolean}.
	 *
	 * @param typeToInspect A type that may, or may not, match the pattern.
	 * @return {@code true} in the event of a match, {@code false} otherwise.
	 */
	boolean matches(PojoTypeModel<?> typeToInspect);

	default TypePatternMatcher negate() {
		return new NegatingTypePatternMatcher( this );
	}

	default TypePatternMatcher and(TypePatternMatcher other) {
		return new AndTypePatternMatcher( this, other );
	}

}
