package org.hibernate.search.backend.elasticsearch.json.spi;

import java.nio.file.Path;
import java.util.List;

import org.hibernate.search.util.impl.test.jar.PublicApiDependencyTestUtils;

import org.junit.jupiter.api.Test;

class JsonApiBoundaryTest {
	@Test
	void publishedContractsDoNotExposeJackson() throws Exception {
		PublicApiDependencyTestUtils.assertNoDependencies( Path.of( "target/classes" ),
				List.of( "com.fasterxml.jackson", "tools.jackson" ) );
	}
}
