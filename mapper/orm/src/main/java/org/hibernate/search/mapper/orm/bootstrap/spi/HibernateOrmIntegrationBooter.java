/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.search.mapper.orm.bootstrap.spi;

import java.util.function.BiConsumer;

import org.hibernate.accessor.HibernateAccessorFactory;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.spi.BootstrapContext;
import org.hibernate.search.mapper.orm.bootstrap.impl.HibernateOrmIntegrationBooterImpl;

public interface HibernateOrmIntegrationBooter {

	static HibernateOrmIntegrationBooter.Builder builder(Metadata metadata, BootstrapContext bootstrapContext) {
		return new HibernateOrmIntegrationBooterImpl.BuilderImpl( metadata, bootstrapContext );
	}

	interface Builder {
		Builder accessorFactory(HibernateAccessorFactory accessorFactory);

		HibernateOrmIntegrationBooter build();
	}

	void preBoot(BiConsumer<String, Object> propertyCollector);

}
