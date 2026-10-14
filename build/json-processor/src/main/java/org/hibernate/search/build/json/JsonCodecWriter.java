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
			line( w, 0, "package " + m.packageName + ";" );
			w.println();
			for ( String name : new String[] { "JsonNode", "node.ObjectNode", "node.NullNode", "node.JsonNodeFactory" } ) {
				line( w, 0, "import tools.jackson.databind." + name + ";" );
			}
			w.println();
			for ( String name : new String[] { "JsonCodec", "JsonCodecs", "JsonCodecRegistry" } ) {
				line( w, 0, "import org.hibernate.search.util.json.spi." + name + ";" );
			}
			w.println();
			line( w, 0, "@javax.annotation.processing.Generated(\"" + processorClassName + "\")" );
			line( w, 0, "public final class " + m.codecSimpleName + " implements JsonCodec<" + m.qualifiedName + "> {" );
			for ( FieldModel f : m.fields ) {
				line( w, 1, "private final JsonCodec<" + f.typeMirror + "> " + f.javaName + "Codec;" );
			}
			w.println();
			line( w, 1, "public " + m.codecSimpleName + "(JsonCodecRegistry registry) {" );
			for ( FieldModel f : m.fields ) {
				line( w, 2, "this." + f.javaName + "Codec = " + f.codecExpression() + ";" );
			}
			line( w, 1, "}" );
			w.println();
			line( w, 1, "@Override" );
			line( w, 1, "public " + m.qualifiedName + " decode(JsonNode value) {" );
			line( w, 2, "if ( JsonCodecs.isNull( value ) ) {" );
			line( w, 3, "return null;" );
			line( w, 2, "}" );
			line( w, 2, m.qualifiedName + " instance = new " + m.qualifiedName + "();" );
			line( w, 2, "for ( var entry : ((ObjectNode) value).properties() ) {" );
			line( w, 3, "switch ( entry.getKey() ) {" );
			for ( FieldModel f : m.fields ) {
				line( w, 4, "case \"" + f.jsonName + "\":" );
				for ( String alt : f.alternateNames ) {
					line( w, 4, "case \"" + alt + "\":" );
				}
				line( w, 5, "instance." + f.setter + "( " + f.javaName + "Codec.decode( entry.getValue() ) );" );
				line( w, 5, "break;" );
			}
			line( w, 4, "default:" );
			if ( m.hasExtraProperties() ) {
				line( w, 5, "if ( instance." + m.extraPropsGetter + "() == null ) {" );
				line( w, 6, "instance." + m.extraPropsSetter + "( new java.util.LinkedHashMap<>() );" );
				line( w, 5, "}" );
				line( w, 5, "instance." + m.extraPropsGetter + "().put( entry.getKey(), entry.getValue() );" );
			}
			line( w, 5, "break;" );
			line( w, 3, "}" );
			line( w, 2, "}" );
			line( w, 2, "return instance;" );
			line( w, 1, "}" );
			w.println();
			line( w, 1, "@Override" );
			line( w, 1, "public JsonNode encode(" + m.qualifiedName + " instance, boolean serializeNulls) {" );
			line( w, 2, "if ( instance == null ) {" );
			line( w, 3, "return NullNode.instance;" );
			line( w, 2, "}" );
			line( w, 2, "ObjectNode result = JsonNodeFactory.instance.objectNode();" );
			for ( FieldModel f : m.fields ) {
				line( w, 2, "JsonCodecs.put( result, \"" + f.jsonName + "\", " + f.javaName + "Codec.encode( instance." + f.getter
						+ "(), serializeNulls ), serializeNulls );" );
			}
			if ( m.hasExtraProperties() ) {
				line( w, 2, "if ( instance." + m.extraPropsGetter + "() != null ) {" );
				line( w, 3, "for ( var entry : instance." + m.extraPropsGetter + "().entrySet() ) {" );
				line( w, 4, "JsonCodecs.put( result, entry.getKey(), JsonCodecs.copy( entry.getValue(), serializeNulls ), serializeNulls );" );
				line( w, 3, "}" );
				line( w, 2, "}" );
			}
			line( w, 2, "return result;" );
			line( w, 1, "}" );
			line( w, 0, "}" );
		}
	}

	private static void line(PrintWriter writer, int indentation, String text) {
		writer.print( "\t".repeat( indentation ) );
		writer.println( text );
	}
}
