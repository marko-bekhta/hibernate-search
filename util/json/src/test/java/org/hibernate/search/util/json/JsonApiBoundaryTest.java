package org.hibernate.search.util.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.hibernate.search.util.impl.test.jar.PublicApiDependencyTestUtils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import org.jboss.jandex.Indexer;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;

class JsonApiBoundaryTest {
	@Test
	void everyModelAndSpiSignatureIsLibraryNeutral() throws Exception {
		PublicApiDependencyTestUtils.assertNoDependencies( Path.of( "target/classes" ),
				List.of( "com.google.gson", "com.fasterxml.jackson", "tools.jackson",
						"org.hibernate.search.util.json.impl" ) );
	}

	@Test
	void productionHasNoGsonDatabindOrReflectiveMapping() throws Exception {
		try ( var files = Files.walk( Path.of( "src/main/java" ) ) ) {
			for ( Path file : files.filter( path -> path.toString().endsWith( ".java" ) ).toList() ) {
				assertThat( Files.readString( file ) ).as( file.toString() )
						.doesNotContain( "com.google.gson", "com.fasterxml.jackson.databind", "tools.jackson.databind",
								"java.lang.reflect",
								"getDeclaredField", "getDeclaredMethod" );
			}
		}
	}


	@ParameterizedTest
	@MethodSource("leakingContracts")
	void checkerRejectsRepresentativeCompiledLeaks(Class<?> leakingContract) throws Exception {
		try ( var input =
				leakingContract.getResourceAsStream( "/" + leakingContract.getName().replace( '.', '/' ) + ".class" ) ) {
			Indexer indexer = new Indexer();
			indexer.index( input );
			var type = indexer.complete().getKnownClasses().iterator().next();
			assertThatThrownBy( () -> PublicApiDependencyTestUtils.assertNoDependencies( type, List.of( "tools.jackson" ) ) )
					.isInstanceOf( AssertionError.class ).hasMessageContaining( "tools.jackson" );
		}
	}

	static List<Class<?>> leakingContracts() {
		return List.of( GenericReturn.class, GenericInterface.class, GenericBound.class, Superclass.class,
				Field.class, Constructor.class, Throws.class, Annotated.class, TypeAnnotated.class, AnnotationDefault.class );
	}

	public interface GenericReturn { List<JsonParser> parse(); }

	public interface GenericInterface extends java.util.function.Supplier<JsonParser> {}

	public interface GenericBound<T extends JsonParser> {}

	public abstract static class Superclass extends JsonParser {}

	public static class Field { protected JsonParser parser; }

	public static class Constructor {
		public Constructor(JsonParser parser) {
		}
	}

	public interface Throws { void parse() throws JacksonException; }

	@LibraryType(JsonParser.class)
	public interface Annotated {}

	public interface TypeAnnotated { List<@LibraryType(JsonParser.class) String> parse(); }

	public @interface AnnotationDefault {
		Class<?> value() default JsonParser.class;
	}

	@Retention(RetentionPolicy.CLASS)
	@Target({ ElementType.TYPE, ElementType.TYPE_USE })
	public @interface LibraryType {
		Class<?> value();
	}
}
