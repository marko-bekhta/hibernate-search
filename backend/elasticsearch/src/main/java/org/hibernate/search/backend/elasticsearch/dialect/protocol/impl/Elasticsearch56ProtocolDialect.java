/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.backend.elasticsearch.dialect.protocol.impl;

/**
 * The protocol dialect for Elasticsearch 5.6.
 */
public class Elasticsearch56ProtocolDialect extends Elasticsearch60ProtocolDialect
		implements ElasticsearchProtocolDialect {

	// Nothing specific so far. This may change if we add new features that work better in later versions.

}
