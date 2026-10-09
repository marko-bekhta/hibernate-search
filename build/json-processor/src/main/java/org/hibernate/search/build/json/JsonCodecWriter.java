package org.hibernate.search.build.json;

import java.io.IOException;
import java.io.PrintWriter;

import javax.annotation.processing.Filer;
import javax.lang.model.element.TypeElement;

final class JsonCodecWriter {
	private final String processorClassName;

	JsonCodecWriter(String processorClassName) {
		this.processorClassName = processorClassName;
	}

	void write(ClassModel m, TypeElement element, Filer filer) throws IOException {
		// Validate every field before opening the generated source.
		for ( FieldModel f : m.fields ) {
			f.codecExpression();
		}
		try ( PrintWriter w = new PrintWriter( filer.createSourceFile( m.codecQualifiedName, element ).openWriter() ) ) {
			w.println( "package " + m.packageName + ";" );
			w.println(
					"import org.hibernate.search.util.json.JsonElement; import org.hibernate.search.util.json.JsonObject; import org.hibernate.search.util.json.JsonNull;" );
			w.println(
					"import org.hibernate.search.util.json.spi.JsonCodec; import org.hibernate.search.util.json.spi.JsonCodecs; import org.hibernate.search.util.json.spi.JsonCodecRegistry;" );
			w.println( "@javax.annotation.processing.Generated(\"" + processorClassName + "\")" );
			w.println( "public final class " + m.codecSimpleName + " implements JsonCodec<" + m.qualifiedName + "> {" );
			for ( FieldModel f : m.fields ) {
				w.println( "private final JsonCodec<" + f.typeMirror + "> " + f.javaName + "Codec;" );
			}
			w.println( "public " + m.codecSimpleName + "(JsonCodecRegistry registry) {" );
			for ( FieldModel f : m.fields ) {
				w.println( "this." + f.javaName + "Codec = " + f.codecExpression() + ";" );
			}
			w.println( "}" );
			w.println( "public " + m.qualifiedName + " decode(JsonElement value) {" );
			w.println( "if (JsonCodecs.isNull(value)) { return null; }" );
			w.println( m.qualifiedName + " instance = new " + m.qualifiedName + "();" );
			w.println( "for (var entry : value.getAsJsonObject().entrySet()) { switch(entry.getKey()) {" );
			for ( FieldModel f : m.fields ) {
				w.println( "case \"" + f.jsonName + "\":" );
				for ( String alt : f.alternateNames ) {
					w.println( "case \"" + alt + "\":" );
				}
				w.println( "instance." + f.setter + "(" + f.javaName + "Codec.decode(entry.getValue())); break;" );
			}
			w.println( "default:" );
			if ( m.hasExtraProperties() ) {
				w.println( "if(instance." + m.extraPropsGetter + "() == null) { instance." + m.extraPropsSetter
						+ "(new java.util.LinkedHashMap<>()); }" );
				w.println( "instance." + m.extraPropsGetter + "().put(entry.getKey(),entry.getValue());" );
			}
			w.println( "break; } } return instance; }" );
			w.println( "public JsonElement encode(" + m.qualifiedName + " instance, boolean serializeNulls) {" );
			w.println( "if(instance == null) { return JsonNull.INSTANCE; } JsonObject result = new JsonObject();" );
			for ( FieldModel f : m.fields ) {
				w.println( "JsonCodecs.put(result,\"" + f.jsonName + "\"," + f.javaName + "Codec.encode(instance." + f.getter
						+ "(), serializeNulls), serializeNulls);" );
			}
			if ( m.hasExtraProperties() ) {
				w.println( "if(instance." + m.extraPropsGetter + "() != null) { instance." + m.extraPropsGetter
						+ "().forEach((k,v) -> JsonCodecs.put(result,k,JsonCodecs.copy(v,serializeNulls),serializeNulls)); }" );
			}
			w.println( "return result; } }" );
		}
	}
}
