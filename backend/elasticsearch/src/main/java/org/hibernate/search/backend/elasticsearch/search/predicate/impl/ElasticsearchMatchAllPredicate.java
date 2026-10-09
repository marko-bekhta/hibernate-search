package org.hibernate.search.backend.elasticsearch.search.predicate.impl;

import org.hibernate.search.backend.elasticsearch.search.common.impl.ElasticsearchSearchIndexScope;
import org.hibernate.search.engine.search.predicate.SearchPredicate;
import org.hibernate.search.engine.search.predicate.spi.MatchAllPredicateBuilder;
import org.hibernate.search.util.json.JsonObject;
import org.hibernate.search.util.json.spi.JsonAccessor;
import org.hibernate.search.util.json.spi.JsonObjectAccessor;

class ElasticsearchMatchAllPredicate extends AbstractElasticsearchPredicate {

	static final JsonObjectAccessor MATCH_ALL_ACCESSOR =
			JsonAccessor.root().property( "match_all" ).unwrapSingletonArrays().asObject();

	private ElasticsearchMatchAllPredicate(Builder builder) {
		super( builder );
	}

	@Override
	public void checkNestableWithin(PredicateNestingContext context) {
		// Nothing to do
	}

	@Override
	protected JsonObject doToJsonQuery(PredicateRequestContext context,
			JsonObject outerObject, JsonObject innerObject) {
		MATCH_ALL_ACCESSOR.set( outerObject, innerObject );
		return outerObject;
	}

	static class Builder extends AbstractElasticsearchPredicate.AbstractBuilder implements MatchAllPredicateBuilder {
		Builder(ElasticsearchSearchIndexScope<?> scope) {
			super( scope );
		}

		@Override
		public SearchPredicate build() {
			return new ElasticsearchMatchAllPredicate( this );
		}
	}
}
