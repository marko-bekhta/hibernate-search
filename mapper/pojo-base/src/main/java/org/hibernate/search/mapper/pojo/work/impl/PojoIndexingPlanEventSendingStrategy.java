/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.search.mapper.pojo.work.impl;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

import org.hibernate.search.engine.backend.common.spi.MultiEntityOperationExecutionReport;
import org.hibernate.search.engine.backend.work.execution.OperationSubmitter;
import org.hibernate.search.mapper.pojo.work.spi.PojoIndexingQueueEventSendingPlan;
import org.hibernate.search.mapper.pojo.work.spi.PojoWorkSessionContext;

/**
 * A strategy for sending indexing events to a remote processor,
 * which will use a {@link PojoIndexingPlanEventProcessingStrategy}.
 */
public class PojoIndexingPlanEventSendingStrategy implements PojoIndexingPlanStrategy {
	private final PojoIndexingQueueEventSendingPlan sendingPlan;

	public PojoIndexingPlanEventSendingStrategy(PojoIndexingQueueEventSendingPlan sendingPlan) {
		this.sendingPlan = sendingPlan;
	}

	@Override
	public boolean shouldResolveDirtyForDeleteOnly() {
		// When possible, we will resolve dirty entities to reindex in the background process
		// that consumes the events we're sending.
		// For deletes, though, we cannot do that, so we resolve dirty entities directly in-session.
		return true;
	}

	@Override
	public CompletableFuture<MultiEntityOperationExecutionReport> doExecuteAndReport(
			Collection<PojoIndexedTypeIndexingPlan<?, ?>> indexedTypeDelegates,
			PojoLoadingPlanProvider loadingPlanProvider,
			OperationSubmitter operationSubmitter) {
		// No need to go through every single type: the state is global.
		return sendingPlan.sendAndReport( operationSubmitter );
	}

	@Override
	public void doDiscard(Collection<PojoIndexedTypeIndexingPlan<?, ?>> indexedTypeDelegates) {
		// No need to go through every single type: the state is global.
		sendingPlan.discard();
	}

	@Override
	public <I, E> PojoIndexedTypeIndexingPlan<I, E> createIndexedDelegate(PojoWorkIndexedTypeContext<I, E> typeContext,
			PojoWorkSessionContext sessionContext, PojoIndexingPlanImpl root) {
		// Will send indexing events to an external queue.
		return new PojoIndexedTypeIndexingPlan<>( typeContext, sessionContext, root,
				new PojoTypeIndexingPlanEventQueueDelegate<>( typeContext, sessionContext, sendingPlan ) );
	}

	@Override
	public <I, E> PojoContainedTypeIndexingPlan<I, E> createDelegate(PojoWorkContainedTypeContext<I, E> typeContext,
			PojoWorkSessionContext sessionContext, PojoIndexingPlanImpl root) {
		// Will send indexing events to an external queue.
		return new PojoContainedTypeIndexingPlan<>( typeContext, sessionContext, root,
				new PojoTypeIndexingPlanEventQueueDelegate<>( typeContext, sessionContext, sendingPlan ) );
	}
}
