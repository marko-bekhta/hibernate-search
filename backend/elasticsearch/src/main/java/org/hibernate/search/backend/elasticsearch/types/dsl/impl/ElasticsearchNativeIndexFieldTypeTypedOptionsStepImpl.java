package org.hibernate.search.backend.elasticsearch.types.dsl.impl;

import org.hibernate.search.backend.elasticsearch.json.JsonHandler;
import org.hibernate.search.backend.elasticsearch.lowlevel.index.mapping.impl.PropertyMapping;

class ElasticsearchNativeIndexFieldTypeTypedOptionsStepImpl<T>
		extends
		AbstractElasticsearchNativeIndexFieldTypeOptionsStep<ElasticsearchNativeIndexFieldTypeTypedOptionsStepImpl<T>, T> {
	ElasticsearchNativeIndexFieldTypeTypedOptionsStepImpl(ElasticsearchIndexFieldTypeBuildContext buildContext,
			PropertyMapping mapping, JsonHandler<T> handler) {
		super( buildContext, mapping, handler );
	}

	@Override
	protected ElasticsearchNativeIndexFieldTypeTypedOptionsStepImpl<T> thisAsS() {
		return this;
	}
}
