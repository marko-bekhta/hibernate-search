package org.hibernate.search.backend.elasticsearch.types.predicate.impl;

import org.hibernate.search.backend.elasticsearch.search.common.impl.AbstractElasticsearchCodecAwareSearchQueryElementFactory;
import org.hibernate.search.backend.elasticsearch.search.common.impl.ElasticsearchSearchIndexScope;
import org.hibernate.search.backend.elasticsearch.search.common.impl.ElasticsearchSearchIndexValueFieldContext;
import org.hibernate.search.backend.elasticsearch.types.codec.impl.ElasticsearchFieldCodec;
import org.hibernate.search.engine.search.common.ValueModel;
import org.hibernate.search.engine.search.predicate.spi.FieldValueFactorFunctionBuilder;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class ElasticsearchFieldValueFactorFunction {

	private ElasticsearchFieldValueFactorFunction() {
	}

	public static class Factory<F>
			extends AbstractElasticsearchCodecAwareSearchQueryElementFactory<FieldValueFactorFunctionBuilder, F> {
		public Factory(ElasticsearchFieldCodec<F> codec) {
			super( codec );
		}

		@Override
		public FieldValueFactorFunctionBuilder create(ElasticsearchSearchIndexScope<?> scope,
				ElasticsearchSearchIndexValueFieldContext<F> field) {
			return new Builder<>( codec, scope, field );
		}
	}

	public static class Builder<F> implements FieldValueFactorFunctionBuilder {

		private final ElasticsearchFieldCodec<F> codec;
		private final ElasticsearchSearchIndexScope<?> scope;
		private final ElasticsearchSearchIndexValueFieldContext<F> field;

		private String modifier;
		private Float factor;
		private JsonElement missing;

		Builder(ElasticsearchFieldCodec<F> codec, ElasticsearchSearchIndexScope<?> scope,
				ElasticsearchSearchIndexValueFieldContext<F> field) {
			this.codec = codec;
			this.scope = scope;
			this.field = field;
		}

		@Override
		public void modifierNone() {
			this.modifier = null;
		}

		@Override
		public void modifierLog() {
			this.modifier = "log";
		}

		@Override
		public void modifierLog1p() {
			this.modifier = "log1p";
		}

		@Override
		public void modifierLog2p() {
			this.modifier = "log2p";
		}

		@Override
		public void modifierLn() {
			this.modifier = "ln";
		}

		@Override
		public void modifierLn1p() {
			this.modifier = "ln1p";
		}

		@Override
		public void modifierLn2p() {
			this.modifier = "ln2p";
		}

		@Override
		public void modifierSquare() {
			this.modifier = "square";
		}

		@Override
		public void modifierSqrt() {
			this.modifier = "sqrt";
		}

		@Override
		public void modifierReciprocal() {
			this.modifier = "reciprocal";
		}

		@Override
		public void factor(float factor) {
			this.factor = factor;
		}

		@Override
		public void missing(Object value, ValueModel valueModel) {
			this.missing = field.encodingContext().convertAndEncode( scope, field, value, valueModel,
					ElasticsearchFieldCodec::encode );
		}

		String fieldPath() {
			return field.absolutePath();
		}

		public JsonObject toJson() {
			JsonObject fvf = new JsonObject();
			fvf.addProperty( "field", field.absolutePath() );
			if ( modifier != null ) {
				fvf.addProperty( "modifier", modifier );
			}
			if ( factor != null ) {
				fvf.addProperty( "factor", factor );
			}
			if ( missing != null ) {
				fvf.add( "missing", missing );
			}
			return fvf;
		}
	}

}
