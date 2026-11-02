package org.hibernate.search.backend.lucene.search.predicate.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.LongToDoubleFunction;

import org.hibernate.search.backend.lucene.search.common.impl.LuceneSearchIndexScope;
import org.hibernate.search.backend.lucene.types.predicate.impl.LuceneFieldValueFactorFunction;
import org.hibernate.search.engine.search.predicate.SearchPredicate;
import org.hibernate.search.engine.search.predicate.spi.FieldValueFactorFunctionBuilder;
import org.hibernate.search.engine.search.predicate.spi.FunctionScorePredicateBuilder;

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.SortedNumericDocValues;
import org.apache.lucene.queries.function.FunctionScoreQuery;
import org.apache.lucene.search.DoubleValues;
import org.apache.lucene.search.DoubleValuesSource;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;

public class LuceneFunctionScorePredicate extends AbstractLuceneSearchPredicate {

	private final LuceneSearchPredicate innerPredicate;
	private final List<FunctionEntry> functions;
	private final ScoreMode scoreMode;
	private final BoostMode boostMode;

	private LuceneFunctionScorePredicate(Builder builder) {
		super( builder );
		this.innerPredicate = builder.innerPredicate;
		this.functions = List.copyOf( builder.functions );
		this.scoreMode = builder.scoreMode != null ? builder.scoreMode : ScoreMode.MULTIPLY;
		this.boostMode = builder.boostMode != null ? builder.boostMode : BoostMode.MULTIPLY;
	}

	@Override
	public void checkNestableWithin(String expectedParentNestedPath) {
		if ( innerPredicate != null ) {
			innerPredicate.checkNestableWithin( expectedParentNestedPath );
		}
		for ( FunctionEntry entry : functions ) {
			if ( entry.filter != null ) {
				entry.filter.checkNestableWithin( expectedParentNestedPath );
			}
		}
	}

	@Override
	protected Query doToQuery(PredicateRequestContext context) {
		Query innerQuery = innerPredicate != null
				? innerPredicate.toQuery( context )
				: MatchAllDocsQuery.INSTANCE;

		DoubleValuesSource combinedSource = buildCombinedSource( context );

		return switch ( boostMode ) {
			case MULTIPLY -> FunctionScoreQuery.boostByValue( innerQuery, combinedSource );
			case REPLACE -> new FunctionScoreQuery( innerQuery, combinedSource );
			default -> new FunctionScoreQuery( innerQuery,
					new BoostModeSource( combinedSource, boostMode ) );
		};
	}

	private DoubleValuesSource buildCombinedSource(PredicateRequestContext context) {
		if ( functions.size() == 1 ) {
			return functions.get( 0 ).toSource( context );
		}
		List<DoubleValuesSource> sources = new ArrayList<>();
		List<Float> weights = new ArrayList<>();
		for ( FunctionEntry entry : functions ) {
			sources.add( entry.toSource( context ) );
			weights.add( entry.weight );
		}
		return new CompositeScoreFunctionSource( sources, weights, scoreMode );
	}

	// -- Enums for internal use --

	enum ScoreMode {
		MULTIPLY {
			@Override
			double combine(List<DoubleValues> values, List<Float> weights) throws IOException {
				double result = 1.0;
				for ( int i = 0; i < values.size(); i++ ) {
					result *= values.get( i ).doubleValue() * weights.get( i );
				}
				return result;
			}
		},
		SUM {
			@Override
			double combine(List<DoubleValues> values, List<Float> weights) throws IOException {
				double result = 0.0;
				for ( int i = 0; i < values.size(); i++ ) {
					result += values.get( i ).doubleValue() * weights.get( i );
				}
				return result;
			}
		},
		AVG {
			@Override
			double combine(List<DoubleValues> values, List<Float> weights) throws IOException {
				double result = 0.0;
				double weightSum = 0;
				for ( int i = 0; i < values.size(); i++ ) {
					result += values.get( i ).doubleValue() * weights.get( i );
					weightSum += weights.get( i );
				}
				return weightSum > 0 ? result / weightSum : 1.0;
			}
		},
		FIRST {
			@Override
			double combine(List<DoubleValues> values, List<Float> weights) throws IOException {
				return values.isEmpty() ? 1.0 : values.get( 0 ).doubleValue() * weights.get( 0 );
			}
		},
		MAX {
			@Override
			double combine(List<DoubleValues> values, List<Float> weights) throws IOException {
				double result = Double.NEGATIVE_INFINITY;
				for ( int i = 0; i < values.size(); i++ ) {
					result = Math.max( result, values.get( i ).doubleValue() * weights.get( i ) );
				}
				return result == Double.NEGATIVE_INFINITY ? 1.0 : result;
			}
		},
		MIN {
			@Override
			double combine(List<DoubleValues> values, List<Float> weights) throws IOException {
				double result = Double.POSITIVE_INFINITY;
				for ( int i = 0; i < values.size(); i++ ) {
					result = Math.min( result, values.get( i ).doubleValue() * weights.get( i ) );
				}
				return result == Double.POSITIVE_INFINITY ? 1.0 : result;
			}
		};

		abstract double combine(List<DoubleValues> values, List<Float> weights) throws IOException;
	}

	enum BoostMode {
		MULTIPLY {
			@Override
			double combine(double queryScore, double functionScore) {
				return queryScore * functionScore;
			}
		},
		REPLACE {
			@Override
			double combine(double queryScore, double functionScore) {
				return functionScore;
			}
		},
		SUM {
			@Override
			double combine(double queryScore, double functionScore) {
				return queryScore + functionScore;
			}
		},
		AVG {
			@Override
			double combine(double queryScore, double functionScore) {
				return ( queryScore + functionScore ) / 2.0;
			}
		},
		MAX {
			@Override
			double combine(double queryScore, double functionScore) {
				return Math.max( queryScore, functionScore );
			}
		},
		MIN {
			@Override
			double combine(double queryScore, double functionScore) {
				return Math.min( queryScore, functionScore );
			}
		};

		abstract double combine(double queryScore, double functionScore);
	}

	// -- Builder --

	static class Builder extends AbstractBuilder implements FunctionScorePredicateBuilder {

		private final LuceneSearchIndexScope<?> scope;
		private LuceneSearchPredicate innerPredicate;
		private final List<FunctionEntry> functions = new ArrayList<>();
		private ScoreMode scoreMode;
		private BoostMode boostMode;
		private List<String> resolvedNestedPathHierarchy = Collections.emptyList();

		Builder(LuceneSearchIndexScope<?> scope) {
			super( scope );
			this.scope = scope;
		}

		@Override
		public void innerPredicate(SearchPredicate predicate) {
			this.innerPredicate = LuceneSearchPredicate.from( scope, predicate );
		}

		@Override
		public void addFieldValueFactorFunction(FieldValueFactorFunctionBuilder functionBuilder,
				float weight, SearchPredicate filter) {
			LuceneFieldValueFactorFunction.Builder<?, ?> fvfBuilder =
					(LuceneFieldValueFactorFunction.Builder<?, ?>) functionBuilder;
			validateNestedPathHierarchy( fvfBuilder.nestedPathHierarchy(), fvfBuilder.absoluteFieldPath() );
			functions.add( new FieldValueFactorEntry( fvfBuilder, weight, toLucenePredicate( filter ) ) );
		}

		@Override
		public void addWeightFunction(float weight, SearchPredicate filter) {
			functions.add( new WeightEntry( weight, toLucenePredicate( filter ) ) );
		}

		private void validateNestedPathHierarchy(List<String> fieldNestedPathHierarchy, String fieldPath) {
			if ( resolvedNestedPathHierarchy.isEmpty() && fieldNestedPathHierarchy.isEmpty() ) {
				return;
			}
			if ( resolvedNestedPathHierarchy.isEmpty() ) {
				resolvedNestedPathHierarchy = fieldNestedPathHierarchy;
				return;
			}
			if ( !Objects.equals( resolvedNestedPathHierarchy, fieldNestedPathHierarchy ) ) {
				throw new IllegalArgumentException(
						"Cannot use function score predicate with functions targeting fields"
								+ " at different nested paths: previously resolved nested path hierarchy "
								+ resolvedNestedPathHierarchy + ", but field '" + fieldPath
								+ "' has nested path hierarchy " + fieldNestedPathHierarchy
								+ ". Split into separate functionScore predicates, each inside its own"
								+ " nested() predicate, combined via bool()." );
			}
		}

		@Override
		public void scoreModeMultiply() {
			this.scoreMode = ScoreMode.MULTIPLY;
		}

		@Override
		public void scoreModeSum() {
			this.scoreMode = ScoreMode.SUM;
		}

		@Override
		public void scoreModeAvg() {
			this.scoreMode = ScoreMode.AVG;
		}

		@Override
		public void scoreModeFirst() {
			this.scoreMode = ScoreMode.FIRST;
		}

		@Override
		public void scoreModeMax() {
			this.scoreMode = ScoreMode.MAX;
		}

		@Override
		public void scoreModeMin() {
			this.scoreMode = ScoreMode.MIN;
		}

		@Override
		public void boostModeMultiply() {
			this.boostMode = BoostMode.MULTIPLY;
		}

		@Override
		public void boostModeReplace() {
			this.boostMode = BoostMode.REPLACE;
		}

		@Override
		public void boostModeSum() {
			this.boostMode = BoostMode.SUM;
		}

		@Override
		public void boostModeAvg() {
			this.boostMode = BoostMode.AVG;
		}

		@Override
		public void boostModeMax() {
			this.boostMode = BoostMode.MAX;
		}

		@Override
		public void boostModeMin() {
			this.boostMode = BoostMode.MIN;
		}

		@Override
		public SearchPredicate build() {
			return new LuceneFunctionScorePredicate( this );
		}

		private LuceneSearchPredicate toLucenePredicate(SearchPredicate predicate) {
			return predicate == null ? null : LuceneSearchPredicate.from( scope, predicate );
		}
	}

	// -- Function entries --

	private abstract static class FunctionEntry {
		final float weight;
		final LuceneSearchPredicate filter;

		FunctionEntry(float weight, LuceneSearchPredicate filter) {
			this.weight = weight;
			this.filter = filter;
		}

		abstract DoubleValuesSource toSource(PredicateRequestContext context);
	}

	private static class FieldValueFactorEntry extends FunctionEntry {
		private final String fieldPath;
		private final LuceneFieldValueFactorFunction.Builder<?, ?> fvfBuilder;

		FieldValueFactorEntry(LuceneFieldValueFactorFunction.Builder<?, ?> fvfBuilder,
				float weight, LuceneSearchPredicate filter) {
			super( weight, filter );
			this.fieldPath = fvfBuilder.absoluteFieldPath();
			this.fvfBuilder = fvfBuilder;
		}

		@Override
		DoubleValuesSource toSource(PredicateRequestContext context) {
			return new FieldValueFactorSource(
					fieldPath,
					fvfBuilder.codec().getDomain()::sortedDocValueToDouble,
					fvfBuilder.modifier(),
					fvfBuilder.factor(),
					fvfBuilder.missing()
			);
		}
	}

	private static class WeightEntry extends FunctionEntry {
		WeightEntry(float weight, LuceneSearchPredicate filter) {
			super( weight, filter );
		}

		@Override
		DoubleValuesSource toSource(PredicateRequestContext context) {
			return DoubleValuesSource.constant( 1.0 );
		}
	}

	// -- Custom DoubleValuesSource implementations --

	public enum Modifier {
		NONE {
			@Override
			double apply(double value) {
				return value;
			}
		},
		LOG {
			@Override
			double apply(double value) {
				return Math.log10( value );
			}
		},
		LOG1P {
			@Override
			double apply(double value) {
				return Math.log10( 1 + value );
			}
		},
		LOG2P {
			@Override
			double apply(double value) {
				return Math.log10( 2 + value );
			}
		},
		LN {
			@Override
			double apply(double value) {
				return Math.log( value );
			}
		},
		LN1P {
			@Override
			double apply(double value) {
				return Math.log( 1 + value );
			}
		},
		LN2P {
			@Override
			double apply(double value) {
				return Math.log( 2 + value );
			}
		},
		SQUARE {
			@Override
			double apply(double value) {
				return value * value;
			}
		},
		SQRT {
			@Override
			double apply(double value) {
				return Math.sqrt( value );
			}
		},
		RECIPROCAL {
			@Override
			double apply(double value) {
				return 1.0 / value;
			}
		};

		abstract double apply(double value);
	}

	private static class FieldValueFactorSource extends DoubleValuesSource {
		private final String fieldPath;
		private final LongToDoubleFunction docValueDecoder;
		private final Modifier modifier;
		private final float factor;
		private final Double missing;

		FieldValueFactorSource(String fieldPath, LongToDoubleFunction docValueDecoder,
				Modifier modifier, float factor, Double missing) {
			this.fieldPath = fieldPath;
			this.docValueDecoder = docValueDecoder;
			this.modifier = modifier;
			this.factor = factor;
			this.missing = missing;
		}

		@Override
		public DoubleValues getValues(LeafReaderContext ctx, DoubleValues scores) throws IOException {
			SortedNumericDocValues docValues = ctx.reader().getSortedNumericDocValues( fieldPath );
			return new DoubleValues() {
				private boolean hasValue;

				@Override
				public double doubleValue() throws IOException {
					double fieldValue;
					if ( hasValue ) {
						fieldValue = docValueDecoder.applyAsDouble( docValues.nextValue() );
					}
					else {
						fieldValue = missing;
					}
					return modifier.apply( factor * fieldValue );
				}

				@Override
				public boolean advanceExact(int doc) throws IOException {
					if ( docValues == null ) {
						if ( missing != null ) {
							hasValue = false;
							return true;
						}
						throw new IOException( "Missing value for field [" + fieldPath + "]" );
					}
					hasValue = docValues.advanceExact( doc );
					if ( !hasValue && missing == null ) {
						throw new IOException( "Missing value for field [" + fieldPath + "]" );
					}
					return true;
				}
			};
		}

		@Override
		public boolean needsScores() {
			return false;
		}

		@Override
		public DoubleValuesSource rewrite(IndexSearcher reader) {
			return this;
		}

		@Override
		public int hashCode() {
			return Objects.hash( fieldPath, modifier, factor, missing );
		}

		@Override
		public boolean equals(Object obj) {
			if ( this == obj ) {
				return true;
			}
			if ( !( obj instanceof FieldValueFactorSource ) ) {
				return false;
			}
			FieldValueFactorSource other = (FieldValueFactorSource) obj;
			return Objects.equals( fieldPath, other.fieldPath )
					&& Objects.equals( modifier, other.modifier )
					&& factor == other.factor
					&& Objects.equals( missing, other.missing );
		}

		@Override
		public String toString() {
			return "FieldValueFactor(" + fieldPath + ")";
		}

		@Override
		public boolean isCacheable(LeafReaderContext ctx) {
			return true;
		}
	}

	private static class CompositeScoreFunctionSource extends DoubleValuesSource {
		private final List<DoubleValuesSource> sources;
		private final List<Float> weights;
		private final ScoreMode scoreMode;

		CompositeScoreFunctionSource(List<DoubleValuesSource> sources, List<Float> weights,
				ScoreMode scoreMode) {
			this.sources = sources;
			this.weights = weights;
			this.scoreMode = scoreMode;
		}

		@Override
		public DoubleValues getValues(LeafReaderContext ctx, DoubleValues scores) throws IOException {
			List<DoubleValues> values = new ArrayList<>();
			for ( DoubleValuesSource source : sources ) {
				values.add( source.getValues( ctx, scores ) );
			}
			return new DoubleValues() {
				@Override
				public double doubleValue() throws IOException {
					return scoreMode.combine( values, weights );
				}

				@Override
				public boolean advanceExact(int doc) throws IOException {
					for ( DoubleValues v : values ) {
						v.advanceExact( doc );
					}
					return true;
				}
			};
		}

		@Override
		public boolean needsScores() {
			return false;
		}

		@Override
		public DoubleValuesSource rewrite(IndexSearcher reader) throws IOException {
			List<DoubleValuesSource> rewritten = new ArrayList<>();
			for ( DoubleValuesSource source : sources ) {
				rewritten.add( source.rewrite( reader ) );
			}
			return new CompositeScoreFunctionSource( rewritten, weights, scoreMode );
		}

		@Override
		public int hashCode() {
			return Objects.hash( sources, weights, scoreMode );
		}

		@Override
		public boolean equals(Object obj) {
			if ( this == obj ) {
				return true;
			}
			if ( !( obj instanceof CompositeScoreFunctionSource ) ) {
				return false;
			}
			CompositeScoreFunctionSource other = (CompositeScoreFunctionSource) obj;
			return Objects.equals( sources, other.sources )
					&& Objects.equals( weights, other.weights )
					&& scoreMode == other.scoreMode;
		}

		@Override
		public String toString() {
			return "CompositeScoreFunction(" + scoreMode + ", " + sources + ")";
		}

		@Override
		public boolean isCacheable(LeafReaderContext ctx) {
			for ( DoubleValuesSource source : sources ) {
				if ( !source.isCacheable( ctx ) ) {
					return false;
				}
			}
			return true;
		}
	}

	private static class BoostModeSource extends DoubleValuesSource {
		private final DoubleValuesSource functionSource;
		private final BoostMode boostMode;

		BoostModeSource(DoubleValuesSource functionSource, BoostMode boostMode) {
			this.functionSource = functionSource;
			this.boostMode = boostMode;
		}

		@Override
		public DoubleValues getValues(LeafReaderContext ctx, DoubleValues scores) throws IOException {
			DoubleValues functionValues = functionSource.getValues( ctx, scores );
			return new DoubleValues() {
				@Override
				public double doubleValue() throws IOException {
					double queryScore = scores != null ? scores.doubleValue() : 0;
					double functionScore = functionValues.doubleValue();
					return boostMode.combine( queryScore, functionScore );
				}

				@Override
				public boolean advanceExact(int doc) throws IOException {
					return functionValues.advanceExact( doc );
				}
			};
		}

		@Override
		public boolean needsScores() {
			return true;
		}

		@Override
		public DoubleValuesSource rewrite(IndexSearcher reader) throws IOException {
			return new BoostModeSource( functionSource.rewrite( reader ), boostMode );
		}

		@Override
		public int hashCode() {
			return Objects.hash( functionSource, boostMode );
		}

		@Override
		public boolean equals(Object obj) {
			if ( this == obj ) {
				return true;
			}
			if ( !( obj instanceof BoostModeSource ) ) {
				return false;
			}
			BoostModeSource other = (BoostModeSource) obj;
			return Objects.equals( functionSource, other.functionSource )
					&& boostMode == other.boostMode;
		}

		@Override
		public String toString() {
			return "BoostMode(" + boostMode + ", " + functionSource + ")";
		}

		@Override
		public boolean isCacheable(LeafReaderContext ctx) {
			return functionSource.isCacheable( ctx );
		}
	}

}
