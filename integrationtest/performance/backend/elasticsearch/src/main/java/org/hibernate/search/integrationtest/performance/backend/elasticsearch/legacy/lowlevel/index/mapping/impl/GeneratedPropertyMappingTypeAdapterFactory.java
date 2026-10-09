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
public class GeneratedPropertyMappingTypeAdapterFactory implements TypeAdapterFactory {

	private static final TypeToken<java.util.Map<java.lang.String,
			org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.lowlevel.index.mapping.impl.PropertyMapping>> TYPE_TOKEN_0 =
					new TypeToken<java.util.Map<java.lang.String,
							org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.lowlevel.index.mapping.impl.PropertyMapping>>() {};

	@Override
	@SuppressWarnings("unchecked")
	public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
		if ( !TypeToken.get(
				org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.lowlevel.index.mapping.impl.PropertyMapping.class )
				.equals( type ) ) {
			return null;
		}
		return (TypeAdapter<T>) new Adapter( gson );
	}

	private static class Adapter
			extends TypeAdapter<
					org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.lowlevel.index.mapping.impl.PropertyMapping> {

		private final TypeAdapter<java.lang.String> analyzerAdapter;
		private final TypeAdapter<java.lang.String> dataTypeAdapter;
		private final TypeAdapter<java.lang.Integer> dimensionAdapter;
		private final TypeAdapter<java.lang.Integer> dimsAdapter;
		private final TypeAdapter<java.lang.Boolean> docValuesAdapter;
		private final TypeAdapter<
				org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.lowlevel.index.mapping.impl.DynamicType> dynamicAdapter;
		private final TypeAdapter<java.lang.String> elementTypeAdapter;
		private final TypeAdapter<java.util.List<java.lang.String>> formatAdapter;
		private final TypeAdapter<java.lang.Boolean> indexAdapter;
		private final TypeAdapter<
				org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.lowlevel.index.mapping.impl.ElasticsearchDenseVectorIndexOptions> indexOptionsAdapter;
		private final TypeAdapter<
				org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.lowlevel.index.mapping.impl.OpenSearchVectorTypeMethod> methodAdapter;
		private final TypeAdapter<java.lang.String> normalizerAdapter;
		private final TypeAdapter<java.lang.Boolean> normsAdapter;
		private final TypeAdapter<com.google.gson.JsonElement> nullValueAdapter;
		private final TypeAdapter<java.util.Map<java.lang.String,
				org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.lowlevel.index.mapping.impl.PropertyMapping>> propertiesAdapter;
		private final TypeAdapter<java.lang.Double> scalingFactorAdapter;
		private final TypeAdapter<java.lang.String> searchAnalyzerAdapter;
		private final TypeAdapter<java.lang.String> similarityAdapter;
		private final TypeAdapter<java.lang.String> termVectorAdapter;
		private final TypeAdapter<java.lang.String> typeAdapter;
		private final TypeAdapter<JsonElement> jsonElementAdapter;

		Adapter(Gson gson) {
			this.analyzerAdapter = gson.getAdapter( java.lang.String.class );
			this.dataTypeAdapter = gson.getAdapter( java.lang.String.class );
			this.dimensionAdapter = gson.getAdapter( java.lang.Integer.class );
			this.dimsAdapter = gson.getAdapter( java.lang.Integer.class );
			this.docValuesAdapter = gson.getAdapter( java.lang.Boolean.class );
			this.dynamicAdapter = gson.getAdapter(
					org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.lowlevel.index.mapping.impl.DynamicType.class );
			this.elementTypeAdapter = gson.getAdapter( java.lang.String.class );
			this.formatAdapter =
					new org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.lowlevel.index.mapping.impl.FormatJsonAdapter();
			this.indexAdapter = gson.getAdapter( java.lang.Boolean.class );
			this.indexOptionsAdapter = gson.getAdapter(
					org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.lowlevel.index.mapping.impl.ElasticsearchDenseVectorIndexOptions.class );
			this.methodAdapter = gson.getAdapter(
					org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.lowlevel.index.mapping.impl.OpenSearchVectorTypeMethod.class );
			this.normalizerAdapter = gson.getAdapter( java.lang.String.class );
			this.normsAdapter = gson.getAdapter( java.lang.Boolean.class );
			this.nullValueAdapter = gson.getAdapter( com.google.gson.JsonElement.class );
			this.propertiesAdapter = gson.getAdapter( TYPE_TOKEN_0 );
			this.scalingFactorAdapter = gson.getAdapter( java.lang.Double.class );
			this.searchAnalyzerAdapter = gson.getAdapter( java.lang.String.class );
			this.similarityAdapter = gson.getAdapter( java.lang.String.class );
			this.termVectorAdapter = gson.getAdapter( java.lang.String.class );
			this.typeAdapter = gson.getAdapter( java.lang.String.class );
			this.jsonElementAdapter = gson.getAdapter( JsonElement.class );
		}

		@Override
		public org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.lowlevel.index.mapping.impl.PropertyMapping read(
				JsonReader in)
				throws IOException {
			if ( in.peek() == JsonToken.NULL ) {
				in.nextNull();
				return null;
			}

			org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.lowlevel.index.mapping.impl.PropertyMapping instance =
					new org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.lowlevel.index.mapping.impl.PropertyMapping();
			in.beginObject();
			while ( in.hasNext() ) {
				String name = in.nextName();
				switch ( name ) {
					case "analyzer":
						instance.setAnalyzer( analyzerAdapter.read( in ) );
						break;
					case "data_type":
						instance.setDataType( dataTypeAdapter.read( in ) );
						break;
					case "dimension":
						instance.setDimension( dimensionAdapter.read( in ) );
						break;
					case "dims":
						instance.setDims( dimsAdapter.read( in ) );
						break;
					case "doc_values":
						instance.setDocValues( docValuesAdapter.read( in ) );
						break;
					case "dynamic":
						instance.setDynamic( dynamicAdapter.read( in ) );
						break;
					case "element_type":
						instance.setElementType( elementTypeAdapter.read( in ) );
						break;
					case "format":
						instance.setFormat( formatAdapter.read( in ) );
						break;
					case "index":
						instance.setIndex( indexAdapter.read( in ) );
						break;
					case "index_options":
						instance.setIndexOptions( indexOptionsAdapter.read( in ) );
						break;
					case "method":
						instance.setMethod( methodAdapter.read( in ) );
						break;
					case "normalizer":
						instance.setNormalizer( normalizerAdapter.read( in ) );
						break;
					case "norms":
						instance.setNorms( normsAdapter.read( in ) );
						break;
					case "null_value":
						instance.setNullValue( nullValueAdapter.read( in ) );
						break;
					case "properties":
						instance.setProperties( propertiesAdapter.read( in ) );
						break;
					case "scaling_factor":
						instance.setScalingFactor( scalingFactorAdapter.read( in ) );
						break;
					case "search_analyzer":
						instance.setSearchAnalyzer( searchAnalyzerAdapter.read( in ) );
						break;
					case "similarity":
						instance.setSimilarity( similarityAdapter.read( in ) );
						break;
					case "term_vector":
						instance.setTermVector( termVectorAdapter.read( in ) );
						break;
					case "type":
						instance.setType( typeAdapter.read( in ) );
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
				org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.lowlevel.index.mapping.impl.PropertyMapping instance)
				throws IOException {
			if ( instance == null ) {
				out.nullValue();
				return;
			}
			out.beginObject();
			out.name( "analyzer" );
			analyzerAdapter.write( out, instance.getAnalyzer() );
			out.name( "data_type" );
			dataTypeAdapter.write( out, instance.getDataType() );
			out.name( "dimension" );
			dimensionAdapter.write( out, instance.getDimension() );
			out.name( "dims" );
			dimsAdapter.write( out, instance.getDims() );
			out.name( "doc_values" );
			docValuesAdapter.write( out, instance.getDocValues() );
			out.name( "dynamic" );
			dynamicAdapter.write( out, instance.getDynamic() );
			out.name( "element_type" );
			elementTypeAdapter.write( out, instance.getElementType() );
			out.name( "format" );
			formatAdapter.write( out, instance.getFormat() );
			out.name( "index" );
			indexAdapter.write( out, instance.getIndex() );
			out.name( "index_options" );
			indexOptionsAdapter.write( out, instance.getIndexOptions() );
			out.name( "method" );
			methodAdapter.write( out, instance.getMethod() );
			out.name( "normalizer" );
			normalizerAdapter.write( out, instance.getNormalizer() );
			out.name( "norms" );
			normsAdapter.write( out, instance.getNorms() );
			out.name( "null_value" );
			nullValueAdapter.write( out, instance.getNullValue() );
			out.name( "properties" );
			propertiesAdapter.write( out, instance.getProperties() );
			out.name( "scaling_factor" );
			scalingFactorAdapter.write( out, instance.getScalingFactor() );
			out.name( "search_analyzer" );
			searchAnalyzerAdapter.write( out, instance.getSearchAnalyzer() );
			out.name( "similarity" );
			similarityAdapter.write( out, instance.getSimilarity() );
			out.name( "term_vector" );
			termVectorAdapter.write( out, instance.getTermVector() );
			out.name( "type" );
			typeAdapter.write( out, instance.getType() );
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
