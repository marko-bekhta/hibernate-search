package org.hibernate.search.backend.elasticsearch.json.spi;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.hibernate.search.util.impl.test.jar.PublicApiDependencyTestUtils;

import org.junit.jupiter.api.Test;

class JsonApiBoundaryTest {
	private static final Set<String> NATIVE_CONTRACTS = Set.of(
			"org.hibernate.search.backend.elasticsearch.json.jackson.JacksonJsonHandlers",
			"org.hibernate.search.backend.elasticsearch.json.spi.JsonCodecService",
			"org.hibernate.search.backend.elasticsearch.logging.spi.ElasticsearchClientLog",
			"org.hibernate.search.backend.elasticsearch.client.common.json.entity.spi.JsonHttpEntityContentProvider",
			"org.hibernate.search.backend.elasticsearch.client.common.json.spi.JsonLogHelper",
			"org.hibernate.search.backend.elasticsearch.client.common.spi.ElasticsearchRequest",
			"org.hibernate.search.backend.elasticsearch.client.common.spi.ElasticsearchResponse" );

	@Test
	void onlyApprovedNativeContractsExposeJackson() throws Exception {
		PublicApiDependencyTestUtils.assertNoDependencies( Path.of( "target/classes" ),
				List.of( "com.fasterxml.jackson", "tools.jackson" ),
				(type, prefix) -> "tools.jackson".equals( prefix )
						&& NATIVE_CONTRACTS.stream()
								.anyMatch( name -> type.equals( name )
										|| type.startsWith( name + "$" )
										|| type.equals( name + "_$logger" ) || type.equals( name + "_$bundle" ) ) );
	}
}
