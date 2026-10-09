package org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.lowlevel.index.analysis.impl;

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
public class GeneratedNormalizerDefinitionTypeAdapterFactory implements TypeAdapterFactory {

	private static final TypeToken<java.util.List<java.lang.String>> TYPE_TOKEN_0 =
			new TypeToken<java.util.List<java.lang.String>>() {};
	private static final TypeToken<java.util.List<java.lang.String>> TYPE_TOKEN_1 =
			new TypeToken<java.util.List<java.lang.String>>() {};

	@Override
	@SuppressWarnings("unchecked")
	public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
		if ( !TypeToken.get(
				org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.lowlevel.index.analysis.impl.NormalizerDefinition.class )
				.equals( type ) ) {
			return null;
		}
		return (TypeAdapter<T>) new Adapter( gson );
	}

	private static class Adapter
			extends TypeAdapter<
					org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.lowlevel.index.analysis.impl.NormalizerDefinition> {

		private final TypeAdapter<java.util.List<java.lang.String>> charFiltersAdapter;
		private final TypeAdapter<java.util.List<java.lang.String>> tokenFiltersAdapter;
		private final TypeAdapter<java.lang.String> typeAdapter;
		private final TypeAdapter<JsonElement> jsonElementAdapter;

		Adapter(Gson gson) {
			this.charFiltersAdapter = gson.getAdapter( TYPE_TOKEN_0 );
			this.tokenFiltersAdapter = gson.getAdapter( TYPE_TOKEN_1 );
			this.typeAdapter = gson.getAdapter( java.lang.String.class );
			this.jsonElementAdapter = gson.getAdapter( JsonElement.class );
		}

		@Override
		public org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.lowlevel.index.analysis.impl.NormalizerDefinition read(
				JsonReader in)
				throws IOException {
			if ( in.peek() == JsonToken.NULL ) {
				in.nextNull();
				return null;
			}

			org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.lowlevel.index.analysis.impl.NormalizerDefinition instance =
					new org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.lowlevel.index.analysis.impl.NormalizerDefinition();
			in.beginObject();
			while ( in.hasNext() ) {
				String name = in.nextName();
				switch ( name ) {
					case "char_filter":
						instance.setCharFilters( charFiltersAdapter.read( in ) );
						break;
					case "filter":
						instance.setTokenFilters( tokenFiltersAdapter.read( in ) );
						break;
					case "type":
						instance.setType( typeAdapter.read( in ) );
						break;
					default:
						JsonElement value = jsonElementAdapter.read( in );
						if ( instance.getParameters() == null ) {
							instance.setParameters( new LinkedHashMap<>() );
						}
						instance.getParameters().put( name, value );
						break;
				}
			}
			in.endObject();

			return instance;
		}

		@Override
		public void write(JsonWriter out,
				org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.lowlevel.index.analysis.impl.NormalizerDefinition instance)
				throws IOException {
			if ( instance == null ) {
				out.nullValue();
				return;
			}
			out.beginObject();
			out.name( "char_filter" );
			charFiltersAdapter.write( out, instance.getCharFilters() );
			out.name( "filter" );
			tokenFiltersAdapter.write( out, instance.getTokenFilters() );
			out.name( "type" );
			typeAdapter.write( out, instance.getType() );
			java.util.Map<String, JsonElement> extraProperties = instance.getParameters();
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
