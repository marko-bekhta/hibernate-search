/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.search.mapper.orm.loading;

import java.util.Map;

public interface HibernateOrmBatchReindexCondition {
	String conditionString();

	Map<String, Object> params();
}
