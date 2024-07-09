/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.search.engine.backend.types.converter.spi;

/**
 * A converter for values passed to the DSL.
 *
 * @param <F> The mapping field type
 * @param <E> The encoded type, i.e. the type backend is using to represent the field.
 */
public interface Codec<F, E> {

	E encode(F value);

	F decode(E value);
}
