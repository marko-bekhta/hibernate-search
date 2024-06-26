/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.search.documentation.mapper.orm.binding.fieldtypes.dsl.projectionconverter;

import org.hibernate.search.documentation.testsupport.data.ISBN;
import org.hibernate.search.engine.backend.document.DocumentElement;
import org.hibernate.search.engine.backend.document.IndexFieldReference;
import org.hibernate.search.engine.backend.types.IndexFieldType;
import org.hibernate.search.engine.backend.types.Projectable;
import org.hibernate.search.mapper.pojo.bridge.PropertyBridge;
import org.hibernate.search.mapper.pojo.bridge.binding.PropertyBindingContext;
import org.hibernate.search.mapper.pojo.bridge.mapping.programmatic.PropertyBinder;
import org.hibernate.search.mapper.pojo.bridge.runtime.PropertyBridgeWriteContext;

public class ISBNBinder implements PropertyBinder {

	@Override
	public void bind(PropertyBindingContext context) {
		context.dependencies().useRootOnly();

		//tag::include[]
		IndexFieldType<String> type = context.typeFactory()
				.asString() // <1>
				.projectable( Projectable.YES )
				.projectionConverter( // <2>
						ISBN.class, // <3>
						(value, convertContext) -> ISBN.parse( value ) // <4>
				)
				.toIndexFieldType();
		//end::include[]

		context.bridge( ISBN.class, new Bridge(
				context.indexSchemaElement()
						.field( "isbn", type )
						.toReference()
		) );
	}

	private static class Bridge implements PropertyBridge<ISBN> {
		private final IndexFieldReference<String> isbnField;

		private Bridge(IndexFieldReference<String> isbnField) {
			this.isbnField = isbnField;
		}

		@Override
		public void write(DocumentElement target, ISBN bridgedElement,
				PropertyBridgeWriteContext context) {
			target.addValue( isbnField, bridgedElement.getStringValue() );
		}
	}
}
