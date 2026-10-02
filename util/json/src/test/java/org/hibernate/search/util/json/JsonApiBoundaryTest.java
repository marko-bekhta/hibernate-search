package org.hibernate.search.util.json;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class JsonApiBoundaryTest {
	@Test
	void everyModelAndSpiSignatureIsLibraryNeutral() throws Exception {
		Path classes = Path.of( "target/classes" );
		try ( var files = Files.walk( classes.resolve( "org/hibernate/search/util/json" ) ) ) {
			for ( Path file : files.filter( path -> path.toString().endsWith( ".class" ) )
					.filter( path -> !path.toString().contains( "/impl/" ) ).toList() ) {
				String name = classes.relativize( file ).toString().replace( '/', '.' ).replace( ".class", "" );
				Class<?> type = Class.forName( name );
				assertNeutral( type.toGenericString() );
				for ( var member : type.getDeclaredMethods() ) {
					if ( Modifier.isPublic( member.getModifiers() ) || Modifier.isProtected( member.getModifiers() ) ) {
						assertNeutral( member.toGenericString() );
					}
				}
				for ( var member : type.getDeclaredConstructors() ) {
					if ( Modifier.isPublic( member.getModifiers() ) || Modifier.isProtected( member.getModifiers() ) ) {
						assertNeutral( member.toGenericString() );
					}
				}
				for ( var member : type.getDeclaredFields() ) {
					if ( Modifier.isPublic( member.getModifiers() ) || Modifier.isProtected( member.getModifiers() ) ) {
						assertNeutral( member.toGenericString() );
					}
				}
			}
		}
	}

	@Test
	void productionHasNoGsonDatabindOrReflectiveMapping() throws Exception {
		try ( var files = Files.walk( Path.of( "src/main/java" ) ) ) {
			for ( Path file : files.filter( path -> path.toString().endsWith( ".java" ) ).toList() ) {
				assertThat( Files.readString( file ) ).as( file.toString() )
						.doesNotContain( "com.google.gson", "com.fasterxml.jackson.databind", "java.lang.reflect",
								"getDeclaredField", "getDeclaredMethod" );
			}
		}
	}

	private static void assertNeutral(String signature) {
		assertThat( signature ).doesNotContain( "com.google.gson", "com.fasterxml.jackson",
				"org.hibernate.search.util.json.impl" );
	}
}
