/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.search.mapper.pojo.model.spi;

import org.hibernate.accessor.HibernateAccessorFactory;

public record AccessorFactoriesContext(
		HibernateAccessorFactory accessorFactory,
		HibernateAccessorFactory annotationAccessorFactory) {
}
