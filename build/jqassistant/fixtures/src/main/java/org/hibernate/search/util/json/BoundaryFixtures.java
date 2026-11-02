package org.hibernate.search.util.json;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import java.util.function.Supplier;

import org.hibernate.search.backend.elasticsearch.ExampleBackend;

import com.google.gson.JsonObject;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public class BoundaryFixtures {
	public interface HandlerWithNativeBound<T extends JsonNode> extends JsonHandler<T> {
	}

	public interface GenericReturn {
		List<JsonParser> parse();
	}

	public interface GenericInterface extends Supplier<JsonParser> {
	}

	public interface GenericBound<T extends JsonParser> {
	}

	public abstract static class Superclass extends JsonParser {
	}

	public static class Field {
		protected JsonParser parser;
	}

	public static class Constructor {
		public Constructor(JsonParser parser) {
		}
	}

	public interface Throws {
		void parse() throws JacksonException;
	}

	@LibraryType(JsonParser.class)
	public interface Annotated {
	}

	public interface TypeAnnotated {
		List<@LibraryType(JsonParser.class) String> parse();
	}

	public @interface AnnotationDefault {
		Class<?> value() default JsonParser.class;
	}

	@Retention(RetentionPolicy.CLASS)
	@Target({ ElementType.TYPE, ElementType.TYPE_USE, ElementType.METHOD, ElementType.PARAMETER })
	public @interface LibraryType {
		Class<?> value();
	}

	public interface CleanSignature {
		String value();
	}

	public static class PrivateUse {
		private JsonParser parser;

		public String value() {
			return null;
		}
	}

	public static class Wrapper {
		private JsonParser parser;
	}

	public interface CleanWrapper {
		Wrapper value();
	}

	public interface ArrayReturn {
		List<JsonParser>[] value();
	}

	public interface ClassAnnotationOnly {
		@LibraryType(JsonParser.class)
		String value();
	}

	public interface ParameterAnnotation {
		void consume(@LibraryType(JsonParser.class) String value);
	}

	public record RecordAnnotationOnly(@RecordLibraryType(JsonParser.class) String value) {
	}

	@Retention(RetentionPolicy.CLASS)
	@Target(ElementType.RECORD_COMPONENT)
	public @interface RecordLibraryType {
		Class<?> value();
	}

	public interface DirectArray {
		JsonParser[] value();
	}

	public interface MethodBound {
		<T extends JsonParser> T value();
	}

	public abstract static class NativeWrapper extends JsonParser {
	}

	public interface CleanNativeWrapper {
		NativeWrapper value();
	}

	public @interface AnnotationArrayDefault {
		Class<?>[] value() default { JsonParser.class };
	}

	public interface WildcardArray {
		List<? extends JsonParser[]> value();
	}

	public static class ReflectiveCall {
		public Object value(Class<?> type) {
			return type.getDeclaredFields();
		}
	}

	public static class ReflectiveMapper {
		private ObjectMapper mapper;
	}

	public static class GsonImplementationLeak {
		private JsonObject value;
	}

	public static class BackendImplementationLeak {
		private ExampleBackend value;
	}
}
