package org.hibernate.search.util.json.impl;

import java.util.Optional;
import java.util.function.Supplier;

import org.hibernate.search.util.json.JsonElement;
import org.hibernate.search.util.json.JsonObject;
import org.hibernate.search.util.json.spi.JsonAccessor;
import org.hibernate.search.util.json.spi.JsonElementType;
import org.hibernate.search.util.json.spi.UnexpectedJsonElementTypeException;

/**
 * A {@link JsonAccessor} whose purpose is to check type information
 * on the node being accessed.
 * <p>
 * The node being accessed is provided by the "parent" accessor.
 *
 */
abstract class AbstractTypingJsonAccessor<T> extends AbstractNonRootJsonAccessor<JsonElement, T> {

	public AbstractTypingJsonAccessor(JsonAccessor<JsonElement> parentAccessor) {
		super( parentAccessor );
	}

	@Override
	public Optional<T> get(JsonObject root) {
		return getParentAccessor().get( root ).map( this::fromElement );
	}

	@Override
	public void set(JsonObject root, T newValue) {
		getParentAccessor().set( root, toElement( newValue ) );
	}

	@Override
	public T getOrCreate(JsonObject root, Supplier<? extends T> newValueSupplier) {
		return fromElement( getParentAccessor().getOrCreate( root, () -> toElement( newValueSupplier.get() ) ) );
	}

	private T fromElement(JsonElement parent) {
		JsonElementType<T> expectedType = getExpectedElementType();
		if ( parent == null || parent.isJsonNull() ) {
			return null;
		}
		else if ( !expectedType.isInstance( parent ) ) {
			throw new UnexpectedJsonElementTypeException( this, expectedType, parent );
		}
		else {
			return expectedType.fromElement( parent );
		}
	}

	protected JsonElement toElement(T value) {
		return getExpectedElementType().toElement( value );
	}

	protected abstract JsonElementType<T> getExpectedElementType();

	@Override
	protected void appendRuntimeRelativePath(StringBuilder path) {
		path.append( '(' ).append( getExpectedElementType() ).append( ')' );
	}

	@Override
	protected void appendStaticRelativePath(StringBuilder path, boolean first) {
		// No-op: the type doesn't show up in this path
	}

}
