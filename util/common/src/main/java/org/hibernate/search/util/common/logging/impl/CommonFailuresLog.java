/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */

package org.hibernate.search.util.common.logging.impl;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Member;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.URL;

import org.hibernate.search.util.common.SearchException;
import org.hibernate.search.util.common.logging.CategorizedLogger;

import org.jboss.logging.BasicLogger;
import org.jboss.logging.Logger;
import org.jboss.logging.annotations.Cause;
import org.jboss.logging.annotations.FormatWith;
import org.jboss.logging.annotations.LogMessage;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageLogger;
import org.jboss.logging.annotations.ValidIdRange;
import org.jboss.logging.annotations.ValidIdRanges;

@CategorizedLogger(
		category = CommonFailuresLog.CATEGORY_NAME,
		description = """
				Logs information on various non-critical errors related to parsing the Jandex index
				or on low-level executor operations like canceling tasks etc.
				"""
)
@MessageLogger(projectCode = MessageConstants.PROJECT_CODE)
@ValidIdRanges({
		@ValidIdRange(min = MessageConstants.UTIL_ID_RANGE_MIN, max = MessageConstants.UTIL_ID_RANGE_MAX),
})
public interface CommonFailuresLog extends BasicLogger {

	String CATEGORY_NAME = "org.hibernate.search.common.failures";

	CommonFailuresLog INSTANCE = LoggerFactory.make( CommonFailuresLog.class, CATEGORY_NAME, MethodHandles.lookup() );

	// -----------------------------------
	// New messages from Search 6 onwards
	// -----------------------------------
	int ID_OFFSET = MessageConstants.UTIL_ID_RANGE_MIN;

	@Message(id = ID_OFFSET + 0,
			value = "'%1$s' must not be null.")
	IllegalArgumentException mustNotBeNull(String objectDescription);

	@Message(id = ID_OFFSET + 1,
			value = "'%1$s' must not be null or empty.")
	IllegalArgumentException collectionMustNotBeNullNorEmpty(String objectDescription);

	@Message(id = ID_OFFSET + 2,
			value = "'%1$s' must be positive or zero.")
	IllegalArgumentException mustBePositiveOrZero(String objectDescription);

	@Message(id = ID_OFFSET + 3,
			value = "'%1$s' must not be null or empty.")
	IllegalArgumentException stringMustNotBeNullNorEmpty(String objectDescription);

	@Message(id = ID_OFFSET + 4,
			value = "'%1$s' must not be null or empty.")
	IllegalArgumentException arrayMustNotBeNullNorEmpty(String objectDescription);

	@Message(id = ID_OFFSET + 5, value = "Exception while invoking '%1$s' on '%2$s': %3$s.")
	SearchException errorInvokingMember(Member member, String componentAsString,
			@Cause Throwable cause, String causeMessage);

	@Message(id = ID_OFFSET + 6,
			value = "Requested type argument %3$s to type %2$s"
					+ " in implementing type %1$s, but %2$s doesn't declare any type parameter.")
	IllegalArgumentException cannotRequestTypeParameterOfUnparameterizedType(@FormatWith(TypeFormatter.class) Type type,
			@FormatWith(ClassFormatter.class) Class<?> rawSuperType, int typeArgumentIndex);

	@Message(id = ID_OFFSET + 7,
			value = "Requested type argument %3$s to type %2$s"
					+ " in implementing type %1$s, but %2$s only declares %4$s type parameter(s).")
	IllegalArgumentException typeParameterIndexOutOfBound(@FormatWith(TypeFormatter.class) Type type,
			@FormatWith(ClassFormatter.class) Class<?> rawSuperType,
			int typeArgumentIndex, int typeParametersLength);

	@Message(id = ID_OFFSET + 8,
			value = "Requested type argument index %3$s to type %2$s"
					+ " in implementing type %1$s should be 0 or greater.")
	IllegalArgumentException invalidTypeParameterIndex(@FormatWith(TypeFormatter.class) Type type,
			@FormatWith(ClassFormatter.class) Class<?> rawSuperType, int typeArgumentIndex);

	@LogMessage(level = Logger.Level.INFO)
	@Message(id = ID_OFFSET + 9,
			value = "Unable to access the value of containing annotation '%1$s'."
					+ " Ignoring annotation.")
	void cannotAccessRepeateableContainingAnnotationValue(
			@FormatWith(ClassFormatter.class) Class<?> containingAnnotationType, @Cause Throwable e);

	@Message(id = ID_OFFSET + 10,
			value = "'%1$s' must be strictly positive.")
	IllegalArgumentException mustBeStrictlyPositive(String objectDescription);

	@Message(id = ID_OFFSET + 11,
			value = "'%1$s' must not contain any null element.")
	IllegalArgumentException collectionMustNotContainNullElement(String collectionDescription);

	@Message(id = ID_OFFSET + 12, value = "Exception while invoking '%1$s' with arguments %2$s: %3$s")
	SearchException errorInvokingStaticMember(Member member, String argumentsAsString, @Cause Throwable cause,
			String causeMessage);

	@Message(id = ID_OFFSET + 13, value = "Exception while accessing Jandex index for '%1$s': %2$s")
	SearchException errorAccessingJandexIndex(URL codeSourceLocation, String message, @Cause Throwable e);

	@Message(id = ID_OFFSET + 14, value = "Exception while building Jandex index for '%1$s': %2$s")
	SearchException errorBuildingJandexIndex(URL codeSourceLocation, String message, @Cause Throwable e);

	@Message(id = ID_OFFSET + 16, value = "Cannot open filesystem for code source at '%1$s': %2$s")
	IOException cannotOpenCodeSourceFileSystem(URL url, String causeMessage, @Cause Throwable e);

	@Message(id = ID_OFFSET + 17, value = "Cannot interpret '%1$s' as a local directory or JAR.")
	IOException cannotInterpretCodeSourceUrl(URL url);

	@Message(id = ID_OFFSET + 18,
			value = "Cannot open a ZIP filesystem for code source at '%1$s', because the URI points to content inside a nested JAR. "
					+ "Run your application on JDK13+ to get nested JAR support, "
					+ "or disable JAR scanning by setting a mapping configurer that calls .discoverAnnotatedTypesFromRootMappingAnnotations(false). "
					+ "See the reference documentation for information about mapping configurers.")
	SearchException cannotOpenNestedJar(URI uri, @Cause Throwable e);

}