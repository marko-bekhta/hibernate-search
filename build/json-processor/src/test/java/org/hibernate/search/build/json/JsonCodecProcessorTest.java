package org.hibernate.search.build.json;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

import org.hibernate.search.util.json.spi.JsonCodec;
import org.hibernate.search.util.json.spi.JsonCodecRegistry;
import org.hibernate.search.util.json.spi.JsonParsing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class JsonCodecProcessorTest {
	@TempDir
	Path directory;
	private static final String ANNOTATIONS = "org.hibernate.search.backend.elasticsearch.json.impl.";

	private List<JavaFileObject> annotations() {
		List<JavaFileObject> sources = new ArrayList<>();
		sources.add( source( ANNOTATIONS + "JsonSerializable", "package " + ANNOTATIONS.substring( 0, ANNOTATIONS.length() - 1 )
				+ "; public @interface JsonSerializable {}" ) );
		sources.add( source( ANNOTATIONS + "JsonName", "package " + ANNOTATIONS.substring( 0, ANNOTATIONS.length() - 1 )
				+ "; public @interface JsonName { String value(); String[] alternate() default {}; }" ) );
		sources.add( source( ANNOTATIONS + "SerializeExtraProperties", "package "
				+ ANNOTATIONS.substring( 0, ANNOTATIONS.length() - 1 ) + "; public @interface SerializeExtraProperties {}" ) );
		sources.add( source( ANNOTATIONS + "JsonFieldCodec", "package " + ANNOTATIONS.substring( 0, ANNOTATIONS.length() - 1 )
				+ "; public @interface JsonFieldCodec { Class<?> value(); }" ) );
		return sources;
	}

	@Test
	@SuppressWarnings("unchecked")
	void generatedCodeCompilesAndHandlesInheritedRenamedCustomNestedAndExtraFields() throws Exception {
		var sources = annotations();
		sources.add( source( "fixture.Parent",
				"""
						package fixture;
						public class Parent {
						@org.hibernate.search.backend.elasticsearch.json.impl.JsonName(value="renamed", alternate={"old"}) private String inherited;
						@org.hibernate.search.backend.elasticsearch.json.impl.SerializeExtraProperties private java.util.Map<String,org.hibernate.search.util.json.JsonElement> extras;
						public String getInherited() { return inherited; } public void setInherited(String value) { inherited=value; }
						public java.util.Map<String,org.hibernate.search.util.json.JsonElement> getExtras() { return extras; }
						public void setExtras(java.util.Map<String,org.hibernate.search.util.json.JsonElement> value) { extras=value; }
						}
						""" ) );
		sources.add( source( "fixture.Joined",
				"""
						package fixture;
						public class Joined implements org.hibernate.search.util.json.spi.JsonCodec<java.util.List<String>> {
						public java.util.List<String> decode(org.hibernate.search.util.json.JsonElement value) { return org.hibernate.search.util.json.spi.JsonCodecs.isNull(value) ? null : java.util.List.of(value.getAsString().split(";")); }
						public org.hibernate.search.util.json.JsonElement encode(java.util.List<String> value, boolean serializeNulls) { return value==null ? org.hibernate.search.util.json.JsonNull.INSTANCE : new org.hibernate.search.util.json.JsonPrimitive(String.join(";",value)); }
						}
						""" ) );
		sources.add( source( "fixture.Fixture",
				"""
						package fixture;
						@org.hibernate.search.backend.elasticsearch.json.impl.JsonSerializable
						public class Fixture extends Parent {
						private java.util.List<Fixture> children;
						@org.hibernate.search.backend.elasticsearch.json.impl.JsonFieldCodec(Joined.class) private java.util.List<String> custom;
						public java.util.List<Fixture> getChildren() { return children; } public void setChildren(java.util.List<Fixture> value) { children=value; }
						public java.util.List<String> getCustom() { return custom; } public void setCustom(java.util.List<String> value) { custom=value; }
						}
						""" ) );
		DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
		assertThat( compile( sources, diagnostics ) ).as( diagnostics.getDiagnostics().toString() ).isTrue();
		try ( var loader =
				new URLClassLoader( new java.net.URL[] { directory.toUri().toURL() }, getClass().getClassLoader() ) ) {
			Class<?> type = loader.loadClass( "fixture.Fixture" );
			var constructor = loader.loadClass( "fixture.GeneratedFixtureJsonCodec" ).getConstructor( JsonCodecRegistry.class );
			var registry = new JsonCodecRegistry( Map.of( type, r -> {
				try {
					return (JsonCodec<?>) constructor.newInstance( r );
				}
				catch (ReflectiveOperationException e) {
					throw new IllegalStateException( e );
				}
			} ) );
			JsonCodec<Object> codec = (JsonCodec<Object>) registry.get( type );
			var parsing = new JsonParsing();
			Object value = codec.decode( parsing.parse(
					"{\"z\":null,\"old\":\"parent\",\"custom\":\"a;b\",\"children\":[{\"renamed\":\"child\",\"extra\":{\"null\":null}}],\"a\":1}" ) );
			var encoded = codec.encode( value, false ).getAsJsonObject();
			assertThat( encoded.keySet() ).containsExactly( "children", "custom", "renamed", "a" );
			assertThat( encoded ).isEqualTo( parsing.parse(
					"{\"children\":[{\"renamed\":\"child\",\"extra\":{}}],\"custom\":\"a;b\",\"renamed\":\"parent\",\"a\":1}" ) );
			assertThat( codec.encode( value, true ).getAsJsonObject().get( "z" ).isJsonNull() ).isTrue();
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "java.util.Set<String>", "java.util.Map<Integer,String>", "String[]", "int" })
	void unsupportedGenericTypesFailCompilationWithActionableDiagnostic(String fieldType) throws Exception {
		var sources = annotations();
		sources.add( source( "fixture.Invalid", """
				package fixture;
				@org.hibernate.search.backend.elasticsearch.json.impl.JsonSerializable
				public class Invalid {
				private %s unsupported;
				public %s getUnsupported() { return unsupported; }
				public void setUnsupported(%s value) { unsupported=value; }
				}
				""".formatted( fieldType, fieldType, fieldType ) ) );
		var diagnostics = new DiagnosticCollector<JavaFileObject>();
		assertThat( compile( sources, diagnostics ) ).isFalse();
		assertThat( diagnostics.getDiagnostics().toString() ).contains( "Unsupported JSON codec field type:" );
	}

	private boolean compile(List<JavaFileObject> sources, DiagnosticCollector<JavaFileObject> diagnostics) throws Exception {
		var compiler = ToolProvider.getSystemJavaCompiler();
		try ( var manager = compiler.getStandardFileManager( diagnostics, null, null ) ) {
			var task = compiler.getTask( null, manager, diagnostics, List.of( "-d", directory.toString(), "-s",
					directory.toString(), "-classpath", System.getProperty( "java.class.path" ) ), null, sources );
			task.setProcessors( List.of( new JsonCodecProcessor() ) );
			return task.call();
		}
	}

	private static JavaFileObject source(String name, String code) {
		return new SimpleJavaFileObject(
				URI.create( "string:///" + name.replace( '.', '/' ) + JavaFileObject.Kind.SOURCE.extension ),
				JavaFileObject.Kind.SOURCE ) {
			@Override
			public CharSequence getCharContent(boolean ignoreEncodingErrors) {
				return code;
			}
		};
	}
}
