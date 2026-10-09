package org.hibernate.search.backend.elasticsearch.schema.management.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.hibernate.search.backend.elasticsearch.client.common.spi.ElasticsearchRequest;
import org.hibernate.search.backend.elasticsearch.json.spi.JsonProviderHelper;
import org.hibernate.search.util.json.JsonObject;
import org.hibernate.search.util.json.spi.JsonParsing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ElasticsearchSchemaExportCharacterizationTest {

	@TempDir
	Path directory;

	@Test
	void treeBodiesRemainSharedAndCollectionsAreUnmodifiable() throws IOException {
		JsonObject body =
				new JsonParsing().parse( "{\"mappings\":{\"properties\":{\"name\":{\"type\":\"keyword\"}}}}" )
						.getAsJsonObject();
		ElasticsearchIndexSchemaExportImpl export = export( ElasticsearchRequest.put().wholeEncodedPath( "/index" )
				.param( "wait_for_active_shards", "1" ).body( body ).build() );
		assertThat( export.bodyParts().get( 0 ) ).isSameAs( body );
		assertThatThrownBy( () -> export.bodyParts().add( new JsonObject() ) )
				.isInstanceOf( UnsupportedOperationException.class );
		assertThatThrownBy( () -> export.parameters().put( "key", "value" ) )
				.isInstanceOf( UnsupportedOperationException.class );
		export.toFiles( directory );
		assertThat( Files.readString( directory.resolve( "create-index.json" ) ) )
				.isEqualTo( "{\"mappings\":{\"properties\":{\"name\":{\"type\":\"keyword\"}}}}" );
		assertThat( new JsonParsing().parse( Files.readString( directory.resolve( "create-index.json" ) ) ) ).isEqualTo( body );
		assertThat( new JsonParsing().parse( Files.readString( directory.resolve( "create-index-query-params.json" ) ) ) )
				.isEqualTo( new JsonParsing().parse( "{\"wait_for_active_shards\":\"1\"}" ) );
	}

	@Test
	void multipleAndAbsentBodiesExportAsArrays() throws IOException {
		ElasticsearchIndexSchemaExportImpl multiple = export( ElasticsearchRequest.put().wholeEncodedPath( "/index" )
				.body( new JsonObject() ).body( new JsonObject() ).build() );
		multiple.toFiles( directory );
		assertThat( new JsonParsing().parse( Files.readString( directory.resolve( "create-index.json" ) ) ) )
				.isEqualTo( new JsonParsing().parse( "[{},{}]" ) );
		export( ElasticsearchRequest.put().wholeEncodedPath( "/index" ).build() ).toFiles( directory );
		assertThat( Files.readString( directory.resolve( "create-index.json" ) ) ).isEqualTo( "[]" );
	}

	private static ElasticsearchIndexSchemaExportImpl export(ElasticsearchRequest request) {
		return new ElasticsearchIndexSchemaExportImpl( JsonProviderHelper.createUserFacingCodec(), "index", request );
	}
}
