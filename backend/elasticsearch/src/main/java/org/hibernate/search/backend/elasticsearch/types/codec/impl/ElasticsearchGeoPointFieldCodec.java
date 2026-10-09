package org.hibernate.search.backend.elasticsearch.types.codec.impl;

import org.hibernate.search.backend.elasticsearch.json.spi.JsonCodecService;
import org.hibernate.search.backend.elasticsearch.logging.spi.ElasticsearchClientLog;
import org.hibernate.search.engine.spatial.GeoPoint;
import org.hibernate.search.util.json.JsonElement;
import org.hibernate.search.util.json.JsonNull;
import org.hibernate.search.util.json.JsonObject;
import org.hibernate.search.util.json.spi.JsonAccessor;
import org.hibernate.search.util.json.spi.JsonElementTypes;

public final class ElasticsearchGeoPointFieldCodec extends AbstractElasticsearchFieldCodec<GeoPoint> {

	private static final JsonAccessor<Double> LATITUDE_ACCESSOR =
			JsonAccessor.root().property( "lat" ).unwrapSingletonArrays().asDouble();
	private static final JsonAccessor<Double> LONGITUDE_ACCESSOR =
			JsonAccessor.root().property( "lon" ).unwrapSingletonArrays().asDouble();

	public ElasticsearchGeoPointFieldCodec(JsonCodecService json) {
		super( json );
	}

	@Override
	public JsonElement encode(GeoPoint value) {
		if ( value == null ) {
			return JsonNull.INSTANCE;
		}
		JsonObject result = new JsonObject();
		LATITUDE_ACCESSOR.set( result, value.latitude() );
		LONGITUDE_ACCESSOR.set( result, value.longitude() );
		return result;
	}

	@Override
	public GeoPoint decode(JsonElement element) {
		if ( element == null || element.isJsonNull() ) {
			return null;
		}
		JsonObject object = JsonElementTypes.OBJECT.fromElement( element );
		double latitude =
				LATITUDE_ACCESSOR.get( object )
						.orElseThrow( ElasticsearchClientLog.INSTANCE::elasticsearchResponseMissingData );
		double longitude =
				LONGITUDE_ACCESSOR.get( object )
						.orElseThrow( ElasticsearchClientLog.INSTANCE::elasticsearchResponseMissingData );
		return GeoPoint.of( latitude, longitude );
	}

	@Override
	public boolean isCompatibleWith(ElasticsearchFieldCodec<?> other) {
		return other instanceof ElasticsearchGeoPointFieldCodec;
	}
}
