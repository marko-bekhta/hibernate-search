package org.hibernate.search.backend.elasticsearch.json.spi;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import org.hibernate.search.backend.elasticsearch.client.common.json.spi.JsonProvider;
import org.hibernate.search.backend.elasticsearch.lowlevel.index.mapping.impl.DynamicType;
import org.hibernate.search.backend.elasticsearch.lowlevel.index.mapping.impl.DynamicTypeJsonCodec;
import org.hibernate.search.backend.elasticsearch.lowlevel.index.mapping.impl.NamedDynamicTemplate;
import org.hibernate.search.backend.elasticsearch.lowlevel.index.mapping.impl.NamedDynamicTemplateJsonCodec;
import org.hibernate.search.backend.elasticsearch.lowlevel.index.mapping.impl.RoutingType;
import org.hibernate.search.backend.elasticsearch.lowlevel.index.mapping.impl.RoutingTypeJsonCodec;
import org.hibernate.search.util.json.spi.JsonCodec;
import org.hibernate.search.util.json.spi.JsonCodecRegistry;

/** Explicit registrations for the backend's known schema model types. */
public final class JsonProviderHelper {
	private static final JsonCodecRegistry REGISTRY = registry();

	private JsonProviderHelper() {
	}

	private static JsonCodecRegistry registry() {
		Map<Class<?>, Function<JsonCodecRegistry, JsonCodec<?>>> factories = new LinkedHashMap<>();
		factories.put( org.hibernate.search.backend.elasticsearch.lowlevel.index.aliases.impl.IndexAliasDefinition.class,
				r -> new org.hibernate.search.backend.elasticsearch.lowlevel.index.aliases.impl.GeneratedIndexAliasDefinitionJsonCodec(
						r ) );
		factories.put( org.hibernate.search.backend.elasticsearch.lowlevel.index.analysis.impl.AnalyzerDefinition.class,
				r -> new org.hibernate.search.backend.elasticsearch.lowlevel.index.analysis.impl.GeneratedAnalyzerDefinitionJsonCodec(
						r ) );
		factories.put( org.hibernate.search.backend.elasticsearch.lowlevel.index.analysis.impl.CharFilterDefinition.class,
				r -> new org.hibernate.search.backend.elasticsearch.lowlevel.index.analysis.impl.GeneratedCharFilterDefinitionJsonCodec(
						r ) );
		factories.put( org.hibernate.search.backend.elasticsearch.lowlevel.index.analysis.impl.NormalizerDefinition.class,
				r -> new org.hibernate.search.backend.elasticsearch.lowlevel.index.analysis.impl.GeneratedNormalizerDefinitionJsonCodec(
						r ) );
		factories.put( org.hibernate.search.backend.elasticsearch.lowlevel.index.analysis.impl.TokenFilterDefinition.class,
				r -> new org.hibernate.search.backend.elasticsearch.lowlevel.index.analysis.impl.GeneratedTokenFilterDefinitionJsonCodec(
						r ) );
		factories.put( org.hibernate.search.backend.elasticsearch.lowlevel.index.analysis.impl.TokenizerDefinition.class,
				r -> new org.hibernate.search.backend.elasticsearch.lowlevel.index.analysis.impl.GeneratedTokenizerDefinitionJsonCodec(
						r ) );
		factories.put( org.hibernate.search.backend.elasticsearch.lowlevel.index.settings.impl.Analysis.class,
				r -> new org.hibernate.search.backend.elasticsearch.lowlevel.index.settings.impl.GeneratedAnalysisJsonCodec(
						r ) );
		factories.put( org.hibernate.search.backend.elasticsearch.lowlevel.index.settings.impl.IndexSettings.class,
				r -> new org.hibernate.search.backend.elasticsearch.lowlevel.index.settings.impl.GeneratedIndexSettingsJsonCodec(
						r ) );
		factories.put( org.hibernate.search.backend.elasticsearch.lowlevel.index.mapping.impl.DynamicTemplate.class,
				r -> new org.hibernate.search.backend.elasticsearch.lowlevel.index.mapping.impl.GeneratedDynamicTemplateJsonCodec(
						r ) );
		factories.put(
				org.hibernate.search.backend.elasticsearch.lowlevel.index.mapping.impl.ElasticsearchDenseVectorIndexOptions.class,
				r -> new org.hibernate.search.backend.elasticsearch.lowlevel.index.mapping.impl.GeneratedElasticsearchDenseVectorIndexOptionsJsonCodec(
						r ) );
		factories.put( org.hibernate.search.backend.elasticsearch.lowlevel.index.mapping.impl.OpenSearchVectorTypeMethod.class,
				r -> new org.hibernate.search.backend.elasticsearch.lowlevel.index.mapping.impl.GeneratedOpenSearchVectorTypeMethodJsonCodec(
						r ) );
		factories.put(
				org.hibernate.search.backend.elasticsearch.lowlevel.index.mapping.impl.OpenSearchVectorTypeMethod.Parameters.class,
				r -> new org.hibernate.search.backend.elasticsearch.lowlevel.index.mapping.impl.GeneratedOpenSearchVectorTypeMethod_ParametersJsonCodec(
						r ) );
		factories.put( org.hibernate.search.backend.elasticsearch.lowlevel.index.mapping.impl.PropertyMapping.class,
				r -> new org.hibernate.search.backend.elasticsearch.lowlevel.index.mapping.impl.GeneratedPropertyMappingJsonCodec(
						r ) );
		factories.put( org.hibernate.search.backend.elasticsearch.lowlevel.index.mapping.impl.RootTypeMapping.class,
				r -> new org.hibernate.search.backend.elasticsearch.lowlevel.index.mapping.impl.GeneratedRootTypeMappingJsonCodec(
						r ) );
		factories.put( DynamicType.class, r -> new DynamicTypeJsonCodec() );
		factories.put( RoutingType.class, r -> new RoutingTypeJsonCodec() );
		factories.put( NamedDynamicTemplate.class, r -> new NamedDynamicTemplateJsonCodec( r ) );
		factories.put( org.hibernate.search.backend.elasticsearch.lowlevel.index.impl.IndexMetadata.class,
				r -> new org.hibernate.search.backend.elasticsearch.lowlevel.index.impl.GeneratedIndexMetadataJsonCodec( r ) );
		return new JsonCodecRegistry( factories );
	}

	public static JsonProvider create(boolean logPrettyPrinting) {
		return new JsonProvider( REGISTRY, logPrettyPrinting );
	}

	public static JsonCodecService createUserFacingCodec() {
		return new JsonCodecService( REGISTRY, false, true );
	}

	public static String toPrettyJson(Object value) {
		return createUserFacingCodec().write( value );
	}
}
