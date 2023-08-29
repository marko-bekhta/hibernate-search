/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.util.impl.test.extension.parameterized;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import org.junit.jupiter.api.TestInstance;

/**
 * A marker annotation to let JUnit engine know how to treat the test class annotated with it.
 * <p>
 * Tests that are expected to run as parameterized classes must have a setup method annotated with {@link ParameterizedSetup}.
 */
@Retention(RetentionPolicy.RUNTIME)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public @interface ParameterizedClass {
}
