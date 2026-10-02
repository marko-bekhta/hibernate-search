package org.hibernate.search.backend.elasticsearch.gson.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.Stream;

import org.hibernate.search.backend.elasticsearch.lowlevel.index.aliases.impl.IndexAliasDefinition;
import org.hibernate.search.backend.elasticsearch.lowlevel.index.analysis.impl.AnalyzerDefinition;
import org.hibernate.search.backend.elasticsearch.lowlevel.index.analysis.impl.CharFilterDefinition;
import org.hibernate.search.backend.elasticsearch.lowlevel.index.analysis.impl.NormalizerDefinition;
import org.hibernate.search.backend.elasticsearch.lowlevel.index.analysis.impl.TokenFilterDefinition;
import org.hibernate.search.backend.elasticsearch.lowlevel.index.analysis.impl.TokenizerDefinition;
import org.hibernate.search.backend.elasticsearch.lowlevel.index.mapping.impl.DynamicTemplate;
import org.hibernate.search.backend.elasticsearch.lowlevel.index.mapping.impl.DynamicType;
import org.hibernate.search.backend.elasticsearch.lowlevel.index.mapping.impl.ElasticsearchDenseVectorIndexOptions;
import org.hibernate.search.backend.elasticsearch.lowlevel.index.mapping.impl.NamedDynamicTemplate;
import org.hibernate.search.backend.elasticsearch.lowlevel.index.mapping.impl.OpenSearchVectorTypeMethod;
import org.hibernate.search.backend.elasticsearch.lowlevel.index.mapping.impl.PropertyMapping;
import org.hibernate.search.backend.elasticsearch.lowlevel.index.mapping.impl.RootTypeMapping;
import org.hibernate.search.backend.elasticsearch.lowlevel.index.mapping.impl.RoutingType;
import org.hibernate.search.backend.elasticsearch.lowlevel.index.settings.impl.Analysis;
import org.hibernate.search.backend.elasticsearch.lowlevel.index.settings.impl.IndexSettings;
import org.hibernate.search.util.common.AssertionFailure;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.google.gson.ReflectionAccessFilter;

class GsonCodecCharacterizationTest {

	private final Gson gson = GsonProviderHelper.create(
			() -> new GsonBuilder().addReflectionAccessFilter( type -> ReflectionAccessFilter.FilterResult.BLOCK_ALL ), false )
			.getGsonNoSerializeNulls();

	static Stream<Arguments> codecs() {
		return Stream.of(
				Arguments.of( IndexAliasDefinition.class, "{\"is_write_index\":true,\"filter\":{\"term\":{\"tenant\":1}}}" ),
				Arguments.of( AnalyzerDefinition.class,
						"{\"type\":\"custom\",\"tokenizer\":\"standard\",\"filter\":[\"lowercase\"],\"custom\":1}" ),
				Arguments.of( CharFilterDefinition.class, "{\"type\":\"mapping\",\"mappings\":[\"a => b\"]}" ),
				Arguments.of( NormalizerDefinition.class, "{\"type\":\"custom\",\"filter\":[\"lowercase\"]}" ),
				Arguments.of( TokenFilterDefinition.class, "{\"type\":\"stop\",\"stopwords\":[\"a\",\"the\"]}" ),
				Arguments.of( TokenizerDefinition.class, "{\"type\":\"ngram\",\"min_gram\":2}" ),
				Arguments.of( Analysis.class,
						"{\"analyzer\":{\"a\":{\"type\":\"custom\",\"tokenizer\":\"standard\"}},\"custom\":true}" ),
				Arguments.of( IndexSettings.class,
						"{\"max_result_window\":20000,\"knn\":true,\"number_of_shards\":2,\"analysis\":{\"filter\":{\"f\":{\"type\":\"stop\"}}}}" ),
				Arguments.of( DynamicTemplate.class,
						"{\"match\":\"*_text\",\"match_mapping_type\":\"string\",\"mapping\":{\"type\":\"text\"}}" ),
				Arguments.of( ElasticsearchDenseVectorIndexOptions.class,
						"{\"type\":\"hnsw\",\"m\":16,\"ef_construction\":100,\"custom\":true}" ),
				Arguments.of( OpenSearchVectorTypeMethod.class,
						"{\"name\":\"hnsw\",\"engine\":\"lucene\",\"space_type\":\"l2\",\"parameters\":{\"ef_construction\":100,\"m\":16,\"custom\":1}}" ),
				Arguments.of( OpenSearchVectorTypeMethod.Parameters.class, "{\"ef_construction\":100,\"m\":16,\"custom\":1}" ),
				Arguments.of( PropertyMapping.class,
						"{\"type\":\"date\",\"format\":\"strict_date_optional_time||epoch_millis\",\"null_value\":9007199254740993,\"doc_values\":false,\"custom\":{\"nested\":3}}" ),
				Arguments.of( RootTypeMapping.class,
						"{\"dynamic\":\"strict\",\"_routing\":{\"required\":true},\"properties\":{\"name\":{\"type\":\"keyword\"}},\"dynamic_templates\":[{\"strings\":{\"match_mapping_type\":\"string\",\"mapping\":{\"type\":\"keyword\"}}}]}" ),
				Arguments.of( NamedDynamicTemplate.class,
						"{\"strings\":{\"match\":\"*\",\"mapping\":{\"type\":\"keyword\"}}}" ),
				Arguments.of( DynamicType.class, "\"true\"" ),
				Arguments.of( DynamicType.class, "\"false\"" ),
				Arguments.of( DynamicType.class, "\"strict\"" ),
				Arguments.of( RoutingType.class, "{\"required\":true}" )
		);
	}

	@ParameterizedTest(name = "{0}: {1}")
	@MethodSource("codecs")
	void roundTrip(Class<?> type, String json) {
		assertThat( gson.getAdapter( type ).getClass().getName() ).doesNotContain( "ReflectiveTypeAdapter" );
		Object value = gson.fromJson( json, type );
		assertThat( gson.toJsonTree( value, type ) ).isEqualTo( JsonParser.parseString( json ) );
		assertThat( gson.fromJson( "null", type ) ).isNull();
		assertThat( gson.toJson( null, type ) ).isEqualTo( "null" );
	}

	@Test
	void handwrittenRepresentationsAndFailures() {
		// Optional routing reads successfully, but its adapter cannot write OPTIONAL.
		assertThat( gson.fromJson( "{\"required\":false}", RoutingType.class ) ).isEqualTo( RoutingType.OPTIONAL );
		assertThatThrownBy( () -> gson.toJson( RoutingType.OPTIONAL ) ).isInstanceOf( AssertionFailure.class );
		assertThat( gson.fromJson( "{}", RoutingType.class ) ).isNull();
		assertThatThrownBy( () -> gson.fromJson( "{\"unknown\":true}", RoutingType.class ) )
				.isInstanceOf( AssertionFailure.class );
		assertThatThrownBy( () -> gson.fromJson( "true", DynamicType.class ) ).isInstanceOf( RuntimeException.class );
		assertThatThrownBy( () -> gson.fromJson( "\"unknown\"", DynamicType.class ) ).isInstanceOf( AssertionFailure.class );
		assertThatThrownBy( () -> gson.fromJson( "{}", NamedDynamicTemplate.class ) ).isInstanceOf( RuntimeException.class );
		assertThatThrownBy( () -> gson.fromJson( "{\"a\":{},\"b\":{}}", NamedDynamicTemplate.class ) )
				.isInstanceOf( RuntimeException.class );
	}

	@Test
	void formatAndNullSerialization() {
		PropertyMapping mapping =
				gson.fromJson( "{\"type\":\"date\",\"format\":\"a||b\",\"unknown\":null}", PropertyMapping.class );
		assertThat( mapping.getFormat() ).containsExactly( "a", "b" );
		assertThat( gson.toJsonTree( mapping ).getAsJsonObject().has( "unknown" ) ).isFalse();
		Gson withNulls = GsonProviderHelper.create( GsonBuilder::new, false ).getGson();
		assertThat( withNulls.toJsonTree( mapping ).getAsJsonObject().get( "unknown" ).isJsonNull() ).isTrue();
		assertThat( withNulls.toJsonTree( mapping ).getAsJsonObject().get( "index" ).isJsonNull() ).isTrue();
		assertThat( gson.fromJson( "{\"format\":null}", PropertyMapping.class ).getFormat() ).isNull();
		assertThat( gson.fromJson( "{\"format\":\"a||b||\"}", PropertyMapping.class ).getFormat() )
				.containsExactly( "a", "b" );
		assertThatThrownBy( () -> gson.fromJson( "{\"format\":[\"a\",\"b\"]}", PropertyMapping.class ) )
				.isInstanceOf( RuntimeException.class );
	}

	@Test
	void generatedMemberOrderAndExtraPropertyOrder() {
		IndexSettings settings =
				gson.fromJson( "{\"z\":1,\"max_result_window\":20,\"a\":2,\"knn\":true}", IndexSettings.class );
		assertThat( gson.toJsonTree( settings ).getAsJsonObject().keySet() )
				.containsExactly( "knn", "max_result_window", "z", "a" );
	}

	@Test
	void codecsPreserveLargeNumbersAndExtraNumberLexemes() {
		PropertyMapping mapping = gson.fromJson( "{\"null_value\":9007199254740993}", PropertyMapping.class );
		assertThat( mapping.getNullValue().getAsLong() ).isEqualTo( 9007199254740993L );
		assertThat( gson.toJsonTree( mapping ).getAsJsonObject().get( "null_value" ).toString() )
				.isEqualTo( "9007199254740993" );
		IndexSettings settings = gson.fromJson( "{\"extra\":1.2300e+04}", IndexSettings.class );
		assertThat( gson.toJsonTree( settings ).getAsJsonObject().get( "extra" ).toString() )
				.isEqualTo( "1.2300e+04" );
	}
}
