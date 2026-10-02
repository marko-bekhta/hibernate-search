package org.hibernate.search.util.json.impl;

import java.util.Optional;
import java.util.function.Supplier;

import org.hibernate.search.util.common.AssertionFailure;
import org.hibernate.search.util.json.JsonElement;
import org.hibernate.search.util.json.JsonObject;
import org.hibernate.search.util.json.spi.JsonObjectAccessor;

public class RootJsonAccessor implements JsonCompositeAccessor<JsonElement> {

	// Wrap the instance in a JsonObjectAccessor, because we know the root is an object
	public static final JsonObjectAccessor INSTANCE = new JsonObjectAccessorImpl( new RootJsonAccessor() );

	private RootJsonAccessor() {
		// Private, use INSTANCE instead.
	}

	@Override
	public Optional<JsonElement> get(JsonObject root) {
		return Optional.of( requireRoot( root ) );
	}

	private JsonObject requireRoot(JsonObject root) {
		if ( root == null ) {
			throw new AssertionFailure( "A null root was encountered" );
		}
		else {
			return root;
		}
	}

	@Override
	public void set(JsonObject root, JsonElement value) {
		throw new UnsupportedOperationException( "Cannot set the root element" );
	}

	@Override
	public JsonObject getOrCreate(JsonObject root, Supplier<? extends JsonElement> newValueSupplier) {
		return requireRoot( root );
	}

	@Override
	public JsonObject getOrCreate(JsonObject root) {
		return requireRoot( root );
	}

	@Override
	public String toString() {
		return "root";
	}
}
