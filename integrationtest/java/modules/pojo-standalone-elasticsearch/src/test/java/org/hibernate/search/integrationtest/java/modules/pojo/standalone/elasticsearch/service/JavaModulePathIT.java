package org.hibernate.search.integrationtest.java.modules.pojo.standalone.elasticsearch.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.hibernate.search.mapper.pojo.standalone.mapping.SearchMapping;
import org.hibernate.search.util.json.JsonObject;
import org.hibernate.search.util.json.spi.JsonParsing;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class JavaModulePathIT {

	@BeforeAll
	@AfterAll
	static void clear() {
		SimulatedDatastore.clear();
	}

	/*
	 * Test that the service successfully uses Hibernate Search in the module path.
	 * We don't really care about the features themselves,
	 * but the easiest way to check this is to just use Hibernate Search features and see if it works.
	 */
	@Test
	void test() {
		checkIsInModulePath( Object.class );
		checkIsInModulePath( AuthorService.class );
		checkIsInModulePath( SearchMapping.class );

		try ( AuthorService service = new AuthorService() ) {
			service.add( "foo" );
			service.add( "bar" );
			service.add( "foo bar" );
			assertThat( service.search( "foo" ) ).hasSize( 2 );
		}
	}

	@Test
	void jsonApiIsTransitiveAndImplementationIsEncapsulated() {
		checkIsInModulePath( JsonApiExample.class );
		checkIsInModulePath( JsonObject.class );
		checkIsInModulePath( JsonParsing.class );
		assertThat( JsonApiExample.requestValue() ).isEqualTo( 42L );
		assertThat( JsonObject.class.getModule().isExported( "org.hibernate.search.util.json.impl",
				JsonApiExample.class.getModule() ) ).isFalse();
		assertThat( JsonObject.class.getModule().getDescriptor().opens() ).isEmpty();
		assertThat( JsonObject.class.getModule().getDescriptor().requires() )
				.anySatisfy( requirement -> {
					assertThat( requirement.name() ).isEqualTo( "tools.jackson.core" );
					assertThat( requirement.modifiers() )
							.doesNotContain( java.lang.module.ModuleDescriptor.Requires.Modifier.TRANSITIVE );
				} );
	}

	private void checkIsInModulePath(Class<?> clazz) {
		assertThat( clazz.getModule().isNamed() )
				.as( clazz + " should be part of a named module - there is a problem in test setup" )
				.isTrue();
	}
}
