/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.backend.elasticsearch.dialect.model.impl;

import org.hibernate.search.backend.elasticsearch.types.dsl.provider.impl.Elasticsearch6IndexFieldTypeFactoryContextProvider;
import org.hibernate.search.backend.elasticsearch.types.dsl.provider.impl.ElasticsearchIndexFieldTypeFactoryContextProvider;

import com.google.gson.Gson;

/**
 * The model dialect for Elasticsearch 6.0 to 6.7.
 */
public class Elasticsearch6ModelDialect extends Elasticsearch7ModelDialect implements ElasticsearchModelDialect {

	@Override
	public ElasticsearchIndexFieldTypeFactoryContextProvider createIndexTypeFieldFactoryContextProvider(
			Gson userFacingGson) {
		// Necessary because the date formats are handled differently in ES7
		return new Elasticsearch6IndexFieldTypeFactoryContextProvider( userFacingGson );
	}
}
