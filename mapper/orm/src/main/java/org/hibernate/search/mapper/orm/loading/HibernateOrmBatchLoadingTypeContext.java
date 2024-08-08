/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.search.mapper.orm.loading;

public interface HibernateOrmBatchLoadingTypeContext<E> {

	Class<E> javaClass();

	String jpaEntityName();

}
