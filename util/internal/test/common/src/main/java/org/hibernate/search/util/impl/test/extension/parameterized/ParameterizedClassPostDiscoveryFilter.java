/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.util.impl.test.extension.parameterized;

import static org.junit.platform.commons.util.AnnotationUtils.isAnnotated;

import org.junit.jupiter.engine.descriptor.MethodBasedTestDescriptor;
import org.junit.platform.engine.FilterResult;
import org.junit.platform.engine.TestDescriptor;

/**
 * Launcher post discovery filter that will not include tests annotated with {@link org.junit.jupiter.api.Test} or {@link org.junit.jupiter.params.ParameterizedTest}
 * in a regular test run if the class they are located in is a {@link ParameterizedClass} type of test.
 * These tests will be discovered by the extension itself, and it'll handle their execution.
 */
public class ParameterizedClassPostDiscoveryFilter implements org.junit.platform.launcher.PostDiscoveryFilter {
	@Override
	public FilterResult apply(TestDescriptor testDescriptor) {
		if ( testDescriptor instanceof MethodBasedTestDescriptor ) {
			MethodBasedTestDescriptor methodTestDescriptor = (MethodBasedTestDescriptor) testDescriptor;
			if ( isParameterizedClass( methodTestDescriptor.getTestClass() )
					&& !ParameterizedClassUtils.isParameterizedSetup( methodTestDescriptor.getTestMethod() ) ) {
				return FilterResult
						.excluded( "It is a test method that ParameterizedClassExtension will discover and execute itself." );
			}

		}
		return FilterResult.included( "This filter does not care about this case." );
	}

	private static boolean isParameterizedClass(Class<?> testClass) {
		return isAnnotated( testClass, ParameterizedClass.class );
	}
}
