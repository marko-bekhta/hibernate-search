/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.integrationtest.mapper.orm.model;

import static org.hibernate.bytecode.internal.BytecodeProviderInitiator.buildDefaultBytecodeProvider;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Collections;
import java.util.List;

import org.hibernate.bytecode.enhance.spi.EnhancementContext;
import org.hibernate.bytecode.enhance.spi.Enhancer;

import org.hibernate.testing.bytecode.enhancement.EnhancementSelector;
import org.hibernate.testing.bytecode.enhancement.PackageSelector;

import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestInstanceFactoryContext;
import org.junit.jupiter.api.extension.TestInstancePreConstructCallback;
import org.junit.jupiter.api.extension.TestInstancePreDestroyCallback;

public class BytecodeEnhancementExtension implements TestInstancePreConstructCallback, TestInstancePreDestroyCallback {

	@Retention(RetentionPolicy.RUNTIME)
	@ExtendWith(BytecodeEnhancementExtension.class)
	@interface BytecodeEnhanced {

	}

	private ClassLoader originalClassLoader;

	@Override
	public void preConstructTestInstance(TestInstanceFactoryContext testInstanceFactoryContext,
			ExtensionContext extensionContext)
			throws Exception {
		originalClassLoader = Thread.currentThread().getContextClassLoader();
		Thread.currentThread().setContextClassLoader( testInstanceFactoryContext.getTestClass().getClassLoader() );
	}

	@Override
	public void preDestroyTestInstance(ExtensionContext extensionContext) throws Exception {
		Thread.currentThread().setContextClassLoader( originalClassLoader );
	}

	public static ClassLoader getEnhancerClassLoader(EnhancementContext context, String packageName) {
		return buildEnhancerClassLoader( context, Collections.singletonList( new PackageSelector( packageName ) ) );
	}

	private static ClassLoader buildEnhancerClassLoader(
			EnhancementContext enhancerContext,
			List<EnhancementSelector> selectors) {
		return new EnhancingClassLoader(
				buildDefaultBytecodeProvider().getEnhancer( enhancerContext ),
				selectors
		);
	}

	private static class EnhancingClassLoader extends ClassLoader {
		private static final String debugOutputDir = System.getProperty( "java.io.tmpdir" );

		private final Enhancer enhancer;
		private final List<EnhancementSelector> selectors;

		public EnhancingClassLoader(Enhancer enhancer, List<EnhancementSelector> selectors) {
			this.enhancer = enhancer;
			this.selectors = selectors;
		}

		public Class<?> loadClass(String name) throws ClassNotFoundException {
			for ( EnhancementSelector selector : selectors ) {
				if ( selector.select( name ) ) {
					final Class c = findLoadedClass( name );
					if ( c != null ) {
						return c;
					}

					try ( InputStream is = getResourceAsStream( name.replace( '.', '/' ) + ".class" ) ) {
						if ( is == null ) {
							throw new ClassNotFoundException( name + " not found" );
						}

						byte[] original = new byte[is.available()];
						try ( BufferedInputStream bis = new BufferedInputStream( is ) ) {
							bis.read( original );
						}

						byte[] enhanced = enhancer.enhance( name, original );
						if ( enhanced == null ) {
							return defineClass( name, original, 0, original.length );
						}

						File f = new File(
								debugOutputDir + File.separator + name.replace( ".", File.separator ) + ".class" );
						f.getParentFile().mkdirs();
						f.createNewFile();
						try ( FileOutputStream out = new FileOutputStream( f ) ) {
							out.write( enhanced );
						}
						return defineClass( name, enhanced, 0, enhanced.length );
					}
					catch (Throwable t) {
						throw new ClassNotFoundException( name + " not found", t );
					}
				}
			}

			return getParent().loadClass( name );
		}
	}
}
