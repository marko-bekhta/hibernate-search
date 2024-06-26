/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.search.engine.backend.types.dsl;

import org.hibernate.search.engine.backend.types.Aggregable;
import org.hibernate.search.engine.backend.types.Sortable;

/**
 * The initial and final step in a "standard" index field type definition, where optional parameters can be set.
 * <p>
 * "Standard" index field types are types exposing a common set capabilities that are considered "standard",
 * such as search or projection.
 *
 * @param <S> The "self" type (the actual exposed type of this step).
 * @param <F> The type of field values.
 */
public interface StandardIndexFieldTypeOptionsStep<S extends StandardIndexFieldTypeOptionsStep<?, F>, F>
		extends SearchableProjectableIndexFieldTypeOptionsStep<S, F> {

	/**
	 * @param sortable Whether this field should be sortable.
	 * @return {@code this}, for method chaining.
	 * @see Sortable
	 */
	S sortable(Sortable sortable);

	/**
	 * @param aggregable Whether aggregations are enabled for this field.
	 * @return {@code this}, for method chaining.
	 * @see Aggregable
	 */
	S aggregable(Aggregable aggregable);

}
