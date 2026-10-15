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
import java.util.Set;
import java.util.function.Supplier;

import org.hibernate.search.util.impl.test.jar.PublicApiDependencyTestUtils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import org.jboss.jandex.Indexer;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.JsonNode;

class JsonApiBoundaryTest {
	@Test
	void onlyApprovedNativeSpiSignaturesExposeJackson() throws Exception {
		PublicApiDependencyTestUtils.assertNoDependencies( Path.of( "target/classes" ),
				List.of( "com.google.gson", "com.fasterxml.jackson", "tools.jackson",
						"org.hibernate.search.util.json.impl", "org.hibernate.search.backend" ),
				(type, prefix) -> "tools.jackson".equals( prefix )
						&& Set.of(
								"JsonCodec", "JsonCodecs", "JsonParserSession", "JsonParsing", "JsonBodies", "JsonNumbers",
								"JsonTreeValidation" ).stream()
								.anyMatch( name -> type.equals( "org.hibernate.search.util.json.spi." + name )
										|| type.startsWith( "org.hibernate.search.util.json.spi." + name + "$" ) ) );
	}

	@Test
	void productionHasNoGsonOrReflectiveDtoMapping() throws Exception {
		try ( var files = Files.walk( Path.of( "src/main/java" ) ) ) {
			for ( Path file : files.filter( path -> path.toString().endsWith( ".java" ) ).toList() ) {
				assertThat( Files.readString( file ) ).as( file.toString() )
						.doesNotContain( "com.google.gson", "com.fasterxml.jackson.databind", "ObjectMapper", "JsonMapper",
								"java.lang.reflect",
								"getDeclaredField", "getDeclaredMethod", "org.hibernate.search.backend" );
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

	@Test
	void explicitExceptionDoesNotDisableOtherPrefixes() throws Exception {
		try ( var input = GenericReturn.class.getResourceAsStream(
				"/" + GenericReturn.class.getName().replace( '.', '/' ) + ".class" ) ) {
			Indexer indexer = new Indexer();
			indexer.index( input );
			var type = indexer.complete().getKnownClasses().iterator().next();
			assertThatThrownBy( () -> PublicApiDependencyTestUtils.assertNoDependencies( type,
					List.of( "tools.jackson", "java.util" ),
					(name, prefix) -> name.equals( GenericReturn.class.getName() ) && prefix.equals( "tools.jackson" ) ) )
					.isInstanceOf( AssertionError.class ).hasMessageContaining( "java.util" );
		}
	}

	static List<Class<?>> leakingContracts() {
		return List.of( GenericReturn.class, GenericInterface.class, GenericBound.class, Superclass.class,
				Field.class, Constructor.class, Throws.class, Annotated.class, TypeAnnotated.class, AnnotationDefault.class,
				HandlerWithNativeBound.class );
	}

	public interface HandlerWithNativeBound<T extends JsonNode> extends JsonHandler<T> {}

	public interface GenericReturn { List<JsonParser> parse(); }

	public interface GenericInterface extends Supplier<JsonParser> {}

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
