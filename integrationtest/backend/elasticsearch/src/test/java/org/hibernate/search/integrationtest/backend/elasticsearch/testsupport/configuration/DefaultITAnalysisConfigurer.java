/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.search.integrationtest.backend.elasticsearch.testsupport.configuration;

import org.hibernate.search.backend.elasticsearch.analysis.ElasticsearchAnalysisConfigurationContext;
import org.hibernate.search.backend.elasticsearch.analysis.ElasticsearchAnalysisConfigurer;
import org.hibernate.search.integrationtest.backend.tck.testsupport.configuration.DefaultAnalysisDefinitions;

public class DefaultITAnalysisConfigurer implements ElasticsearchAnalysisConfigurer {
	@Override
	public void configure(ElasticsearchAnalysisConfigurationContext context) {
		context.analyzer( DefaultAnalysisDefinitions.ANALYZER_STANDARD_ENGLISH.name ).type( "standard" )
				.param( "stopwords", "_english_" );

		context.normalizer( DefaultAnalysisDefinitions.NORMALIZER_LOWERCASE.name ).custom()
				.tokenFilters( "lowercase" );

		context.analyzer( DefaultAnalysisDefinitions.ANALYZER_WHITESPACE.name ).custom()
				.tokenizer( "whitespace" );

		context.analyzer( DefaultAnalysisDefinitions.ANALYZER_WHITESPACE_LOWERCASE.name ).custom()
				.tokenizer( "whitespace" )
				.tokenFilters( "lowercase" );

		String tokenizerName = DefaultAnalysisDefinitions.ANALYZER_NGRAM.name + "_tokenizer";
		context.analyzer( DefaultAnalysisDefinitions.ANALYZER_NGRAM.name ).custom()
				.tokenizer( tokenizerName );

		context.tokenizer( tokenizerName )
				.type( "ngram" )
				.param( "min_gram", 5 )
				.param( "max_gram", 6 );
	}
}
