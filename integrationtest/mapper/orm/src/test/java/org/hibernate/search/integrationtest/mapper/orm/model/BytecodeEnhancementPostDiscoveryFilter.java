/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.integrationtest.mapper.orm.model;

import static org.hibernate.search.integrationtest.mapper.orm.model.BytecodeEnhancementExtension.getEnhancerClassLoader;
import static org.junit.platform.commons.util.AnnotationUtils.isAnnotated;

import java.lang.reflect.Method;

import org.hibernate.testing.bytecode.enhancement.EnhancerTestContext;

import org.junit.jupiter.engine.config.JupiterConfiguration;
import org.junit.jupiter.engine.descriptor.ClassBasedTestDescriptor;
import org.junit.jupiter.engine.descriptor.ClassTestDescriptor;
import org.junit.jupiter.engine.descriptor.JupiterEngineDescriptor;
import org.junit.jupiter.engine.descriptor.TestMethodTestDescriptor;
import org.junit.platform.engine.FilterResult;
import org.junit.platform.engine.TestDescriptor;

public class BytecodeEnhancementPostDiscoveryFilter implements org.junit.platform.launcher.PostDiscoveryFilter {
	@Override
	public FilterResult apply(TestDescriptor testDescriptor) {
		if ( testDescriptor instanceof ClassBasedTestDescriptor ) {
			try {
				ClassBasedTestDescriptor descriptor = (ClassBasedTestDescriptor) testDescriptor;
				// if the test class is annotated with @BytecodeEnhanced
				// we replace the descriptor with the new one that will point to an enhanced test class,
				// this also means that we need to add all the child descriptors back as well...
				// Then in the extension we set the classloader that contains the enhanced test class and set it to the original once the test class is destroyed
				if ( isAnnotated( descriptor.getTestClass(), BytecodeEnhancementExtension.BytecodeEnhanced.class ) ) {
					TestDescriptor parent = descriptor.getParent().get();
					Class<?> klass = descriptor.getTestClass();
					String packageName = klass.getPackage().getName();

					JupiterConfiguration jc = ( (JupiterEngineDescriptor) parent ).getConfiguration();
					ClassTestDescriptor updated = new ClassTestDescriptor(
							descriptor.getUniqueId(),
							getEnhancerClassLoader( new EnhancerTestContext(), packageName ).loadClass(
									klass.getName() ),
							jc
					);

					for ( TestDescriptor child : descriptor.getChildren() ) {
						// this needs more cases for parameterized tests, test templates and so on ...
						// for now it'll only work with simple @Test tests
						if ( child instanceof TestMethodTestDescriptor ) {
							Method testMethod = ( (TestMethodTestDescriptor) child ).getTestMethod();
							updated.addChild(
									new TestMethodTestDescriptor(
											child.getUniqueId(),
											updated.getTestClass(),
											updated.getTestClass().getMethod(
													testMethod.getName(),
													testMethod.getParameterTypes()
											),
											jc
									)
							);

						}
					}

					descriptor.removeFromHierarchy();
					parent.addChild( updated );
				}
			}
			catch (Exception e) {
				throw new RuntimeException( e );
			}
		}
		return FilterResult.included( "This filter does not care about this case." );
	}
}
