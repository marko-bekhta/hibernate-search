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
public class GeneratedOpenSearchVectorTypeMethodTypeAdapterFactory implements TypeAdapterFactory {

	@Override
	@SuppressWarnings("unchecked")
	public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
		if ( !TypeToken.get(
				org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.lowlevel.index.mapping.impl.OpenSearchVectorTypeMethod.class )
				.equals( type ) ) {
			return null;
		}
		return (TypeAdapter<T>) new Adapter( gson );
	}

	private static class Adapter
			extends TypeAdapter<
					org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.lowlevel.index.mapping.impl.OpenSearchVectorTypeMethod> {

		private final TypeAdapter<java.lang.String> engineAdapter;
		private final TypeAdapter<java.lang.String> nameAdapter;
		private final TypeAdapter<
				org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.lowlevel.index.mapping.impl.OpenSearchVectorTypeMethod.Parameters> parametersAdapter;
		private final TypeAdapter<java.lang.String> spaceTypeAdapter;
		private final TypeAdapter<JsonElement> jsonElementAdapter;

		Adapter(Gson gson) {
			this.engineAdapter = gson.getAdapter( java.lang.String.class );
			this.nameAdapter = gson.getAdapter( java.lang.String.class );
			this.parametersAdapter = gson.getAdapter(
					org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.lowlevel.index.mapping.impl.OpenSearchVectorTypeMethod.Parameters.class );
			this.spaceTypeAdapter = gson.getAdapter( java.lang.String.class );
			this.jsonElementAdapter = gson.getAdapter( JsonElement.class );
		}

		@Override
		public org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.lowlevel.index.mapping.impl.OpenSearchVectorTypeMethod read(
				JsonReader in)
				throws IOException {
			if ( in.peek() == JsonToken.NULL ) {
				in.nextNull();
				return null;
			}

			org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.lowlevel.index.mapping.impl.OpenSearchVectorTypeMethod instance =
					new org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.lowlevel.index.mapping.impl.OpenSearchVectorTypeMethod();
			in.beginObject();
			while ( in.hasNext() ) {
				String name = in.nextName();
				switch ( name ) {
					case "engine":
						instance.setEngine( engineAdapter.read( in ) );
						break;
					case "name":
						instance.setName( nameAdapter.read( in ) );
						break;
					case "parameters":
						instance.setParameters( parametersAdapter.read( in ) );
						break;
					case "space_type":
						instance.setSpaceType( spaceTypeAdapter.read( in ) );
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
				org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.lowlevel.index.mapping.impl.OpenSearchVectorTypeMethod instance)
				throws IOException {
			if ( instance == null ) {
				out.nullValue();
				return;
			}
			out.beginObject();
			out.name( "engine" );
			engineAdapter.write( out, instance.getEngine() );
			out.name( "name" );
			nameAdapter.write( out, instance.getName() );
			out.name( "parameters" );
			parametersAdapter.write( out, instance.getParameters() );
			out.name( "space_type" );
			spaceTypeAdapter.write( out, instance.getSpaceType() );
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
