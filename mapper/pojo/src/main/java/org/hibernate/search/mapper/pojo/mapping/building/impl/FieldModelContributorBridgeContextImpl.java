/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.mapper.pojo.mapping.building.impl;

import org.hibernate.search.engine.backend.types.dsl.StandardIndexFieldTypeContext;
import org.hibernate.search.mapper.pojo.bridge.ValueBridge;
import org.hibernate.search.mapper.pojo.mapping.building.spi.FieldModelContributorBridgeContext;

public class FieldModelContributorBridgeContextImpl<F> implements FieldModelContributorBridgeContext {

	private final ValueBridge<?, F> bridge;
	private final StandardIndexFieldTypeContext<?, ? super F> fieldTypeContext;

	public FieldModelContributorBridgeContextImpl(ValueBridge<?, F> bridge, StandardIndexFieldTypeContext<?, ? super F> fieldTypeContext) {
		this.bridge = bridge;
		this.fieldTypeContext = fieldTypeContext;
	}

	@Override
	public void indexNullAs(String value) {
		fieldTypeContext.indexNullAs( bridge.parse( value ) );
	}
}
