/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.search.backend.elasticsearch.search.predicate.impl;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.search.backend.elasticsearch.search.common.impl.ElasticsearchSearchIndexScope;
import org.hibernate.search.backend.elasticsearch.types.predicate.impl.ElasticsearchFieldValueFactorFunction;
import org.hibernate.search.engine.search.predicate.SearchPredicate;
import org.hibernate.search.engine.search.predicate.spi.FieldValueFactorFunctionBuilder;
import org.hibernate.search.engine.search.predicate.spi.FunctionScorePredicateBuilder;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

class ElasticsearchFunctionScorePredicate extends AbstractElasticsearchPredicate {

	private final ElasticsearchSearchPredicate innerPredicate;
	private final List<FunctionEntry> functions;
	private final String scoreMode;
	private final String boostMode;

	private ElasticsearchFunctionScorePredicate(Builder builder) {
		super( builder );
		this.innerPredicate = builder.innerPredicate;
		this.functions = builder.functions;
		this.scoreMode = builder.scoreMode;
		this.boostMode = builder.boostMode;
	}

	@Override
	public void checkNestableWithin(PredicateNestingContext context) {
		if ( innerPredicate != null ) {
			innerPredicate.checkNestableWithin( context );
		}
		for ( FunctionEntry entry : functions ) {
			if ( entry.filter != null ) {
				entry.filter.checkNestableWithin( context );
			}
		}
	}

	@Override
	protected JsonObject doToJsonQuery(PredicateRequestContext context,
			JsonObject outerObject, JsonObject innerObject) {
		if ( innerPredicate != null ) {
			innerObject.add( "query", innerPredicate.toJsonQuery( context ) );
		}

		JsonArray functionsArray = new JsonArray();
		for ( FunctionEntry entry : functions ) {
			functionsArray.add( entry.toJson( context ) );
		}
		innerObject.add( "functions", functionsArray );

		if ( scoreMode != null ) {
			innerObject.addProperty( "score_mode", scoreMode );
		}
		if ( boostMode != null ) {
			innerObject.addProperty( "boost_mode", boostMode );
		}

		outerObject.add( "function_score", innerObject );
		return outerObject;
	}

	static class Builder extends AbstractBuilder implements FunctionScorePredicateBuilder {

		private final ElasticsearchSearchIndexScope<?> scope;
		private ElasticsearchSearchPredicate innerPredicate;
		private final List<FunctionEntry> functions = new ArrayList<>();
		private String scoreMode;
		private String boostMode;

		Builder(ElasticsearchSearchIndexScope<?> scope) {
			super( scope );
			this.scope = scope;
		}

		@Override
		public void innerPredicate(SearchPredicate predicate) {
			this.innerPredicate = ElasticsearchSearchPredicate.from( scope, predicate );
		}

		@Override
		public void addFieldValueFactorFunction(FieldValueFactorFunctionBuilder functionBuilder,
				float weight, SearchPredicate filter) {
			ElasticsearchFieldValueFactorFunction.Builder fvfBuilder =
					(ElasticsearchFieldValueFactorFunction.Builder) functionBuilder;
			functions.add( new FieldValueFactorEntry( fvfBuilder, weight, toEsPredicate( filter ) ) );
		}

		@Override
		public void addWeightFunction(float weight, SearchPredicate filter) {
			functions.add( new WeightEntry( weight, toEsPredicate( filter ) ) );
		}

		@Override
		public void scoreModeMultiply() {
			this.scoreMode = "multiply";
		}

		@Override
		public void scoreModeSum() {
			this.scoreMode = "sum";
		}

		@Override
		public void scoreModeAvg() {
			this.scoreMode = "avg";
		}

		@Override
		public void scoreModeFirst() {
			this.scoreMode = "first";
		}

		@Override
		public void scoreModeMax() {
			this.scoreMode = "max";
		}

		@Override
		public void scoreModeMin() {
			this.scoreMode = "min";
		}

		@Override
		public void boostModeMultiply() {
			this.boostMode = "multiply";
		}

		@Override
		public void boostModeReplace() {
			this.boostMode = "replace";
		}

		@Override
		public void boostModeSum() {
			this.boostMode = "sum";
		}

		@Override
		public void boostModeAvg() {
			this.boostMode = "avg";
		}

		@Override
		public void boostModeMax() {
			this.boostMode = "max";
		}

		@Override
		public void boostModeMin() {
			this.boostMode = "min";
		}

		@Override
		public SearchPredicate build() {
			return new ElasticsearchFunctionScorePredicate( this );
		}

		private ElasticsearchSearchPredicate toEsPredicate(SearchPredicate predicate) {
			return predicate == null ? null : ElasticsearchSearchPredicate.from( scope, predicate );
		}
	}

	private abstract static class FunctionEntry {
		final float weight;
		final ElasticsearchSearchPredicate filter;

		FunctionEntry(float weight, ElasticsearchSearchPredicate filter) {
			this.weight = weight;
			this.filter = filter;
		}

		JsonObject toJson(PredicateRequestContext context) {
			JsonObject entry = new JsonObject();
			contributeFunction( entry );
			if ( weight != 1.0f ) {
				entry.addProperty( "weight", weight );
			}
			if ( filter != null ) {
				entry.add( "filter", filter.toJsonQuery( context ) );
			}
			return entry;
		}

		abstract void contributeFunction(JsonObject entry);
	}

	private static class FieldValueFactorEntry extends FunctionEntry {
		private final ElasticsearchFieldValueFactorFunction.Builder fvfBuilder;

		FieldValueFactorEntry(ElasticsearchFieldValueFactorFunction.Builder fvfBuilder,
				float weight, ElasticsearchSearchPredicate filter) {
			super( weight, filter );
			this.fvfBuilder = fvfBuilder;
		}

		@Override
		void contributeFunction(JsonObject entry) {
			entry.add( "field_value_factor", fvfBuilder.toJson() );
		}
	}

	private static class WeightEntry extends FunctionEntry {
		WeightEntry(float weight, ElasticsearchSearchPredicate filter) {
			super( weight, filter );
		}

		@Override
		void contributeFunction(JsonObject entry) {
			// Standalone weight function: weight IS the function.
			// The weight is already set by FunctionEntry.toJson() via the weight field.
			// For standalone weight, we always emit the weight property even if 1.0f.
			entry.addProperty( "weight", weight );
		}
	}

}
