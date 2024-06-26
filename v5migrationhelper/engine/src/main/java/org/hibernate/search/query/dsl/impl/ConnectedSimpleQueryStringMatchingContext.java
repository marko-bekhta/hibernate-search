/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */

package org.hibernate.search.query.dsl.impl;

import org.hibernate.search.query.dsl.SimpleQueryStringDefinitionTermination;
import org.hibernate.search.query.dsl.SimpleQueryStringMatchingContext;
import org.hibernate.search.query.dsl.SimpleQueryStringTermination;

/**
 * @author Guillaume Smet
 */
public class ConnectedSimpleQueryStringMatchingContext implements SimpleQueryStringMatchingContext {

	private final QueryBuildingContext queryContext;
	private final QueryCustomizer queryCustomizer;

	private final FieldsContext fieldsContext;

	private boolean withAndAsDefaultOperator = false;

	public ConnectedSimpleQueryStringMatchingContext(String field, QueryCustomizer queryCustomizer,
			QueryBuildingContext queryContext) {
		this.queryContext = queryContext;
		this.queryCustomizer = queryCustomizer;
		this.fieldsContext = new FieldsContext( new String[] { field }, queryContext );
	}

	public ConnectedSimpleQueryStringMatchingContext(String[] fields, QueryCustomizer queryCustomizer,
			QueryBuildingContext queryContext) {
		this.queryContext = queryContext;
		this.queryCustomizer = queryCustomizer;
		this.fieldsContext = new FieldsContext( fields, queryContext );
	}

	@Override
	public SimpleQueryStringMatchingContext andField(String field) {
		fieldsContext.add( field );
		return this;
	}

	@Override
	public SimpleQueryStringMatchingContext andFields(String... fields) {
		fieldsContext.addAll( fields );
		return this;
	}

	@Override
	public SimpleQueryStringMatchingContext boostedTo(float boost) {
		fieldsContext.boostedTo( boost );
		return this;
	}

	@Override
	public SimpleQueryStringTermination matching(String simpleQueryString) {
		return new ConnectedMultiFieldsSimpleQueryStringQueryBuilder( queryContext, queryCustomizer, fieldsContext,
				simpleQueryString, withAndAsDefaultOperator );
	}

	@Override
	public SimpleQueryStringDefinitionTermination withAndAsDefaultOperator() {
		withAndAsDefaultOperator = true;
		return this;
	}

}
