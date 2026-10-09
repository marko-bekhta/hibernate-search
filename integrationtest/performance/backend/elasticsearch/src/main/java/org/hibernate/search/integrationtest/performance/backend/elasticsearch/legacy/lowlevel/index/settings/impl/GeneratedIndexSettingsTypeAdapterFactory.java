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
public class GeneratedIndexSettingsTypeAdapterFactory implements TypeAdapterFactory {

	@Override
	@SuppressWarnings("unchecked")
	public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
		if ( !TypeToken.get(
				org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.lowlevel.index.settings.impl.IndexSettings.class )
				.equals( type ) ) {
			return null;
		}
		return (TypeAdapter<T>) new Adapter( gson );
	}

	private static class Adapter
			extends TypeAdapter<
					org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.lowlevel.index.settings.impl.IndexSettings> {

		private final TypeAdapter<
				org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.lowlevel.index.settings.impl.Analysis> analysisAdapter;
		private final TypeAdapter<java.lang.Boolean> knnAdapter;
		private final TypeAdapter<java.lang.Integer> maxResultWindowAdapter;
		private final TypeAdapter<JsonElement> jsonElementAdapter;

		Adapter(Gson gson) {
			this.analysisAdapter = gson.getAdapter(
					org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.lowlevel.index.settings.impl.Analysis.class );
			this.knnAdapter = gson.getAdapter( java.lang.Boolean.class );
			this.maxResultWindowAdapter = gson.getAdapter( java.lang.Integer.class );
			this.jsonElementAdapter = gson.getAdapter( JsonElement.class );
		}

		@Override
		public org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.lowlevel.index.settings.impl.IndexSettings read(
				JsonReader in)
				throws IOException {
			if ( in.peek() == JsonToken.NULL ) {
				in.nextNull();
				return null;
			}

			org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.lowlevel.index.settings.impl.IndexSettings instance =
					new org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.lowlevel.index.settings.impl.IndexSettings();
			in.beginObject();
			while ( in.hasNext() ) {
				String name = in.nextName();
				switch ( name ) {
					case "analysis":
						instance.setAnalysis( analysisAdapter.read( in ) );
						break;
					case "knn":
						instance.setKnn( knnAdapter.read( in ) );
						break;
					case "max_result_window":
						instance.setMaxResultWindow( maxResultWindowAdapter.read( in ) );
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
				org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.lowlevel.index.settings.impl.IndexSettings instance)
				throws IOException {
			if ( instance == null ) {
				out.nullValue();
				return;
			}
			out.beginObject();
			out.name( "analysis" );
			analysisAdapter.write( out, instance.getAnalysis() );
			out.name( "knn" );
			knnAdapter.write( out, instance.getKnn() );
			out.name( "max_result_window" );
			maxResultWindowAdapter.write( out, instance.getMaxResultWindow() );
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
