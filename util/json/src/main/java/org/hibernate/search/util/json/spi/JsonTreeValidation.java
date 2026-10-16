package org.hibernate.search.util.json.spi;

import org.hibernate.search.util.common.annotation.Incubating;
import org.hibernate.search.util.json.impl.JsonTreeValidator;

import tools.jackson.databind.JsonNode;

/** Validation of stable external ordinary JSON trees; it never serializes, copies or maps Java models. */
@Incubating
public final class JsonTreeValidation {
	/** Opt-in internal invariant checks; production defaults to false. Read once per JVM. */
	public static final String INTERNAL_VALIDATION_PROPERTY = "hibernate.search.json.internal_validation";
	private static final boolean INTERNAL_VALIDATION = Boolean.getBoolean( INTERNAL_VALIDATION_PROPERTY );

	public static boolean isInternalValidationEnabled() {
		return INTERNAL_VALIDATION;
	}

	/** Only for stable trees built/parsed internally or already validated at their external boundary. */
	public static void validateInternal(JsonNode root) {
		if ( INTERNAL_VALIDATION ) {
			JsonTreeValidator.validate( root );
		}
	}

	private JsonTreeValidation() {
	}

	/**
	 * Accept standard acyclic JSON nodes, including shared acyclic subtrees. Reject missing, POJO, raw,
	 * binary, custom/subclassed and nonfinite nodes. Numeric ranges match the parser policy.
	 * Text-input byte/token constraints do not limit an already constructed exact numeric value.
	 */
	public static void validateExternal(JsonNode root) {
		JsonTreeValidator.validate( root );
	}
}
