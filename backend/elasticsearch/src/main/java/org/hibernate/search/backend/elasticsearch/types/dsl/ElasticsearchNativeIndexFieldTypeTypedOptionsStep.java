package org.hibernate.search.backend.elasticsearch.types.dsl;

import org.hibernate.search.engine.backend.types.dsl.IndexFieldTypeOptionsStep;

/** Options for a native JSON field whose indexing input and projection output are both {@code T}. */
public interface ElasticsearchNativeIndexFieldTypeTypedOptionsStep<
		S extends ElasticsearchNativeIndexFieldTypeTypedOptionsStep<?, T>,
		T>
		extends IndexFieldTypeOptionsStep<S, T> {
}
