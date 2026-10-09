package org.hibernate.search.backend.elasticsearch.search.sort.impl;

import org.hibernate.search.backend.elasticsearch.search.common.impl.ElasticsearchSearchIndexScope;

import org.hibernate.search.util.json.JsonObject;

class ElasticsearchUserProvidedJsonSort extends AbstractElasticsearchSort {

	private final JsonObject json;

	ElasticsearchUserProvidedJsonSort(ElasticsearchSearchIndexScope<?> scope, JsonObject json) {
		super( scope );
		this.json = json;
	}

	@Override
	public void toJsonSorts(ElasticsearchSearchSortCollector collector) {
		collector.collectSort( json );
	}

}
