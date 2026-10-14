package org.hibernate.search.backend.elasticsearch.search.aggregation.impl;

import org.hibernate.search.backend.elasticsearch.json.JsonHandler;
import org.hibernate.search.backend.elasticsearch.json.impl.JsonHandlerBridge;
import org.hibernate.search.backend.elasticsearch.search.common.impl.ElasticsearchSearchIndexScope;
import org.hibernate.search.engine.search.aggregation.AggregationKey;

import tools.jackson.databind.node.ObjectNode;

class ElasticsearchUserProvidedJsonAggregation<T> extends AbstractElasticsearchAggregation<T> {

	private final ObjectNode requestJson;
	private final JsonHandler<T> handler;

	private ElasticsearchUserProvidedJsonAggregation(Builder<T> builder) {
		super( builder );
		this.requestJson = builder.json;
		this.handler = builder.handler;
	}

	@Override
	public Extractor<T> request(AggregationRequestContext context, AggregationKey<?> key,
			ObjectNode jsonAggregations) {
		jsonAggregations.set( key.name(), requestJson );
		return new HandlerExtractor<>( key, handler );
	}

	static class Builder<T> extends AbstractBuilder<T> {

		private final ObjectNode json;
		private final JsonHandler<T> handler;

		Builder(ElasticsearchSearchIndexScope<?> scope, ObjectNode json, JsonHandler<T> handler) {
			super( scope );
			this.json = json;
			this.handler = java.util.Objects.requireNonNull( handler );
		}

		@Override
		public ElasticsearchSearchAggregation<T> build() {
			return new ElasticsearchUserProvidedJsonAggregation<>( this );
		}
	}

	private record HandlerExtractor<T>(AggregationKey<?> key, JsonHandler<T> handler) implements Extractor<T> {
		@Override
		public T extract(ObjectNode aggregationResult, AggregationExtractContext context) {
			return JsonHandlerBridge.exportObject( handler, aggregationResult.get( key.name() ), "aggregation result" );
		}
	}
}
