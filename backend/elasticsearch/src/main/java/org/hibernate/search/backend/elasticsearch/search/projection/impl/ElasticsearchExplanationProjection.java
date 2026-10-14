package org.hibernate.search.backend.elasticsearch.search.projection.impl;

import org.hibernate.search.backend.elasticsearch.json.JsonHandler;
import org.hibernate.search.backend.elasticsearch.json.impl.JsonAccessor;
import org.hibernate.search.backend.elasticsearch.json.impl.JsonHandlerBridge;
import org.hibernate.search.backend.elasticsearch.reporting.impl.ElasticsearchSearchHints;
import org.hibernate.search.backend.elasticsearch.search.common.impl.ElasticsearchSearchIndexScope;
import org.hibernate.search.engine.search.loading.spi.LoadingResult;
import org.hibernate.search.engine.search.loading.spi.ProjectionHitMapper;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

class ElasticsearchExplanationProjection<T> extends AbstractElasticsearchProjection<T>
		implements ElasticsearchSearchProjection.Extractor<ObjectNode, T> {

	private static final JsonAccessor<Boolean> REQUEST_EXPLAIN_ACCESSOR =
			JsonAccessor.root().property( "explain" ).unwrapSingletonArrays().asBoolean();

	private final JsonHandler<T> handler;

	ElasticsearchExplanationProjection(ElasticsearchSearchIndexScope<?> scope, JsonHandler<T> handler) {
		super( scope );
		this.handler = java.util.Objects.requireNonNull( handler );
	}

	@Override
	public String toString() {
		return getClass().getSimpleName();
	}

	@Override
	public Extractor<?, T> request(ObjectNode requestBody, ProjectionRequestContext context) {
		context.checkNotNested(
				ElasticsearchProjectionTypeKeys.EXPLANATION,
				ElasticsearchSearchHints.INSTANCE.explanationProjectionNestingNotSupportedHint()
		);
		REQUEST_EXPLAIN_ACCESSOR.set( requestBody, true );
		return this;
	}

	@Override
	public ObjectNode extract(ProjectionHitMapper<?> projectionHitMapper, ObjectNode hit,
			ObjectNode source, ProjectionExtractContext context) {
		tools.jackson.databind.JsonNode present = hit.get( "_explanation" );
		return present == null ? null : JsonHandlerBridge.requireObject( present, "explanation projection" );
	}

	@Override
	public T transform(LoadingResult<?> loadingResult, ObjectNode extractedData,
			ProjectionTransformContext context) {
		return JsonHandlerBridge.exportObject( handler, extractedData, "explanation projection" );
	}
}
