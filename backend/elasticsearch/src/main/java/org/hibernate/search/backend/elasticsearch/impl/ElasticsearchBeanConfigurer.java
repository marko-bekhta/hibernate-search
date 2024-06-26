/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.search.backend.elasticsearch.impl;

import org.hibernate.search.backend.elasticsearch.cfg.ElasticsearchBackendSettings;
import org.hibernate.search.backend.elasticsearch.index.layout.IndexLayoutStrategy;
import org.hibernate.search.backend.elasticsearch.index.layout.impl.NoAliasIndexLayoutStrategy;
import org.hibernate.search.backend.elasticsearch.index.layout.impl.SimpleIndexLayoutStrategy;
import org.hibernate.search.backend.elasticsearch.resources.impl.DefaultElasticsearchWorkExecutorProvider;
import org.hibernate.search.backend.elasticsearch.work.spi.ElasticsearchWorkExecutorProvider;
import org.hibernate.search.engine.backend.spi.BackendFactory;
import org.hibernate.search.engine.environment.bean.BeanHolder;
import org.hibernate.search.engine.environment.bean.spi.BeanConfigurationContext;
import org.hibernate.search.engine.environment.bean.spi.BeanConfigurer;

public class ElasticsearchBeanConfigurer implements BeanConfigurer {
	@Override
	public void configure(BeanConfigurationContext context) {
		context.define(
				BackendFactory.class, ElasticsearchBackendSettings.TYPE_NAME,
				beanResolver -> BeanHolder.of( new ElasticsearchBackendFactory() )
		);
		context.define(
				IndexLayoutStrategy.class, SimpleIndexLayoutStrategy.NAME,
				beanResolver -> BeanHolder.of( new SimpleIndexLayoutStrategy() )
		);
		context.define(
				IndexLayoutStrategy.class, NoAliasIndexLayoutStrategy.NAME,
				beanResolver -> BeanHolder.of( new NoAliasIndexLayoutStrategy() )
		);
		context.define(
				ElasticsearchWorkExecutorProvider.class, DefaultElasticsearchWorkExecutorProvider.DEFAULT_BEAN_NAME,
				beanResolver -> BeanHolder.of( new DefaultElasticsearchWorkExecutorProvider() )
		);
	}
}
