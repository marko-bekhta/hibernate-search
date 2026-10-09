package org.hibernate.search.backend.elasticsearch.json.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

class JsonCodecBoundaryTest {
	private static final Set<String> GSON_INPUT_COMPATIBILITY = Set.of(
			"gson/GsonJsonConversion.java",
			"search/predicate/dsl/ElasticsearchSearchPredicateFactory.java",
			"search/sort/dsl/ElasticsearchSearchSortFactory.java",
			"search/aggregation/dsl/ElasticsearchSearchAggregationFactory.java",
			"types/dsl/ElasticsearchNativeIndexFieldTypeMappingStep.java"
	);

	private static final List<String> PRODUCTION_MODULES = List.of(
			"backend/elasticsearch", "backend/elasticsearch-aws",
			"backend/elasticsearch-client/elasticsearch-rest4-client",
			"backend/elasticsearch-client/elasticsearch-rest5-client",
			"backend/elasticsearch-client/opensearch-rest-client", "util/json", "build/json-processor" );
	private static final Pattern GSON_REFERENCE = Pattern.compile( "com\\s*\\.\\s*google\\s*\\.\\s*gson|GsonJsonConversion" );
	private static final Pattern REFLECTIVE_MAPPING = Pattern.compile(
			"java\\s*\\.\\s*lang\\s*\\.\\s*reflect\\s*\\.\\s*(Field|Method|Constructor)\\b"
					+ "|getDeclared(Field|Method|Constructor)" );

	@Test
	void productionModulesUseSearchTreesAndNoReflectiveFallback() throws Exception {
		Path repository = Path.of( "../.." ).toAbsolutePath().normalize();
		for ( String module : PRODUCTION_MODULES ) {
			Path root = repository.resolve( module + "/src/main/java" );
			assertThat( root ).isDirectory();
			try ( var files = Files.walk( root ) ) {
				for ( Path file : files.filter( p -> p.toString().endsWith( ".java" ) ).toList() ) {
					String source = Files.readString( file );
					String relative = root.relativize( file ).toString().replace( '\\', '/' );
					String prefix = "org/hibernate/search/backend/elasticsearch/";
					boolean compatibility = "backend/elasticsearch".equals( module )
							&& relative.startsWith( prefix )
							&& GSON_INPUT_COMPATIBILITY.contains( relative.substring( prefix.length() ) );
					assertOrdinarySource( file.toString(), source, compatibility );
					if ( compatibility ) {
						assertThat( source ).as( file.toString() ).contains( "@" + Deprecated.class.getSimpleName(),
								"future breaking release" );
					}
				}
			}
		}
	}

	@Test
	void sourcePolicyRejectsLeaksAndDoesNotAllowAnEntireCompatibilityPackage() {
		for ( String source : List.of( "import com.google.gson.JsonObject;", "com . google . gson.JsonElement field;",
				"GsonJsonConversion.fromGson(value)", "import java.lang.reflect.Field;", "type.getDeclaredMethods()",
				"import tools.jackson.databind.ObjectMapper;" ) ) {
			assertThatThrownBy( () -> assertOrdinarySource( "new-production-file.java", source, false ) )
					.isInstanceOf( AssertionError.class );
		}
		assertThat( GSON_INPUT_COMPATIBILITY ).hasSize( 5 ).doesNotContain( "gson/NewMapper.java" );
		assertOrdinarySource( "compatibility.java", "com.google.gson.JsonObject", true );
		assertThatThrownBy( () -> assertOrdinarySource( "compatibility.java", "type.getDeclaredFields()", true ) )
				.isInstanceOf( AssertionError.class );
	}

	private static void assertOrdinarySource(String path, String source, boolean compatibility) {
		if ( !compatibility ) {
			assertThat( GSON_REFERENCE.matcher( source ).find() ).as( path + ": Gson reference" ).isFalse();
		}
		assertThat( REFLECTIVE_MAPPING.matcher( source ).find() ).as( path + ": reflective mapper" ).isFalse();
		assertThat( source ).as( path ).doesNotContain( "jackson.databind" );
	}

	@Test
	void generatedModelCodecsStayTreeBasedAndLibraryNeutral() throws Exception {
		Path root = Path.of( "target/generated-sources/annotations" );
		try ( var files = Files.walk( root ) ) {
			var codecs = files.filter( p -> p.getFileName().toString().endsWith( "JsonCodec.java" ) ).toList();
			assertThat( codecs ).hasSize( 15 );
			for ( Path file : codecs ) {
				assertThat( Files.readString( file ) ).as( file.toString() )
						.doesNotContain( "com.google.gson", "jackson", "java.lang.reflect", "toString()", "parse(" );
			}
		}
	}
}
