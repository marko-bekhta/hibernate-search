package org.hibernate.search.util.json.spi;

import tools.jackson.databind.JsonNode;

/** Validation of stable external ordinary JSON trees; it never serializes, copies or maps Java models. */
public final class JsonTreeValidation {
	private JsonTreeValidation() {
	}

	/**
	 * Accept standard acyclic JSON nodes, including shared acyclic subtrees. Reject missing, POJO, raw,
	 * binary, custom/subclassed and nonfinite nodes. Numeric ranges match the parser policy.
	 * Text-input byte/token constraints do not limit an already constructed exact numeric value.
	 */
	public static void validateExternal(JsonNode root) {
		org.hibernate.search.util.json.impl.JsonTreeValidator.validate( root );
	}
}
