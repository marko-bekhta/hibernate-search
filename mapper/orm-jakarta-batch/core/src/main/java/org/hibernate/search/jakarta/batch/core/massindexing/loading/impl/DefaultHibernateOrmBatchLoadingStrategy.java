/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.search.jakarta.batch.core.massindexing.loading.impl;

import org.hibernate.metamodel.mapping.EmbeddableMappingType;
import org.hibernate.metamodel.mapping.EntityIdentifierMapping;
import org.hibernate.search.jakarta.batch.core.massindexing.util.impl.CompositeIdOrder;
import org.hibernate.search.jakarta.batch.core.massindexing.util.impl.IdOrder;
import org.hibernate.search.jakarta.batch.core.massindexing.util.impl.SingularIdOrder;
import org.hibernate.search.mapper.orm.loading.HibernateOrmBatchEntityLoader;
import org.hibernate.search.mapper.orm.loading.HibernateOrmBatchEntityLoadingOptions;
import org.hibernate.search.mapper.orm.loading.HibernateOrmBatchEntitySink;
import org.hibernate.search.mapper.orm.loading.HibernateOrmBatchIdentifierLoader;
import org.hibernate.search.mapper.orm.loading.HibernateOrmBatchIdentifierLoadingOptions;
import org.hibernate.search.mapper.orm.loading.HibernateOrmBatchLoadingStrategy;
import org.hibernate.search.mapper.orm.loading.HibernateOrmBatchLoadingTypeContext;
import org.hibernate.search.mapper.orm.loading.spi.HibernateOrmLoadingTypeContext;

public class DefaultHibernateOrmBatchLoadingStrategy<E, I> implements HibernateOrmBatchLoadingStrategy<E, I> {

	private final IdOrder idOrder;

	public DefaultHibernateOrmBatchLoadingStrategy(HibernateOrmLoadingTypeContext<E> type) {
		EntityIdentifierMapping identifierMapping = type.entityMappingType().getIdentifierMapping();
		if ( identifierMapping.getPartMappingType() instanceof EmbeddableMappingType ) {
			idOrder = new CompositeIdOrder<>( type );
		}
		else {
			idOrder = new SingularIdOrder<>( type );
		}
	}

	@Override
	public HibernateOrmBatchIdentifierLoader createIdentifierLoader(HibernateOrmBatchLoadingTypeContext<E> typeContext,
			HibernateOrmBatchIdentifierLoadingOptions options) {
		return new DefaultHibernateOrmBatchIdentifierLoader<>( typeContext, options, idOrder );
	}

	@Override
	public HibernateOrmBatchEntityLoader createEntityLoader(HibernateOrmBatchLoadingTypeContext<E> typeContext,
			HibernateOrmBatchEntitySink<E> sink, HibernateOrmBatchEntityLoadingOptions options) {
		return new DefaultHibernateOrmBatchEntityLoader<>( typeContext, sink, options );
	}
}
