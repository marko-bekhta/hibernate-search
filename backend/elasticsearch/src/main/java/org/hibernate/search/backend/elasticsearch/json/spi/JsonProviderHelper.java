package org.hibernate.search.backend.elasticsearch.json.spi;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import org.hibernate.search.backend.elasticsearch.client.common.json.spi.JsonProvider;
import org.hibernate.search.backend.elasticsearch.lowlevel.index.aliases.impl.GeneratedIndexAliasDefinitionJsonCodec;
import org.hibernate.search.backend.elasticsearch.lowlevel.index.aliases.impl.IndexAliasDefinition;
import org.hibernate.search.backend.elasticsearch.lowlevel.index.analysis.impl.AnalyzerDefinition;
import org.hibernate.search.backend.elasticsearch.lowlevel.index.analysis.impl.CharFilterDefinition;
import org.hibernate.search.backend.elasticsearch.lowlevel.index.analysis.impl.GeneratedAnalyzerDefinitionJsonCodec;
import org.hibernate.search.backend.elasticsearch.lowlevel.index.analysis.impl.GeneratedCharFilterDefinitionJsonCodec;
import org.hibernate.search.backend.elasticsearch.lowlevel.index.analysis.impl.GeneratedNormalizerDefinitionJsonCodec;
import org.hibernate.search.backend.elasticsearch.lowlevel.index.analysis.impl.GeneratedTokenFilterDefinitionJsonCodec;
import org.hibernate.search.backend.elasticsearch.lowlevel.index.analysis.impl.GeneratedTokenizerDefinitionJsonCodec;
import org.hibernate.search.backend.elasticsearch.lowlevel.index.analysis.impl.NormalizerDefinition;
import org.hibernate.search.backend.elasticsearch.lowlevel.index.analysis.impl.TokenFilterDefinition;
import org.hibernate.search.backend.elasticsearch.lowlevel.index.analysis.impl.TokenizerDefinition;
import org.hibernate.search.backend.elasticsearch.lowlevel.index.impl.GeneratedIndexMetadataJsonCodec;
import org.hibernate.search.backend.elasticsearch.lowlevel.index.impl.IndexMetadata;
import org.hibernate.search.backend.elasticsearch.lowlevel.index.mapping.impl.DynamicTemplate;
import org.hibernate.search.backend.elasticsearch.lowlevel.index.mapping.impl.DynamicType;
import org.hibernate.search.backend.elasticsearch.lowlevel.index.mapping.impl.DynamicTypeJsonCodec;
import org.hibernate.search.backend.elasticsearch.lowlevel.index.mapping.impl.ElasticsearchDenseVectorIndexOptions;
import org.hibernate.search.backend.elasticsearch.lowlevel.index.mapping.impl.GeneratedDynamicTemplateJsonCodec;
import org.hibernate.search.backend.elasticsearch.lowlevel.index.mapping.impl.GeneratedElasticsearchDenseVectorIndexOptionsJsonCodec;
import org.hibernate.search.backend.elasticsearch.lowlevel.index.mapping.impl.GeneratedOpenSearchVectorTypeMethodJsonCodec;
import org.hibernate.search.backend.elasticsearch.lowlevel.index.mapping.impl.GeneratedOpenSearchVectorTypeMethod_ParametersJsonCodec;
import org.hibernate.search.backend.elasticsearch.lowlevel.index.mapping.impl.GeneratedPropertyMappingJsonCodec;
import org.hibernate.search.backend.elasticsearch.lowlevel.index.mapping.impl.GeneratedRootTypeMappingJsonCodec;
import org.hibernate.search.backend.elasticsearch.lowlevel.index.mapping.impl.NamedDynamicTemplate;
import org.hibernate.search.backend.elasticsearch.lowlevel.index.mapping.impl.NamedDynamicTemplateJsonCodec;
import org.hibernate.search.backend.elasticsearch.lowlevel.index.mapping.impl.OpenSearchVectorTypeMethod;
import org.hibernate.search.backend.elasticsearch.lowlevel.index.mapping.impl.PropertyMapping;
import org.hibernate.search.backend.elasticsearch.lowlevel.index.mapping.impl.RootTypeMapping;
import org.hibernate.search.backend.elasticsearch.lowlevel.index.mapping.impl.RoutingType;
import org.hibernate.search.backend.elasticsearch.lowlevel.index.mapping.impl.RoutingTypeJsonCodec;
import org.hibernate.search.backend.elasticsearch.lowlevel.index.settings.impl.Analysis;
import org.hibernate.search.backend.elasticsearch.lowlevel.index.settings.impl.GeneratedAnalysisJsonCodec;
import org.hibernate.search.backend.elasticsearch.lowlevel.index.settings.impl.GeneratedIndexSettingsJsonCodec;
import org.hibernate.search.backend.elasticsearch.lowlevel.index.settings.impl.IndexSettings;
import org.hibernate.search.util.common.annotation.Incubating;
import org.hibernate.search.util.json.spi.JsonCodec;
import org.hibernate.search.util.json.spi.JsonCodecRegistry;

/** Explicit registrations for the backend's known schema model types. */
@Incubating
public final class JsonProviderHelper {
	private static final JsonCodecRegistry REGISTRY = registry();

	private JsonProviderHelper() {
	}

	private static JsonCodecRegistry registry() {
		Map<Class<?>, Function<JsonCodecRegistry, JsonCodec<?>>> factories = new LinkedHashMap<>();
		factories.put( IndexAliasDefinition.class,
				r -> new GeneratedIndexAliasDefinitionJsonCodec(
						r ) );
		factories.put( AnalyzerDefinition.class,
				r -> new GeneratedAnalyzerDefinitionJsonCodec(
						r ) );
		factories.put( CharFilterDefinition.class,
				r -> new GeneratedCharFilterDefinitionJsonCodec(
						r ) );
		factories.put( NormalizerDefinition.class,
				r -> new GeneratedNormalizerDefinitionJsonCodec(
						r ) );
		factories.put( TokenFilterDefinition.class,
				r -> new GeneratedTokenFilterDefinitionJsonCodec(
						r ) );
		factories.put( TokenizerDefinition.class,
				r -> new GeneratedTokenizerDefinitionJsonCodec(
						r ) );
		factories.put( Analysis.class,
				r -> new GeneratedAnalysisJsonCodec(
						r ) );
		factories.put( IndexSettings.class,
				r -> new GeneratedIndexSettingsJsonCodec(
						r ) );
		factories.put( DynamicTemplate.class,
				r -> new GeneratedDynamicTemplateJsonCodec(
						r ) );
		factories.put(
				ElasticsearchDenseVectorIndexOptions.class,
				r -> new GeneratedElasticsearchDenseVectorIndexOptionsJsonCodec(
						r ) );
		factories.put( OpenSearchVectorTypeMethod.class,
				r -> new GeneratedOpenSearchVectorTypeMethodJsonCodec(
						r ) );
		factories.put(
				OpenSearchVectorTypeMethod.Parameters.class,
				r -> new GeneratedOpenSearchVectorTypeMethod_ParametersJsonCodec(
						r ) );
		factories.put( PropertyMapping.class,
				r -> new GeneratedPropertyMappingJsonCodec(
						r ) );
		factories.put( RootTypeMapping.class,
				r -> new GeneratedRootTypeMappingJsonCodec(
						r ) );
		factories.put( DynamicType.class, r -> new DynamicTypeJsonCodec() );
		factories.put( RoutingType.class, r -> new RoutingTypeJsonCodec() );
		factories.put( NamedDynamicTemplate.class, r -> new NamedDynamicTemplateJsonCodec( r ) );
		factories.put( IndexMetadata.class,
				r -> new GeneratedIndexMetadataJsonCodec( r ) );
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
