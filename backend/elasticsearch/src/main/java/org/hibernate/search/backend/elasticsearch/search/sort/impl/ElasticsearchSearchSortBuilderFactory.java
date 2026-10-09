package org.hibernate.search.backend.elasticsearch.search.sort.impl;

import org.hibernate.search.backend.elasticsearch.search.common.impl.ElasticsearchSearchIndexScope;
import org.hibernate.search.engine.search.sort.SearchSort;
import org.hibernate.search.engine.search.sort.spi.CompositeSortBuilder;
import org.hibernate.search.engine.search.sort.spi.ScoreSortBuilder;
import org.hibernate.search.engine.search.sort.spi.SearchSortBuilderFactory;
import org.hibernate.search.engine.search.sort.spi.WithParametersSortBuilder;

import org.hibernate.search.util.json.JsonObject;

public class ElasticsearchSearchSortBuilderFactory implements SearchSortBuilderFactory {

	private final ElasticsearchSearchIndexScope<?> scope;

	public ElasticsearchSearchSortBuilderFactory(ElasticsearchSearchIndexScope<?> scope) {
		this.scope = scope;
	}

	@Override
	public ScoreSortBuilder score() {
		return new ElasticsearchScoreSort.Builder( scope );
	}

	@Override
	public SearchSort indexOrder() {
		return new ElasticsearchIndexOrderSort( scope );
	}

	@Override
	public CompositeSortBuilder composite() {
		return new ElasticsearchCompositeSort.Builder( scope );
	}

	@Override
	public WithParametersSortBuilder withParameters() {
		return new ElasticsearchWithParametersSort.Builder( scope );
	}

	public ElasticsearchSearchSort fromJson(JsonObject jsonObject) {
		return new ElasticsearchUserProvidedJsonSort( scope, jsonObject );
	}

	public ElasticsearchSearchSort fromJson(String jsonString) {
		return fromJson( scope.userFacingCodec().decode( jsonString, JsonObject.class ) );
	}
}
