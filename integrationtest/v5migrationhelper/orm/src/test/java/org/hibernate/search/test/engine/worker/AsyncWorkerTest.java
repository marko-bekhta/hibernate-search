/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.search.test.engine.worker;

import java.util.Map;

import org.hibernate.search.backend.lucene.cfg.LuceneBackendSettings;
import org.hibernate.search.backend.lucene.cfg.LuceneIndexSettings;
import org.hibernate.search.engine.cfg.BackendSettings;
import org.hibernate.search.mapper.orm.automaticindexing.session.AutomaticIndexingSynchronizationStrategyNames;
import org.hibernate.search.mapper.orm.cfg.HibernateOrmMapperSettings;

/**
 * @author Emmanuel Bernard
 */
public class AsyncWorkerTest extends WorkerTestCase {

	@Override
	public void configure(Map<String, Object> cfg) {
		cfg.put( HibernateOrmMapperSettings.AUTOMATIC_INDEXING_SYNCHRONIZATION_STRATEGY,
				AutomaticIndexingSynchronizationStrategyNames.ASYNC );
		cfg.put( BackendSettings.backendKey( LuceneBackendSettings.THREAD_POOL_SIZE ), 1 );
		cfg.put( BackendSettings.backendKey( LuceneIndexSettings.INDEXING_QUEUE_COUNT ), 1 );
		cfg.put( BackendSettings.backendKey( LuceneIndexSettings.INDEXING_QUEUE_SIZE ), 10 );
	}

	@Override
	protected boolean isWorkerSync() {
		return false;
	}
}
