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
public class GeneratedRootTypeMappingTypeAdapterFactory implements TypeAdapterFactory {

	private static final TypeToken<java.util.List<
			org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.lowlevel.index.mapping.impl.NamedDynamicTemplate>> TYPE_TOKEN_0 =
					new TypeToken<java.util.List<
							org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.lowlevel.index.mapping.impl.NamedDynamicTemplate>>() {};
	private static final TypeToken<java.util.Map<java.lang.String,
			org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.lowlevel.index.mapping.impl.PropertyMapping>> TYPE_TOKEN_1 =
					new TypeToken<java.util.Map<java.lang.String,
							org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.lowlevel.index.mapping.impl.PropertyMapping>>() {};

	@Override
	@SuppressWarnings("unchecked")
	public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
		if ( !TypeToken.get(
				org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.lowlevel.index.mapping.impl.RootTypeMapping.class )
				.equals( type ) ) {
			return null;
		}
		return (TypeAdapter<T>) new Adapter( gson );
	}

	private static class Adapter
			extends TypeAdapter<
					org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.lowlevel.index.mapping.impl.RootTypeMapping> {

		private final TypeAdapter<
				org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.lowlevel.index.mapping.impl.DynamicType> dynamicAdapter;
		private final TypeAdapter<java.util.List<
				org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.lowlevel.index.mapping.impl.NamedDynamicTemplate>> dynamicTemplatesAdapter;
		private final TypeAdapter<java.util.Map<java.lang.String,
				org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.lowlevel.index.mapping.impl.PropertyMapping>> propertiesAdapter;
		private final TypeAdapter<
				org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.lowlevel.index.mapping.impl.RoutingType> routingAdapter;
		private final TypeAdapter<JsonElement> jsonElementAdapter;

		Adapter(Gson gson) {
			this.dynamicAdapter = gson.getAdapter(
					org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.lowlevel.index.mapping.impl.DynamicType.class );
			this.dynamicTemplatesAdapter = gson.getAdapter( TYPE_TOKEN_0 );
			this.propertiesAdapter = gson.getAdapter( TYPE_TOKEN_1 );
			this.routingAdapter = gson.getAdapter(
					org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.lowlevel.index.mapping.impl.RoutingType.class );
			this.jsonElementAdapter = gson.getAdapter( JsonElement.class );
		}

		@Override
		public org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.lowlevel.index.mapping.impl.RootTypeMapping read(
				JsonReader in)
				throws IOException {
			if ( in.peek() == JsonToken.NULL ) {
				in.nextNull();
				return null;
			}

			org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.lowlevel.index.mapping.impl.RootTypeMapping instance =
					new org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.lowlevel.index.mapping.impl.RootTypeMapping();
			in.beginObject();
			while ( in.hasNext() ) {
				String name = in.nextName();
				switch ( name ) {
					case "dynamic":
						instance.setDynamic( dynamicAdapter.read( in ) );
						break;
					case "dynamic_templates":
						instance.setDynamicTemplates( dynamicTemplatesAdapter.read( in ) );
						break;
					case "properties":
						instance.setProperties( propertiesAdapter.read( in ) );
						break;
					case "_routing":
						instance.setRouting( routingAdapter.read( in ) );
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
				org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.lowlevel.index.mapping.impl.RootTypeMapping instance)
				throws IOException {
			if ( instance == null ) {
				out.nullValue();
				return;
			}
			out.beginObject();
			out.name( "dynamic" );
			dynamicAdapter.write( out, instance.getDynamic() );
			out.name( "dynamic_templates" );
			dynamicTemplatesAdapter.write( out, instance.getDynamicTemplates() );
			out.name( "properties" );
			propertiesAdapter.write( out, instance.getProperties() );
			out.name( "_routing" );
			routingAdapter.write( out, instance.getRouting() );
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
