package org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.lowlevel.index.settings.impl;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

@javax.annotation.processing.Generated("org.hibernate.search.build.gson.GsonTypeAdapterProcessor")
public class GeneratedAnalysisTypeAdapterFactory implements TypeAdapterFactory {

	private static final TypeToken<java.util.Map<java.lang.String,
			org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.lowlevel.index.analysis.impl.AnalyzerDefinition>> TYPE_TOKEN_0 =
					new TypeToken<java.util.Map<java.lang.String,
							org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.lowlevel.index.analysis.impl.AnalyzerDefinition>>() {};
	private static final TypeToken<java.util.Map<java.lang.String,
			org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.lowlevel.index.analysis.impl.CharFilterDefinition>> TYPE_TOKEN_1 =
					new TypeToken<java.util.Map<java.lang.String,
							org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.lowlevel.index.analysis.impl.CharFilterDefinition>>() {};
	private static final TypeToken<java.util.Map<java.lang.String,
			org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.lowlevel.index.analysis.impl.NormalizerDefinition>> TYPE_TOKEN_2 =
					new TypeToken<java.util.Map<java.lang.String,
							org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.lowlevel.index.analysis.impl.NormalizerDefinition>>() {};
	private static final TypeToken<java.util.Map<java.lang.String,
			org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.lowlevel.index.analysis.impl.TokenFilterDefinition>> TYPE_TOKEN_3 =
					new TypeToken<java.util.Map<java.lang.String,
							org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.lowlevel.index.analysis.impl.TokenFilterDefinition>>() {};
	private static final TypeToken<java.util.Map<java.lang.String,
			org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.lowlevel.index.analysis.impl.TokenizerDefinition>> TYPE_TOKEN_4 =
					new TypeToken<java.util.Map<java.lang.String,
							org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.lowlevel.index.analysis.impl.TokenizerDefinition>>() {};

	@Override
	@SuppressWarnings("unchecked")
	public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
		if ( !TypeToken.get(
				org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.lowlevel.index.settings.impl.Analysis.class )
				.equals( type ) ) {
			return null;
		}
		return (TypeAdapter<T>) new Adapter( gson );
	}

	private static class Adapter
			extends TypeAdapter<
					org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.lowlevel.index.settings.impl.Analysis> {

		private final TypeAdapter<java.util.Map<java.lang.String,
				org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.lowlevel.index.analysis.impl.AnalyzerDefinition>> analyzersAdapter;
		private final TypeAdapter<java.util.Map<java.lang.String,
				org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.lowlevel.index.analysis.impl.CharFilterDefinition>> charFiltersAdapter;
		private final TypeAdapter<java.util.Map<java.lang.String,
				org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.lowlevel.index.analysis.impl.NormalizerDefinition>> normalizersAdapter;
		private final TypeAdapter<java.util.Map<java.lang.String,
				org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.lowlevel.index.analysis.impl.TokenFilterDefinition>> tokenFiltersAdapter;
		private final TypeAdapter<java.util.Map<java.lang.String,
				org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.lowlevel.index.analysis.impl.TokenizerDefinition>> tokenizersAdapter;
		private final TypeAdapter<JsonElement> jsonElementAdapter;

		Adapter(Gson gson) {
			this.analyzersAdapter = gson.getAdapter( TYPE_TOKEN_0 );
			this.charFiltersAdapter = gson.getAdapter( TYPE_TOKEN_1 );
			this.normalizersAdapter = gson.getAdapter( TYPE_TOKEN_2 );
			this.tokenFiltersAdapter = gson.getAdapter( TYPE_TOKEN_3 );
			this.tokenizersAdapter = gson.getAdapter( TYPE_TOKEN_4 );
			this.jsonElementAdapter = gson.getAdapter( JsonElement.class );
		}

		@Override
		public org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.lowlevel.index.settings.impl.Analysis read(
				JsonReader in)
				throws IOException {
			if ( in.peek() == JsonToken.NULL ) {
				in.nextNull();
				return null;
			}

			org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.lowlevel.index.settings.impl.Analysis instance =
					new org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.lowlevel.index.settings.impl.Analysis();
			in.beginObject();
			while ( in.hasNext() ) {
				String name = in.nextName();
				switch ( name ) {
					case "analyzer":
						instance.setAnalyzers( analyzersAdapter.read( in ) );
						break;
					case "char_filter":
						instance.setCharFilters( charFiltersAdapter.read( in ) );
						break;
					case "normalizer":
						instance.setNormalizers( normalizersAdapter.read( in ) );
						break;
					case "filter":
						instance.setTokenFilters( tokenFiltersAdapter.read( in ) );
						break;
					case "tokenizer":
						instance.setTokenizers( tokenizersAdapter.read( in ) );
						break;
					default:
						JsonElement value = jsonElementAdapter.read( in );
						if ( instance.getExtraAttributes() == null ) {
							instance.setExtraAttributes( new LinkedHashMap<>() );
						}
						instance.getExtraAttributes().put( name, value );
						break;
				}
			}
			in.endObject();

			return instance;
		}

		@Override
		public void write(JsonWriter out,
				org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.lowlevel.index.settings.impl.Analysis instance)
				throws IOException {
			if ( instance == null ) {
				out.nullValue();
				return;
			}
			out.beginObject();
			out.name( "analyzer" );
			analyzersAdapter.write( out, instance.getAnalyzers() );
			out.name( "char_filter" );
			charFiltersAdapter.write( out, instance.getCharFilters() );
			out.name( "normalizer" );
			normalizersAdapter.write( out, instance.getNormalizers() );
			out.name( "filter" );
			tokenFiltersAdapter.write( out, instance.getTokenFilters() );
			out.name( "tokenizer" );
			tokenizersAdapter.write( out, instance.getTokenizers() );
			java.util.Map<String, JsonElement> extraProperties = instance.getExtraAttributes();
			if ( extraProperties != null ) {
				for ( java.util.Map.Entry<String, JsonElement> entry : extraProperties.entrySet() ) {
					out.name( entry.getKey() );
					jsonElementAdapter.write( out, entry.getValue() );
				}
			}
			out.endObject();
		}
	}
}
