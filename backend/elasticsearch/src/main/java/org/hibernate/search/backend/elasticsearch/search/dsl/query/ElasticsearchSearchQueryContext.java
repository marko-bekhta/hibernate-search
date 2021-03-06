/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.backend.elasticsearch.search.dsl.query;

import org.hibernate.search.backend.elasticsearch.search.dsl.sort.ElasticsearchSearchSortContainerContext;
import org.hibernate.search.backend.elasticsearch.search.query.ElasticsearchSearchFetchable;
import org.hibernate.search.backend.elasticsearch.search.query.ElasticsearchSearchQuery;
import org.hibernate.search.engine.search.dsl.query.SearchQueryContext;

public interface ElasticsearchSearchQueryContext<H>
		extends SearchQueryContext<ElasticsearchSearchQueryContext<H>, H, ElasticsearchSearchSortContainerContext>,
				ElasticsearchSearchFetchable<H> {

	@Override
	ElasticsearchSearchQuery<H> toQuery();
}
