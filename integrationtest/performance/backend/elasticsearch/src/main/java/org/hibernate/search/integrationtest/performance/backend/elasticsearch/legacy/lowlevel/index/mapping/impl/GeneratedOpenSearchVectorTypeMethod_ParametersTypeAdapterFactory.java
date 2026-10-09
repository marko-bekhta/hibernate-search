package org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.lowlevel.index.mapping.impl;

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
public class GeneratedOpenSearchVectorTypeMethod_ParametersTypeAdapterFactory implements TypeAdapterFactory {

	@Override
	@SuppressWarnings("unchecked")
	public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
		if ( !TypeToken.get(
				org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.lowlevel.index.mapping.impl.OpenSearchVectorTypeMethod.Parameters.class )
				.equals( type ) ) {
			return null;
		}
		return (TypeAdapter<T>) new Adapter( gson );
	}

	private static class Adapter
			extends TypeAdapter<
					org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.lowlevel.index.mapping.impl.OpenSearchVectorTypeMethod.Parameters> {

		private final TypeAdapter<java.lang.Integer> efConstructionAdapter;
		private final TypeAdapter<java.lang.Integer> mAdapter;
		private final TypeAdapter<JsonElement> jsonElementAdapter;

		Adapter(Gson gson) {
			this.efConstructionAdapter = gson.getAdapter( java.lang.Integer.class );
			this.mAdapter = gson.getAdapter( java.lang.Integer.class );
			this.jsonElementAdapter = gson.getAdapter( JsonElement.class );
		}

		@Override
		public org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.lowlevel.index.mapping.impl.OpenSearchVectorTypeMethod.Parameters read(
				JsonReader in)
				throws IOException {
			if ( in.peek() == JsonToken.NULL ) {
				in.nextNull();
				return null;
			}

			org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.lowlevel.index.mapping.impl.OpenSearchVectorTypeMethod.Parameters instance =
					new org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.lowlevel.index.mapping.impl.OpenSearchVectorTypeMethod.Parameters();
			in.beginObject();
			while ( in.hasNext() ) {
				String name = in.nextName();
				switch ( name ) {
					case "ef_construction":
						instance.setEfConstruction( efConstructionAdapter.read( in ) );
						break;
					case "m":
						instance.setM( mAdapter.read( in ) );
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
				org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.lowlevel.index.mapping.impl.OpenSearchVectorTypeMethod.Parameters instance)
				throws IOException {
			if ( instance == null ) {
				out.nullValue();
				return;
			}
			out.beginObject();
			out.name( "ef_construction" );
			efConstructionAdapter.write( out, instance.getEfConstruction() );
			out.name( "m" );
			mAdapter.write( out, instance.getM() );
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
