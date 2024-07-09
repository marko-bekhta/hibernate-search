/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.search.engine.backend.types.converter.spi;

import java.lang.invoke.MethodHandles;
import java.util.function.Function;

import org.hibernate.search.engine.backend.types.converter.ToDocumentValueConverter;
import org.hibernate.search.engine.backend.types.converter.runtime.ToDocumentValueConvertContext;
import org.hibernate.search.engine.backend.types.converter.runtime.ToDocumentValueConvertContextExtension;
import org.hibernate.search.engine.logging.impl.Log;
import org.hibernate.search.util.common.annotation.Incubating;
import org.hibernate.search.util.common.impl.Contracts;
import org.hibernate.search.util.common.logging.impl.LoggerFactory;
import org.hibernate.search.util.common.reporting.spi.EventContextProvider;

/**
 * A converter for values passed to the DSL.
 *
 * @param <V> The type of values passed to the DSL.
 * @param <F> The type of converted values passed to the backend.
 */
public final class DslConverter<V, F, E> {

	private static final Log log = LoggerFactory.make( Log.class, MethodHandles.lookup() );

	public static <F> DslConverter<F, F, F> passThrough(Class<F> fieldAndValueType) {
		return delegate( fieldAndValueType, new PassThroughToDocumentValueConverter<>(), new PassThroughCodec<>() );
	}

	public static <V, F> DslConverter<V, F, F> delegate(Class<V> valueType, ToDocumentValueConverter<V, ? extends F> delegate) {
		return new DslConverter<>( valueType, delegate, new PassThroughCodec<>() );
	}

	public static <V, F, E> DslConverter<V, F, E> delegate(Class<V> valueType,
			ToDocumentValueConverter<V, ? extends F> delegate, Codec<F, E> codec) {
		return new DslConverter<>( valueType, delegate, codec );
	}

	public static <F, E> DslConverter<F, F, E> delegate(Class<F> valueClass, Codec<F, E> codec) {
		return delegate( valueClass, new PassThroughToDocumentValueConverter<>(), codec );
	}

	private final Class<V> valueType;
	private final ToDocumentValueConverter<V, ? extends F> delegate;
	private final Codec<F, E> codec;

	private DslConverter(Class<V> valueType, ToDocumentValueConverter<V, ? extends F> delegate, Codec<F, E> codec) {
		Contracts.assertNotNull( valueType, "valueType" );
		Contracts.assertNotNull( delegate, "delegate" );
		Contracts.assertNotNull( codec, "codec" );
		this.valueType = valueType;
		this.delegate = delegate;
		this.codec = codec;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[valueType=" + valueType.getName() + ",delegate=" + delegate + ",codec=" + codec
				+ "]";
	}

	public Class<V> valueType() {
		return valueType;
	}

	/**
	 * @param value The source value to convert.
	 * @param context A context that can be
	 * {@link ToDocumentValueConvertContext#extension(ToDocumentValueConvertContextExtension) extended}
	 * to a more useful type, giving access to such things as a Hibernate ORM SessionFactory (if using the Hibernate ORM mapper).
	 * @return The converted index field value.
	 */
	public E toDocumentValue(V value, ToDocumentValueConvertContext context) {
		return codec.encode( delegate.toDocumentValue( value, context ) );
	}

	/**
	 * Convert an input value of unknown type that may not have the required type {@code V}.
	 * <p>
	 * Called when passing values to the predicate DSL in particular.
	 *
	 * @param value The value to convert.
	 * @param context A context that can be
	 * {@link ToDocumentValueConvertContext#extension(ToDocumentValueConvertContextExtension) extended}
	 * to a more useful type, giving access to such things as a Hibernate ORM SessionFactory (if using the Hibernate ORM mapper).
	 * @return The converted index field value.
	 *
	 * @throws RuntimeException If the value does not match the expected type.
	 */
	public E unknownTypeToDocumentValue(Object value, ToDocumentValueConvertContext context) {
		return codec.encode( delegate.toDocumentValue( valueType.cast( value ), context ) );
	}

	/**
	 * Check whether DSL arguments values can have the given type,
	 * and returns the DSL converter with an appropriate type.
	 *
	 * @param inputTypeCandidate A candidate type for input values.
	 * @param eventContextProvider A provider for the event context to pass to produced exceptions.
	 * @param <T> A candidate type for input values.
	 * @return The DSL converter, guaranteed to accept values to the given type.
	 *
	 * @throws org.hibernate.search.util.common.SearchException If the DSL converter cannot accept values to the given type.
	 */
	@SuppressWarnings("unchecked") // We check the cast is legal by asking the converter
	public <T> DslConverter<? super T, F, E> withInputType(Class<T> inputTypeCandidate,
			EventContextProvider eventContextProvider) {
		if ( !valueType.isAssignableFrom( inputTypeCandidate ) ) {
			throw log.invalidDslArgumentType( inputTypeCandidate, valueType, eventContextProvider.eventContext() );
		}
		return (DslConverter<? super T, F, E>) this;
	}

	/**
	 * @param other Another {@link DslConverter}, never {@code null}.
	 * @return {@code true} if the given object behaves exactly the same as this object,
	 * i.e. its {@link #toDocumentValue(Object, ToDocumentValueConvertContext)} and {@link #unknownTypeToDocumentValue(Object, ToDocumentValueConvertContext)}
	 * methods are guaranteed to always return the same value as this object's
	 * when given the same input. {@code false} otherwise, or when in doubt.
	 */
	public boolean isCompatibleWith(DslConverter<?, ?, ?> other) {
		return delegate.isCompatibleWith( other.delegate );
	}

	private static class PassThroughCodec<F> implements Codec<F, F> {
		@Override
		public F encode(F value) {
			return value;
		}

		@Override
		public F decode(F value) {
			return value;
		}
	}
}
