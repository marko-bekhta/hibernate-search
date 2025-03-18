/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.search.mapper.orm.massindexing.impl;

import org.hibernate.SharedSessionContract;
import org.hibernate.search.mapper.pojo.massindexing.spi.PojoMassIndexingSessionContext;

public interface HibernateOrmMassIndexingSessionContext extends PojoMassIndexingSessionContext {

	SharedSessionContract session();

}
