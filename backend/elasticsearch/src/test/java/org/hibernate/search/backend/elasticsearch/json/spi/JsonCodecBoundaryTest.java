package org.hibernate.search.backend.elasticsearch.json.spi;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import org.junit.jupiter.api.Test;

class JsonCodecBoundaryTest {
	private static final Set<String> GSON_INPUT_COMPATIBILITY = Set.of(
			"gson/GsonJsonConversion.java",
			"search/predicate/dsl/ElasticsearchSearchPredicateFactory.java",
			"search/sort/dsl/ElasticsearchSearchSortFactory.java",
			"search/aggregation/dsl/ElasticsearchSearchAggregationFactory.java",
			"types/dsl/ElasticsearchNativeIndexFieldTypeMappingStep.java"
	);

	@Test
	void ordinaryBackendSourcesUseSearchTreesAndNoReflectiveFallback() throws Exception {
		Path root = Path.of( "src/main/java/org/hibernate/search/backend/elasticsearch" );
		try ( var files = Files.walk( root ) ) {
			for ( Path file : files.filter( p -> p.toString().endsWith( ".java" ) ).toList() ) {
				String source = Files.readString( file );
				if ( !GSON_INPUT_COMPATIBILITY.contains( root.relativize( file ).toString() ) ) {
					assertThat( source ).as( file.toString() ).doesNotContain( "com.google.gson", "GsonJsonConversion" );
				}
				assertThat( source ).as( file.toString() ).doesNotContain( "java.lang.reflect.Field",
						"java.lang.reflect.Method", "getDeclaredField", "getDeclaredMethod",
						"jackson.databind" );
			}
		}
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
