/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */

package org.hibernate.search.query.dsl.impl;

import org.hibernate.search.query.dsl.FuzzyContext;
import org.hibernate.search.query.dsl.TermMatchingContext;

import org.apache.lucene.search.Query;

/**
 * @author Emmanuel Bernard
 */
class ConnectedFuzzyContext implements FuzzyContext {
	private final QueryBuildingContext queryContext;
	private final QueryCustomizer queryCustomizer;
	private final TermQueryContext termContext;

	public ConnectedFuzzyContext(QueryCustomizer queryCustomizer, QueryBuildingContext queryContext) {
		this.queryCustomizer = queryCustomizer;
		this.termContext = new TermQueryContext( TermQueryContext.Approximation.FUZZY );
		this.queryContext = queryContext;
	}

	@Override
	public TermMatchingContext onField(String field) {
		return new ConnectedTermMatchingContext( termContext, field, queryCustomizer, queryContext );
	}

	@Override
	public TermMatchingContext onFields(String... fields) {
		return new ConnectedTermMatchingContext( termContext, fields, queryCustomizer, queryContext );
	}

	@Override
	public FuzzyContext withEditDistanceUpTo(int maxEditDistance) {
		termContext.setMaxEditDistance( maxEditDistance );
		return this;
	}

	@Override
	public ConnectedFuzzyContext withPrefixLength(int prefixLength) {
		termContext.setPrefixLength( prefixLength );
		return this;
	}

	@Override
	public FuzzyContext boostedTo(float boost) {
		queryCustomizer.boostedTo( boost );
		return this;
	}

	@Override
	public FuzzyContext withConstantScore() {
		queryCustomizer.withConstantScore();
		return this;
	}

	@Override
	public FuzzyContext filteredBy(Query filter) {
		queryCustomizer.filteredBy( filter );
		return this;
	}

}
