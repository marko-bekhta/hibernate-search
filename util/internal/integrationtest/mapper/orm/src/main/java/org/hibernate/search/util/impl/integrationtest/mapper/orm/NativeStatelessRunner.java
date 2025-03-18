/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.search.util.impl.integrationtest.mapper.orm;

import org.hibernate.SessionFactory;
import org.hibernate.StatelessSession;
import org.hibernate.Transaction;
import org.hibernate.search.util.impl.test.function.ThrowingBiFunction;
import org.hibernate.search.util.impl.test.function.ThrowingFunction;

class NativeStatelessRunner implements PersistenceRunner<StatelessSession, Transaction> {
	private final SessionFactory sessionFactory;
	private final Object tenantId;

	NativeStatelessRunner(SessionFactory sessionFactory, Object tenantId) {
		this.sessionFactory = sessionFactory;
		this.tenantId = tenantId;
	}

	@Override
	public <R, E extends Throwable> R applyNoTransaction(ThrowingFunction<? super StatelessSession, R, E> action) throws E {
		if ( tenantId != null ) {
			try ( StatelessSession session =
					sessionFactory.withStatelessOptions().tenantIdentifier( tenantId ).openStatelessSession() ) {
				return action.apply( session );
			}
		}
		else {
			try ( StatelessSession session = sessionFactory.openStatelessSession() ) {
				return action.apply( session );
			}
		}
	}

	@Override
	public <R, E extends Throwable> R applyInTransaction(
			ThrowingBiFunction<? super StatelessSession, ? super Transaction, R, E> action)
			throws E {
		return applyNoTransaction( session ->
		//CHECKSTYLE:OFF: RegexpSinglelineJava - cannot use static import as that would clash with method of this class
		OrmUtils.applyInTransaction( session, tx -> action.apply( session, tx ) )
		//CHECKSTYLE:ON
		);
	}
}
