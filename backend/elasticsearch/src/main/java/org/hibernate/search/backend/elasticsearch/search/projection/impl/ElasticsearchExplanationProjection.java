package org.hibernate.search.backend.elasticsearch.search.projection.impl;

import org.hibernate.search.backend.elasticsearch.reporting.impl.ElasticsearchSearchHints;
import org.hibernate.search.backend.elasticsearch.search.common.impl.ElasticsearchSearchIndexScope;
import org.hibernate.search.engine.search.loading.spi.LoadingResult;
import org.hibernate.search.engine.search.loading.spi.ProjectionHitMapper;
import org.hibernate.search.util.json.JsonObject;
import org.hibernate.search.util.json.spi.JsonAccessor;
import org.hibernate.search.util.json.spi.JsonObjectAccessor;

class ElasticsearchExplanationProjection extends AbstractElasticsearchProjection<JsonObject>
		implements ElasticsearchSearchProjection.Extractor<JsonObject, JsonObject> {

	private static final JsonAccessor<Boolean> REQUEST_EXPLAIN_ACCESSOR =
			JsonAccessor.root().property( "explain" ).unwrapSingletonArrays().asBoolean();
	private static final JsonObjectAccessor HIT_EXPLANATION_ACCESSOR =
			JsonAccessor.root().property( "_explanation" ).unwrapSingletonArrays().asObject();

	ElasticsearchExplanationProjection(ElasticsearchSearchIndexScope<?> scope) {
		super( scope );
	}

	@Override
	public String toString() {
		return getClass().getSimpleName();
	}

	@Override
	public Extractor<?, JsonObject> request(JsonObject requestBody, ProjectionRequestContext context) {
		context.checkNotNested(
				ElasticsearchProjectionTypeKeys.EXPLANATION,
				ElasticsearchSearchHints.INSTANCE.explanationProjectionNestingNotSupportedHint()
		);
		REQUEST_EXPLAIN_ACCESSOR.set( requestBody, true );
		return this;
	}

	@Override
	public JsonObject extract(ProjectionHitMapper<?> projectionHitMapper, JsonObject hit,
			JsonObject source, ProjectionExtractContext context) {
		// We expect the optional to always be non-empty.
		return HIT_EXPLANATION_ACCESSOR.get( hit ).get();
	}

	@Override
	public JsonObject transform(LoadingResult<?> loadingResult, JsonObject extractedData,
			ProjectionTransformContext context) {
		return extractedData;
	}
}
