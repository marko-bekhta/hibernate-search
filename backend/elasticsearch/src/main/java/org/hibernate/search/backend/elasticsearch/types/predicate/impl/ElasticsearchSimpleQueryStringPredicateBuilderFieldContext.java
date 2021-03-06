/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.backend.elasticsearch.types.predicate.impl;

import org.hibernate.search.engine.search.predicate.spi.SimpleQueryStringPredicateBuilder;

import com.google.gson.JsonPrimitive;

public final class ElasticsearchSimpleQueryStringPredicateBuilderFieldContext
		implements SimpleQueryStringPredicateBuilder.FieldContext {
	private static final String BOOST_OPERATOR = "^";

	private final String absoluteFieldPath;
	private Float boost;

	ElasticsearchSimpleQueryStringPredicateBuilderFieldContext(String absoluteFieldPath) {
		this.absoluteFieldPath = absoluteFieldPath;
	}

	@Override
	public void boost(float boost) {
		this.boost = boost;
	}

	public JsonPrimitive build() {
		StringBuilder sb = new StringBuilder( absoluteFieldPath );
		if ( boost != null ) {
			sb.append( BOOST_OPERATOR ).append( boost );
		}
		return new JsonPrimitive( sb.toString() );
	}
}
