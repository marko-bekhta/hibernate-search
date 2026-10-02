package org.hibernate.search.backend.elasticsearch.schema.management.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.hibernate.search.backend.elasticsearch.client.common.spi.ElasticsearchRequest;
import org.hibernate.search.backend.elasticsearch.gson.spi.GsonProviderHelper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

class ElasticsearchSchemaExportCharacterizationTest {

	@TempDir
	Path directory;

	@Test
	void treeBodiesRemainSharedAndCollectionsAreUnmodifiable() throws IOException {
		JsonObject body =
				JsonParser.parseString( "{\"mappings\":{\"properties\":{\"name\":{\"type\":\"keyword\"}}}}" ).getAsJsonObject();
		ElasticsearchIndexSchemaExportImpl export = export( ElasticsearchRequest.put().wholeEncodedPath( "/index" )
				.param( "wait_for_active_shards", "1" ).body( body ).build() );
		assertThat( export.bodyParts().get( 0 ) ).isSameAs( body );
		assertThatThrownBy( () -> export.bodyParts().add( new JsonObject() ) )
				.isInstanceOf( UnsupportedOperationException.class );
		assertThatThrownBy( () -> export.parameters().put( "key", "value" ) )
				.isInstanceOf( UnsupportedOperationException.class );
		export.toFiles( directory );
		assertThat( JsonParser.parseString( Files.readString( directory.resolve( "create-index.json" ) ) ) ).isEqualTo( body );
		assertThat( JsonParser.parseString( Files.readString( directory.resolve( "create-index-query-params.json" ) ) ) )
				.isEqualTo( JsonParser.parseString( "{\"wait_for_active_shards\":\"1\"}" ) );
	}

	@Test
	void multipleAndAbsentBodiesExportAsArrays() throws IOException {
		ElasticsearchIndexSchemaExportImpl multiple = export( ElasticsearchRequest.put().wholeEncodedPath( "/index" )
				.body( new JsonObject() ).body( new JsonObject() ).build() );
		multiple.toFiles( directory );
		assertThat( JsonParser.parseString( Files.readString( directory.resolve( "create-index.json" ) ) ) )
				.isEqualTo( JsonParser.parseString( "[{},{}]" ) );
		export( ElasticsearchRequest.put().wholeEncodedPath( "/index" ).build() ).toFiles( directory );
		assertThat( Files.readString( directory.resolve( "create-index.json" ) ) ).isEqualTo( "[]" );
	}

	private static ElasticsearchIndexSchemaExportImpl export(ElasticsearchRequest request) {
		return new ElasticsearchIndexSchemaExportImpl( GsonProviderHelper.createUserFacingGson(), "index", request );
	}
}
