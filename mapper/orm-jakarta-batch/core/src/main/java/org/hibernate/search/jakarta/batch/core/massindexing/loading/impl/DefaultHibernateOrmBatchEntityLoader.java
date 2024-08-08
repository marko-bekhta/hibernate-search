/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.search.jakarta.batch.core.massindexing.loading.impl;

import java.util.List;

import jakarta.persistence.LockModeType;

import org.hibernate.FlushMode;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.hibernate.search.mapper.orm.loading.HibernateOrmBatchEntityLoader;
import org.hibernate.search.mapper.orm.loading.HibernateOrmBatchEntityLoadingOptions;
import org.hibernate.search.mapper.orm.loading.HibernateOrmBatchEntitySink;
import org.hibernate.search.mapper.orm.loading.HibernateOrmBatchLoadingTypeContext;

public class DefaultHibernateOrmBatchEntityLoader<E> implements HibernateOrmBatchEntityLoader {
	private static final String ID_PARAMETER_NAME = "ids";

	private final HibernateOrmBatchEntitySink<E> sink;
	private final Query<E> query;

	public DefaultHibernateOrmBatchEntityLoader(HibernateOrmBatchLoadingTypeContext<E> typeContext,
			HibernateOrmBatchEntitySink<E> sink, HibernateOrmBatchEntityLoadingOptions options) {
		this.sink = sink;

		StringBuilder query = new StringBuilder();
		query.append( "select e from " )
				.append( typeContext.jpaEntityName() )
				.append( " e where e." )
				.append( typeContext.uniquePropertyName() )
				.append( " in(:" )
				.append( ID_PARAMETER_NAME )
				.append( ")" );

		this.query = options.context( Session.class ).createQuery( query.toString(), typeContext.javaClass() )
				.setReadOnly( true )
				.setCacheable( false )
				.setLockMode( LockModeType.NONE )
				.setCacheMode( options.cacheMode() )
				.setHibernateFlushMode( FlushMode.MANUAL )
				.setFetchSize( options.batchSize() );
	}

	@Override
	public void close() {
	}

	@Override
	public void load(List<Object> identifiers) {
		sink.accept( query.setParameter( ID_PARAMETER_NAME, identifiers ).list() );
	}

}
