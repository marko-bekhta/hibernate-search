package org.hibernate.search.util.json.spi;

/** No JSON value was present at EOF. Transport boundaries may interpret this as an absent body. */
public final class JsonEmptyInputException extends JsonParseException {
	public JsonEmptyInputException() {
		super( "Expected one JSON root; input was empty" );
	}
}
