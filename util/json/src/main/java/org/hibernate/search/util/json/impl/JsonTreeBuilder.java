package org.hibernate.search.util.json.impl;

import java.util.ArrayDeque;

import org.hibernate.search.util.json.JsonArray;
import org.hibernate.search.util.json.JsonElement;
import org.hibernate.search.util.json.JsonObject;
import org.hibernate.search.util.json.spi.JsonEmptyInputException;
import org.hibernate.search.util.json.spi.JsonParseException;

/** Stack-based tree construction; never uses accessor chains. */
final class JsonTreeBuilder {
	private final ArrayDeque<Frame> frames = new ArrayDeque<>();
	private JsonElement root;

	void add(JsonElement value) {
		if ( frames.isEmpty() ) {
			if ( root != null ) {
				throw new JsonParseException( "Expected one JSON root; found trailing content" );
			}
			root = value;
		}
		else {
			Frame frame = frames.peek();
			if ( frame.container.isJsonArray() ) {
				frame.container.getAsJsonArray().add( value );
			}
			else {
				if ( frame.name == null ) {
					throw new JsonParseException( "Missing JSON member name" );
				}
				frame.container.getAsJsonObject().add( frame.name, value );
				frame.name = null;
			}
		}
	}

	void startObject() {
		JsonObject object = new JsonObject();
		add( object );
		frames.push( new Frame( object ) );
	}

	void startArray() {
		JsonArray array = new JsonArray();
		add( array );
		frames.push( new Frame( array ) );
	}

	void name(String name) {
		frames.peek().name = name;
	}

	void endContainer() {
		frames.pop();
	}

	JsonElement finish() {
		if ( root == null ) {
			throw new JsonEmptyInputException();
		}
		if ( !frames.isEmpty() ) {
			throw new JsonParseException( "Truncated JSON input" );
		}
		return root;
	}

	void clear() {
		root = null;
		frames.clear();
	}

	private static final class Frame {
		private final JsonElement container;
		private String name;

		private Frame(JsonElement container) {
			this.container = container;
		}
	}
}
