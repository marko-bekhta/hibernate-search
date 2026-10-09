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
public class GeneratedTokenFilterDefinitionTypeAdapterFactory implements TypeAdapterFactory {

	@Override
	@SuppressWarnings("unchecked")
	public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
		if ( !TypeToken.get(
				org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.lowlevel.index.analysis.impl.TokenFilterDefinition.class )
				.equals( type ) ) {
			return null;
		}
		return (TypeAdapter<T>) new Adapter( gson );
	}

	private static class Adapter
			extends TypeAdapter<
					org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.lowlevel.index.analysis.impl.TokenFilterDefinition> {

		private final TypeAdapter<java.lang.String> typeAdapter;
		private final TypeAdapter<JsonElement> jsonElementAdapter;

		Adapter(Gson gson) {
			this.typeAdapter = gson.getAdapter( java.lang.String.class );
			this.jsonElementAdapter = gson.getAdapter( JsonElement.class );
		}

		@Override
		public org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.lowlevel.index.analysis.impl.TokenFilterDefinition read(
				JsonReader in)
				throws IOException {
			if ( in.peek() == JsonToken.NULL ) {
				in.nextNull();
				return null;
			}

			org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.lowlevel.index.analysis.impl.TokenFilterDefinition instance =
					new org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.lowlevel.index.analysis.impl.TokenFilterDefinition();
			in.beginObject();
			while ( in.hasNext() ) {
				String name = in.nextName();
				switch ( name ) {
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
				org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.lowlevel.index.analysis.impl.TokenFilterDefinition instance)
				throws IOException {
			if ( instance == null ) {
				out.nullValue();
				return;
			}
			out.beginObject();
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
