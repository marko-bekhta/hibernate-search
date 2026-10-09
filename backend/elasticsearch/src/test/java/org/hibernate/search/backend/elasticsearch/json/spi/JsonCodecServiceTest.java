package org.hibernate.search.backend.elasticsearch.json.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

import org.hibernate.search.backend.elasticsearch.lowlevel.index.mapping.impl.RootTypeMapping;
import org.hibernate.search.backend.elasticsearch.lowlevel.index.settings.impl.IndexSettings;
import org.hibernate.search.util.json.JsonObject;
import org.hibernate.search.util.json.spi.JsonParseException;

import org.junit.jupiter.api.Test;

class JsonCodecServiceTest {
	private final JsonCodecService codec = JsonProviderHelper.create( false ).getCodecNoSerializeNulls();

	@Test
	void byteStreamsUseBoundedReadsAndPreserveExplicitCharsets() throws Exception {
		String json = "{\"extra\":\"" + "é中".repeat( 20000 ) + "\"}";
		var bytes = json.getBytes( StandardCharsets.UTF_16LE );
		var input = new ByteArrayInputStream( bytes ) {
			@Override
			public synchronized int read(byte[] buffer, int offset, int count) {
				assertThat( count ).isLessThanOrEqualTo( 8192 );
				return super.read( buffer, offset, Math.min( count, 7 ) );
			}
		};
		IndexSettings value = codec.decode( input, StandardCharsets.UTF_16LE, IndexSettings.class );
		assertThat( value.getExtraAttributes().get( "extra" ).getAsString() ).isEqualTo( "é中".repeat( 20000 ) );
		assertThat( codec.decode( new ByteArrayInputStream( new byte[0] ), JsonObject.class ) ).isNull();
		assertThat( codec.decode( new ByteArrayInputStream( " \n\t".getBytes( StandardCharsets.UTF_8 ) ), JsonObject.class ) )
				.isNull();
		assertThatThrownBy( () -> codec.decode( new ByteArrayInputStream( "{\"a\":".getBytes( StandardCharsets.UTF_8 ) ),
				JsonObject.class ) ).isInstanceOf( JsonParseException.class );
	}

	@Test
	void existingTreesAreDecodedAndCopiedStructurallyWithRecursiveNullPolicy() {
		var tree = codec.decode( "{\"analysis\":{\"custom\":{\"null\":null,\"array\":[null,{\"null\":null}]}},\"null\":null}",
				JsonObject.class );
		IndexSettings value = codec.decode( tree, IndexSettings.class );
		var copy = codec.decode( codec.encode( value ), IndexSettings.class );
		copy.getAnalysis().getExtraAttributes().get( "custom" ).getAsJsonObject().addProperty( "changed", true );
		assertThat( value.getAnalysis().getExtraAttributes().get( "custom" ).getAsJsonObject().has( "changed" ) ).isFalse();
		assertThat( codec.encode( value ).getAsJsonObject().has( "null" ) ).isFalse();
		var extra = codec.encode( value ).getAsJsonObject().getAsJsonObject( "analysis" ).getAsJsonObject( "custom" );
		assertThat( extra.has( "null" ) ).isFalse();
		assertThat( extra.getAsJsonArray( "array" ).get( 0 ).isJsonNull() ).isTrue();
		assertThat( extra.getAsJsonArray( "array" ).get( 1 ).getAsJsonObject().has( "null" ) ).isFalse();
		var withNulls = JsonProviderHelper.create( false ).getCodec().encode( value ).getAsJsonObject();
		assertThat( withNulls.get( "null" ).isJsonNull() ).isTrue();
		assertThat( withNulls.getAsJsonObject( "analysis" ).getAsJsonObject( "custom" ).get( "null" ).isJsonNull() ).isTrue();

	}

	@Test
	void recursiveRegisteredCodecsCanInitializeAndRunConcurrently() throws Exception {
		try ( var workers = Executors.newFixedThreadPool( 8 ) ) {
			var jobs = new java.util.ArrayList<java.util.concurrent.Callable<String>>();
			for ( int i = 0; i < 100; i++ ) {
				jobs.add( () -> {
					var service = JsonProviderHelper.create( false ).getCodecNoSerializeNulls();
					var value =
							service.decode( "{\"properties\":{\"parent\":{\"properties\":{\"leaf\":{\"type\":\"keyword\"}}}}}",
									RootTypeMapping.class );
					return service.write( value );
				} );
			}
			for ( var result : workers.invokeAll( jobs ) ) {
				assertThat( result.get() ).contains( "keyword" );
			}
		}
	}

	@Test
	void longSearchSyntaxKeepsStringSerializationAndNull() {
		var syntax = new org.hibernate.search.backend.elasticsearch.lowlevel.syntax.search.impl.Elasticsearch7SearchSyntax();
		assertThat( syntax.encodeLongForAggregation( 9007199254740993L ).getAsJsonPrimitive().isString() ).isTrue();
		assertThat( syntax.encodeLongForAggregation( 9007199254740993L ).getAsString() ).isEqualTo( "9007199254740993" );
		assertThat( syntax.encodeLongForAggregation( null ).isJsonNull() ).isTrue();
	}
}
