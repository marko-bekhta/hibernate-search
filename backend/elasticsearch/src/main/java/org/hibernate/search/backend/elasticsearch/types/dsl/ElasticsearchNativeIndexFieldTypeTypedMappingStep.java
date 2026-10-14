package org.hibernate.search.backend.elasticsearch.types.dsl;

import org.hibernate.search.backend.elasticsearch.json.JsonHandler;

/** Defines mapping JSON independently of the native indexing/projection value representation. */
public interface ElasticsearchNativeIndexFieldTypeTypedMappingStep<T> {
	ElasticsearchNativeIndexFieldTypeTypedOptionsStep<?, T> mapping(String json);

	<M> ElasticsearchNativeIndexFieldTypeTypedOptionsStep<?, T> mapping(JsonHandler<M> handler, M json);
}
