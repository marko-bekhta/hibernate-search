package org.hibernate.search.util.json.impl;

import java.util.Optional;
import java.util.function.Supplier;

import org.hibernate.search.util.json.JsonArray;
import org.hibernate.search.util.json.JsonElement;
import org.hibernate.search.util.json.JsonObject;
import org.hibernate.search.util.json.spi.JsonAccessor;
import org.hibernate.search.util.json.spi.UnknownTypeJsonAccessor;

/** Read adaptation never changes a selected wrapper; set replaces the selected raw location. */
final class SingletonArrayJsonAccessor extends AbstractCrawlingJsonAccessor<JsonElement> {
	private final UnknownTypeJsonAccessor selected;

	SingletonArrayJsonAccessor(UnknownTypeJsonAccessor selected) {
		super( new SelectedLocation( selected ) );
		this.selected = selected;
	}

	@Override
	protected JsonElement doGet(JsonElement parent) {
		return unwrap( parent );
	}

	@Override
	protected void doSet(JsonElement parent, JsonElement value) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void set(JsonObject root, JsonElement value) {
		selected.set( root, value );
	}

	@Override
	public JsonElement getOrCreate(JsonObject root, Supplier<? extends JsonElement> supplier) {
		return unwrap( selected.getOrCreate( root, supplier ) );
	}

	private JsonElement unwrap(JsonElement value) {
		while ( value != null && value.isJsonArray() ) {
			JsonArray array = value.getAsJsonArray();
			if ( array.size() == 0 ) {
				return null;
			}
			if ( array.size() != 1 ) {
				throw new IllegalStateException(
						"Expected singleton array at " + this + ", got " + array.size() + " elements" );
			}
			value = array.get( 0 );
		}
		return value;
	}

	@Override
	protected void appendRuntimeRelativePath(StringBuilder path) {
		path.append( "(unwrapSingletonArrays)" );
	}

	@Override
	protected void appendStaticRelativePath(StringBuilder path, boolean first) {
	}

	private static final class SelectedLocation implements JsonCompositeAccessor<JsonElement> {
		private final JsonAccessor<JsonElement> selected;

		private SelectedLocation(JsonAccessor<JsonElement> selected) {
			this.selected = selected;
		}

		@Override
		public Optional<JsonElement> get(JsonObject root) {
			return selected.get( root );
		}

		@Override
		public void set(JsonObject root, JsonElement value) {
			selected.set( root, value );
		}

		@Override
		public JsonElement getOrCreate(JsonObject root, Supplier<? extends JsonElement> supplier) {
			return selected.getOrCreate( root, supplier );
		}

		@Override
		public JsonElement getOrCreate(JsonObject root) {
			throw new UnsupportedOperationException();
		}

		@Override
		public String toString() {
			return selected.toString();
		}
	}
}
