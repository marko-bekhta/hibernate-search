package org.hibernate.search.util.json.spi;

import org.hibernate.search.util.common.AssertionFailure;

import org.hibernate.search.util.json.JsonElement;

public abstract class JsonElementType<T> {

	protected JsonElementType() {
		// Not allowed
	}

	public final T fromElement(JsonElement element) {
		if ( element == null ) {
			return null;
		}
		else if ( isInstance( element ) ) {
			return nullUnsafeFromElement( element );
		}
		else {
			/*
			 * Callers are supposed to call isInstance first,
			 * so failing here is actually an internal error.
			 */
			throw new AssertionFailure( element + " cannot be cast to " + this );
		}
	}

	protected abstract T nullUnsafeFromElement(JsonElement element);

	public final JsonElement toElement(T value) {
		if ( value == null ) {
			return null;
		}
		else {
			return nullUnsafeToElement( value );
		}
	}

	protected abstract JsonElement nullUnsafeToElement(T element);

	public final boolean isInstance(JsonElement element) {
		return element != null && nullUnsafeIsInstance( element );
	}

	protected abstract boolean nullUnsafeIsInstance(JsonElement element);
}
