package org.hibernate.search.engine.search.predicate.dsl.impl;

import java.util.function.Function;

import org.hibernate.search.engine.search.common.ValueModel;
import org.hibernate.search.engine.search.predicate.SearchPredicate;
import org.hibernate.search.engine.search.predicate.dsl.FunctionScoreBoostModeStep;
import org.hibernate.search.engine.search.predicate.dsl.FunctionScoreClausesStep;
import org.hibernate.search.engine.search.predicate.dsl.FunctionScoreFieldValueFactorStep;
import org.hibernate.search.engine.search.predicate.dsl.FunctionScoreFieldValueFactorModifierStep;
import org.hibernate.search.engine.search.predicate.dsl.FunctionScoreScoreModeStep;
import org.hibernate.search.engine.search.predicate.dsl.FunctionScoreWeightStep;
import org.hibernate.search.engine.search.predicate.dsl.PredicateFinalStep;
import org.hibernate.search.engine.search.predicate.dsl.SearchPredicateFactory;
import org.hibernate.search.engine.search.predicate.dsl.TypedSearchPredicateFactory;
import org.hibernate.search.engine.search.predicate.dsl.spi.AbstractPredicateFinalStep;
import org.hibernate.search.engine.search.predicate.dsl.spi.SearchPredicateDslContext;
import org.hibernate.search.engine.search.predicate.spi.FieldValueFactorFunctionBuilder;
import org.hibernate.search.engine.search.predicate.spi.FunctionScorePredicateBuilder;
import org.hibernate.search.engine.search.predicate.spi.ScoreFunctionTypeKeys;
import org.hibernate.search.engine.search.reference.scorefunction.FieldValueFactorFieldReference;

public final class FunctionScoreClausesStepImpl<SR>
		extends AbstractPredicateFinalStep
		implements FunctionScoreClausesStep<SR, FunctionScoreClausesStepImpl<SR>>,
		FunctionScoreFieldValueFactorStep<SR, FunctionScoreClausesStepImpl<SR>>,
		FunctionScoreWeightStep<SR, FunctionScoreClausesStepImpl<SR>> {

	private final TypedSearchPredicateFactory<SR> factory;
	private final FunctionScorePredicateBuilder builder;
	private final ScoreModeStepImpl scoreModeStep = new ScoreModeStepImpl();
	private final BoostModeStepImpl boostModeStep = new BoostModeStepImpl();
	private final ModifierStepImpl modifierStep = new ModifierStepImpl();

	private FieldValueFactorFunctionBuilder currentFieldValueFactor;
	private float currentFunctionWeight = 1.0f;
	private SearchPredicate currentFunctionFilter;
	private boolean currentIsWeightFunction;
	private boolean hasFunctions;

	public FunctionScoreClausesStepImpl(SearchPredicateDslContext<?> dslContext,
			TypedSearchPredicateFactory<SR> factory) {
		super( dslContext );
		this.factory = factory;
		this.builder = dslContext.scope().predicateBuilders().functionScore();
	}

	// -- Inner predicate --

	public FunctionScoreClausesStepImpl<SR> innerPredicate(SearchPredicate predicate) {
		builder.innerPredicate( predicate );
		return this;
	}

	public FunctionScoreClausesStepImpl<SR> innerPredicate(PredicateFinalStep predicateStep) {
		return innerPredicate( predicateStep.toPredicate() );
	}

	// -- FunctionScoreClausesStep --

	@Override
	public FunctionScoreFieldValueFactorStep<SR, FunctionScoreClausesStepImpl<SR>> fieldValueFactor(String fieldPath) {
		flushCurrentFunction();
		currentFieldValueFactor = dslContext.scope().fieldQueryElement( fieldPath, ScoreFunctionTypeKeys.FIELD_VALUE_FACTOR );
		currentFunctionWeight = 1.0f;
		currentFunctionFilter = null;
		currentIsWeightFunction = false;
		return this;
	}

	@Override
	public <T> FunctionScoreFieldValueFactorStep<SR, FunctionScoreClausesStepImpl<SR>> fieldValueFactor(
			FieldValueFactorFieldReference<? super SR, T> fieldReference) {
		return fieldValueFactor( fieldReference.absolutePath() );
	}

	@Override
	public FunctionScoreWeightStep<SR, FunctionScoreClausesStepImpl<SR>> weight(float weight) {
		flushCurrentFunction();
		currentFieldValueFactor = null;
		currentFunctionWeight = weight;
		currentFunctionFilter = null;
		currentIsWeightFunction = true;
		hasFunctions = true;
		return this;
	}

	@Override
	public FunctionScoreScoreModeStep<FunctionScoreClausesStepImpl<SR>> scoreMode() {
		return scoreModeStep;
	}

	@Override
	public FunctionScoreBoostModeStep<FunctionScoreClausesStepImpl<SR>> boostMode() {
		return boostModeStep;
	}

	// -- FunctionScoreFieldValueFactorStep --

	@Override
	public FunctionScoreFieldValueFactorModifierStep<? extends FunctionScoreFieldValueFactorStep<SR, FunctionScoreClausesStepImpl<SR>>> modifier() {
		return modifierStep;
	}

	@Override
	public FunctionScoreFieldValueFactorStep<SR, FunctionScoreClausesStepImpl<SR>> factor(float factor) {
		currentFieldValueFactor.factor( factor );
		return this;
	}

	@Override
	public FunctionScoreFieldValueFactorStep<SR, FunctionScoreClausesStepImpl<SR>> missing(Object value,
			ValueModel valueModel) {
		currentFieldValueFactor.missing( value, valueModel );
		return this;
	}

	// -- Per-function weight (from FunctionScoreFieldValueFactorStep) --

	@Override
	public FunctionScoreFieldValueFactorStep<SR, FunctionScoreClausesStepImpl<SR>> functionWeight(float weight) {
		currentFunctionWeight = weight;
		return this;
	}

	// -- Filter (from FunctionScoreWeightStep and FunctionScoreFieldValueFactorStep) --

	@Override
	public FunctionScoreClausesStepImpl<SR> filter(SearchPredicate filter) {
		currentFunctionFilter = filter;
		return self();
	}

	@Override
	public FunctionScoreClausesStepImpl<SR> filter(
			Function<? super SearchPredicateFactory, ? extends PredicateFinalStep> filterContributor) {
		return filter( filterContributor.apply( factory ).toPredicate() );
	}

	// -- PredicateScoreStep --

	@Override
	public FunctionScoreClausesStepImpl<SR> boost(float boost) {
		builder.boost( boost );
		return self();
	}

	@Override
	public FunctionScoreClausesStepImpl<SR> constantScore() {
		builder.constantScore();
		return self();
	}

	// -- Build --

	@Override
	protected SearchPredicate build() {
		flushCurrentFunction();
		return builder.build();
	}

	// -- Internal --

	private FunctionScoreClausesStepImpl<SR> self() {
		return this;
	}

	private void flushCurrentFunction() {
		if ( currentFieldValueFactor != null ) {
			builder.addFieldValueFactorFunction( currentFieldValueFactor, currentFunctionWeight, currentFunctionFilter );
			currentFieldValueFactor = null;
			hasFunctions = true;
		}
		else if ( currentIsWeightFunction ) {
			builder.addWeightFunction( currentFunctionWeight, currentFunctionFilter );
			currentIsWeightFunction = false;
		}
	}

	// -- Sub-step implementations --

	private class ScoreModeStepImpl implements FunctionScoreScoreModeStep<FunctionScoreClausesStepImpl<SR>> {
		@Override
		public FunctionScoreClausesStepImpl<SR> multiply() {
			builder.scoreModeMultiply();
			return self();
		}

		@Override
		public FunctionScoreClausesStepImpl<SR> sum() {
			builder.scoreModeSum();
			return self();
		}

		@Override
		public FunctionScoreClausesStepImpl<SR> avg() {
			builder.scoreModeAvg();
			return self();
		}

		@Override
		public FunctionScoreClausesStepImpl<SR> first() {
			builder.scoreModeFirst();
			return self();
		}

		@Override
		public FunctionScoreClausesStepImpl<SR> max() {
			builder.scoreModeMax();
			return self();
		}

		@Override
		public FunctionScoreClausesStepImpl<SR> min() {
			builder.scoreModeMin();
			return self();
		}
	}

	private class BoostModeStepImpl implements FunctionScoreBoostModeStep<FunctionScoreClausesStepImpl<SR>> {
		@Override
		public FunctionScoreClausesStepImpl<SR> multiply() {
			builder.boostModeMultiply();
			return self();
		}

		@Override
		public FunctionScoreClausesStepImpl<SR> replace() {
			builder.boostModeReplace();
			return self();
		}

		@Override
		public FunctionScoreClausesStepImpl<SR> sum() {
			builder.boostModeSum();
			return self();
		}

		@Override
		public FunctionScoreClausesStepImpl<SR> avg() {
			builder.boostModeAvg();
			return self();
		}

		@Override
		public FunctionScoreClausesStepImpl<SR> max() {
			builder.boostModeMax();
			return self();
		}

		@Override
		public FunctionScoreClausesStepImpl<SR> min() {
			builder.boostModeMin();
			return self();
		}
	}

	private class ModifierStepImpl
			implements FunctionScoreFieldValueFactorModifierStep<FunctionScoreFieldValueFactorStep<SR, FunctionScoreClausesStepImpl<SR>>> {
		@Override
		public FunctionScoreFieldValueFactorStep<SR, FunctionScoreClausesStepImpl<SR>> none() {
			currentFieldValueFactor.modifierNone();
			return FunctionScoreClausesStepImpl.this;
		}

		@Override
		public FunctionScoreFieldValueFactorStep<SR, FunctionScoreClausesStepImpl<SR>> log() {
			currentFieldValueFactor.modifierLog();
			return FunctionScoreClausesStepImpl.this;
		}

		@Override
		public FunctionScoreFieldValueFactorStep<SR, FunctionScoreClausesStepImpl<SR>> log1p() {
			currentFieldValueFactor.modifierLog1p();
			return FunctionScoreClausesStepImpl.this;
		}

		@Override
		public FunctionScoreFieldValueFactorStep<SR, FunctionScoreClausesStepImpl<SR>> log2p() {
			currentFieldValueFactor.modifierLog2p();
			return FunctionScoreClausesStepImpl.this;
		}

		@Override
		public FunctionScoreFieldValueFactorStep<SR, FunctionScoreClausesStepImpl<SR>> ln() {
			currentFieldValueFactor.modifierLn();
			return FunctionScoreClausesStepImpl.this;
		}

		@Override
		public FunctionScoreFieldValueFactorStep<SR, FunctionScoreClausesStepImpl<SR>> ln1p() {
			currentFieldValueFactor.modifierLn1p();
			return FunctionScoreClausesStepImpl.this;
		}

		@Override
		public FunctionScoreFieldValueFactorStep<SR, FunctionScoreClausesStepImpl<SR>> ln2p() {
			currentFieldValueFactor.modifierLn2p();
			return FunctionScoreClausesStepImpl.this;
		}

		@Override
		public FunctionScoreFieldValueFactorStep<SR, FunctionScoreClausesStepImpl<SR>> square() {
			currentFieldValueFactor.modifierSquare();
			return FunctionScoreClausesStepImpl.this;
		}

		@Override
		public FunctionScoreFieldValueFactorStep<SR, FunctionScoreClausesStepImpl<SR>> sqrt() {
			currentFieldValueFactor.modifierSqrt();
			return FunctionScoreClausesStepImpl.this;
		}

		@Override
		public FunctionScoreFieldValueFactorStep<SR, FunctionScoreClausesStepImpl<SR>> reciprocal() {
			currentFieldValueFactor.modifierReciprocal();
			return FunctionScoreClausesStepImpl.this;
		}
	}

}
