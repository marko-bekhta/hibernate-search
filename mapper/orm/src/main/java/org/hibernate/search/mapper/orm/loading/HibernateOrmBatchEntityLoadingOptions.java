/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.search.mapper.orm.loading;

import java.util.List;

import org.hibernate.CacheMode;
import org.hibernate.search.mapper.pojo.loading.MassEntityLoader;
import org.hibernate.search.mapper.pojo.loading.MassIdentifierLoader;
import org.hibernate.search.util.common.annotation.Incubating;

@Incubating
public interface HibernateOrmBatchEntityLoadingOptions {

	/**
	 * @return How many entities to load and index in each batch.
	 * Defines the maximum expected size of each list of IDs
	 * loaded by {@link MassIdentifierLoader#loadNext()}
	 * and passed to {@link MassEntityLoader#load(List)}.
	 */
	int batchSize();

	CacheMode cacheMode();

	/**
	 * Search will add a {@link org.hibernate.StatelessSession} to the context by default.
	 */
	<T> T context(Class<T> contextType);
}
