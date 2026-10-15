package org.hibernate.search.util.impl.test.jar;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.BiPredicate;

import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.Indexer;

/** Inspects compiled contracts, including CLASS-retained annotations, without loading implementation classes. */
public final class PublicApiDependencyTestUtils {
	private PublicApiDependencyTestUtils() {
	}

	public static void assertNoDependencies(Path classes, List<String> forbiddenPrefixes) throws IOException {
		assertNoDependencies( classes, forbiddenPrefixes, (type, prefix) -> false );
	}

	/** Explicit class/prefix exceptions do not exempt a class from other dependency checks. */
	public static void assertNoDependencies(Path classes, List<String> forbiddenPrefixes,
			BiPredicate<String, String> allowedDependency)
			throws IOException {
		assertThat( classes ).isDirectory();
		int inspected = 0;
		try ( var files = Files.walk( classes ) ) {
			for ( Path file : files.filter( p -> p.toString().endsWith( ".class" ) ).toList() ) {
				String name = classes.relativize( file ).toString().replace( '/', '.' ).replace( '\\', '.' );
				if ( name.contains( ".impl." ) || "module-info.class".equals( name ) ) {
					continue;
				}
				try ( InputStream input = Files.newInputStream( file ) ) {
					Indexer indexer = new Indexer();
					indexer.index( input );
					ClassInfo type = indexer.complete().getKnownClasses().iterator().next();
					if ( visible( type.flags() ) || name.endsWith( "package-info.class" ) ) {
						assertNoDependencies( type, forbiddenPrefixes, allowedDependency );
						++inspected;
					}
				}
			}
		}
		assertThat( inspected ).as( "Published contracts in %s", classes ).isPositive();
	}

	public static void assertNoDependencies(ClassInfo type, List<String> forbiddenPrefixes) {
		assertNoDependencies( type, forbiddenPrefixes, (name, prefix) -> false );
	}

	public static void assertNoDependencies(ClassInfo type, List<String> forbiddenPrefixes,
			BiPredicate<String, String> allowedDependency) {
		forbiddenPrefixes = forbiddenPrefixes.stream()
				.filter( prefix -> !allowedDependency.test( type.name().toString(), prefix ) ).toList();
		String context = type.name().toString();
		check( context, type.genericSignature( null ), forbiddenPrefixes );
		check( context, type.descriptor( null ), forbiddenPrefixes );
		for ( var field : type.fields() ) {
			if ( visible( field.flags() ) ) {
				check( context, field.genericSignature( null ), forbiddenPrefixes );
				check( context, field.type().toString(), forbiddenPrefixes );
			}
		}
		for ( var method : type.methods() ) {
			if ( visible( method.flags() ) ) {
				check( context, method.genericSignature( null ), forbiddenPrefixes );
				check( context, method.descriptor( null ), forbiddenPrefixes );
				for ( var exception : method.exceptions() ) {
					check( context, exception.toString(), forbiddenPrefixes );
				}
				if ( method.defaultValue() != null ) {
					check( context, method.defaultValue().toString(), forbiddenPrefixes );
				}
			}
		}
		for ( var annotation : type.annotations() ) {
			if ( exposed( annotation.target() ) ) {
				check( context, annotation.toString(), forbiddenPrefixes );
			}
		}
	}

	private static boolean exposed(AnnotationTarget target) {
		return switch ( target.kind() ) {
			case CLASS, RECORD_COMPONENT -> true;
			case FIELD -> visible( target.asField().flags() );
			case METHOD -> visible( target.asMethod().flags() );
			case METHOD_PARAMETER -> visible( target.asMethodParameter().method().flags() );
			case TYPE -> exposed( target.asType().enclosingTarget() );
		};
	}

	private static boolean visible(int flags) {
		return Modifier.isPublic( flags ) || Modifier.isProtected( flags );
	}

	private static void check(String context, String signature, List<String> forbiddenPrefixes) {
		if ( signature != null ) {
			assertThat( signature.replace( '/', '.' ) ).as( context )
					.doesNotContain( forbiddenPrefixes.toArray( String[]::new ) );
		}
	}
}
