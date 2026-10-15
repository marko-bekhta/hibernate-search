package org.hibernate.search.backend.elasticsearch.types.dsl;

import org.hibernate.search.backend.elasticsearch.search.predicate.dsl.ElasticsearchSearchPredicateFactory;
import org.hibernate.search.backend.elasticsearch.search.sort.dsl.ElasticsearchSearchSortFactory;
import org.hibernate.search.engine.backend.types.dsl.IndexFieldTypeFactory;
import org.hibernate.search.util.json.JsonHandler;

public interface ElasticsearchIndexFieldTypeFactory extends IndexFieldTypeFactory {

	/**
	 * Define a native field type.
	 * <p>
	 * A native field type has the following characteristics:
	 * <ul>
	 *     <li>Hibernate Search doesn't know its exact type, so it must be entirely defined as a JSON object,
	 *     provided as the {@code mappingJsonString} parameter</li>
	 *     <li>When indexing, fields of this type must be populated with JSON.
	 *     The field uses Gson JSON values and can contain scalars, objects and arrays.
	 *     For compatibility, projected root arrays are split into multiple field values.</li>
	 *     <li>The predicate/sort/projection DSLs have only limited support for fields of this type.
	 *     Some features may not work and throw an exception, such as phrase predicates.
	 *     It is recommended to create the predicate/sort/projections targeting these fields from JSON
	 *     using {@link ElasticsearchSearchPredicateFactory#fromJson(String)}
	 *     or {@link ElasticsearchSearchSortFactory#fromJson(String)}</li>
	 * </ul>
	 * @return A DSL step where the index field type can be defined in more details.
	 */
	@Deprecated
	ElasticsearchNativeIndexFieldTypeMappingStep asNative();

	/** Bind one JSON-tree handler for both indexing input and projection result types.
	 * A root array is treated as one JSON value; field-level JSON null projects as Java null.
	 * Multi-valued fields store an outer array of separately indexed values and project each value independently. */
	<T> ElasticsearchNativeIndexFieldTypeTypedMappingStep<T> asNative(JsonHandler<T> handler);

}
