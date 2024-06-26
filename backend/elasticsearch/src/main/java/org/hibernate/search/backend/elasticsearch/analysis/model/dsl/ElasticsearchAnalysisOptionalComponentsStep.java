/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.search.backend.elasticsearch.analysis.model.dsl;

import org.hibernate.search.backend.elasticsearch.analysis.ElasticsearchAnalysisConfigurationContext;

/**
 * The step in an analyzer/normalizer definition
 * where optional components such as char filters or token filters can be added.
 */
public interface ElasticsearchAnalysisOptionalComponentsStep {

	/**
	 * Set the char filters that the normalizer will use.
	 *
	 * @param names The name of each char filters to use, in order.
	 * There must be a corresponding char filter definition on the Elasticsearch server.
	 * This can be achieved by defining the char filter
	 * {@link ElasticsearchAnalysisConfigurationContext#charFilter(String) from Hibernate Search},
	 * by configuring the Elasticsearch server directly, or by using built-in tokenizers.
	 * @return {@code this}, for method chaining.
	 */
	ElasticsearchAnalysisOptionalComponentsStep charFilters(String... names);

	/**
	 * Set the token filters that the normalizer will use.
	 *
	 * @param names The name of the token filters to use, in order.
	 * There must be a corresponding token filter definition on the Elasticsearch server.
	 * This can be achieved by defining the token filter
	 * {@link ElasticsearchAnalysisConfigurationContext#tokenFilter(String) from Hibernate Search},
	 * by configuring the Elasticsearch server, or by using built-in tokenizers.
	 * @return {@code this}, for method chaining.
	 */
	ElasticsearchAnalysisOptionalComponentsStep tokenFilters(String... names);

}
