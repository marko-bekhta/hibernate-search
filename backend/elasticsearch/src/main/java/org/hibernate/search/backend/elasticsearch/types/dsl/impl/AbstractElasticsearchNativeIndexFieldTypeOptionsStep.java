package org.hibernate.search.backend.elasticsearch.types.dsl.impl;

import org.hibernate.search.backend.elasticsearch.json.JsonHandler;
import org.hibernate.search.backend.elasticsearch.json.spi.JsonCodecService;
import org.hibernate.search.backend.elasticsearch.lowlevel.index.mapping.impl.PropertyMapping;
import org.hibernate.search.backend.elasticsearch.search.aggregation.impl.ElasticsearchRangeAggregation;
import org.hibernate.search.backend.elasticsearch.search.aggregation.impl.ElasticsearchTermsAggregation;
import org.hibernate.search.backend.elasticsearch.search.predicate.impl.ElasticsearchExistsPredicate;
import org.hibernate.search.backend.elasticsearch.search.predicate.impl.ElasticsearchRangePredicate;
import org.hibernate.search.backend.elasticsearch.search.projection.impl.ElasticsearchFieldProjection;
import org.hibernate.search.backend.elasticsearch.types.codec.impl.ElasticsearchJsonHandlerFieldCodec;
import org.hibernate.search.backend.elasticsearch.types.dsl.ElasticsearchNativeIndexFieldTypeTypedOptionsStep;
import org.hibernate.search.backend.elasticsearch.types.predicate.impl.ElasticsearchStandardMatchPredicate;
import org.hibernate.search.backend.elasticsearch.types.sort.impl.ElasticsearchStandardFieldSort;
import org.hibernate.search.engine.backend.types.IndexFieldType;
import org.hibernate.search.engine.search.aggregation.spi.AggregationTypeKeys;
import org.hibernate.search.engine.search.predicate.spi.PredicateTypeKeys;
import org.hibernate.search.engine.search.projection.spi.ProjectionTypeKeys;
import org.hibernate.search.engine.search.sort.spi.SortTypeKeys;

abstract class AbstractElasticsearchNativeIndexFieldTypeOptionsStep<
		S extends AbstractElasticsearchNativeIndexFieldTypeOptionsStep<S, T>,
		T>
		extends AbstractElasticsearchIndexFieldTypeOptionsStep<S, T>
		implements ElasticsearchNativeIndexFieldTypeTypedOptionsStep<S, T> {
	private final JsonHandler<T> handler;

	AbstractElasticsearchNativeIndexFieldTypeOptionsStep(ElasticsearchIndexFieldTypeBuildContext buildContext,
			PropertyMapping mapping, JsonHandler<T> handler) {
		super( buildContext, java.util.Objects.requireNonNull( handler.javaType() ), mapping );
		this.handler = handler;
	}

	protected ElasticsearchJsonHandlerFieldCodec<T> createCodec(JsonCodecService json) {
		return new ElasticsearchJsonHandlerFieldCodec<>( json, handler );
	}

	@Override
	public IndexFieldType<T> toIndexFieldType() {
		JsonCodecService json = buildContext.getUserFacingCodec();

		ElasticsearchJsonHandlerFieldCodec<T> codec = createCodec( json );
		builder.codec( codec );

		builder.searchable( true );
		builder.queryElementFactory( PredicateTypeKeys.MATCH,
				new ElasticsearchStandardMatchPredicate.Factory<>( codec ) );
		builder.queryElementFactory( PredicateTypeKeys.RANGE, new ElasticsearchRangePredicate.Factory<>( codec ) );
		builder.queryElementFactory( PredicateTypeKeys.EXISTS, new ElasticsearchExistsPredicate.Factory<>() );

		builder.sortable( true );
		builder.queryElementFactory( SortTypeKeys.FIELD, new ElasticsearchStandardFieldSort.Factory<>( codec ) );

		builder.projectable( true );
		builder.queryElementFactory( ProjectionTypeKeys.FIELD, new ElasticsearchFieldProjection.Factory<>( codec ) );

		builder.aggregable( true );
		builder.queryElementFactory( AggregationTypeKeys.TERMS, new ElasticsearchTermsAggregation.Factory<>( codec ) );
		builder.queryElementFactory( AggregationTypeKeys.RANGE, new ElasticsearchRangeAggregation.Factory<>( codec ) );

		return builder.build();
	}
}
