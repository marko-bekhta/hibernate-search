/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.search.util.impl.integrationtest.common.stub.backend.index.impl;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.hibernate.search.engine.backend.Backend;
import org.hibernate.search.engine.backend.index.spi.IndexManagerBuilder;
import org.hibernate.search.engine.backend.mapping.spi.BackendMapperContext;
import org.hibernate.search.engine.backend.spi.BackendBuildContext;
import org.hibernate.search.engine.backend.spi.BackendImplementor;
import org.hibernate.search.engine.backend.spi.BackendStartContext;
import org.hibernate.search.engine.cfg.ConfigurationPropertySource;
import org.hibernate.search.engine.common.timing.spi.TimingSource;
import org.hibernate.search.util.common.AssertionFailure;
import org.hibernate.search.util.common.reporting.EventContext;
import org.hibernate.search.util.impl.integrationtest.common.stub.backend.BackendMappingHandle;
import org.hibernate.search.util.impl.integrationtest.common.stub.backend.StubBackendBehavior;

public class StubBackend implements BackendImplementor, Backend {

	private final Optional<String> backendName;
	private final EventContext eventContext;
	private final StubBackendBehavior behavior;
	private final TimingSource timingSource;
	private final ConfigurationPropertySource propertySource;

	StubBackend(EventContext eventContext, BackendBuildContext context, StubBackendBehavior behavior,
			CompletionStage<BackendMappingHandle> mappingHandlePromise,
			TimingSource timingSource, ConfigurationPropertySource propertySource) {
		this.backendName = context.backendName();
		this.eventContext = eventContext;
		this.behavior = behavior;
		this.timingSource = timingSource;
		this.propertySource = propertySource;
		behavior.onCreateBackend( new StubBackendBuildContext( context, propertySource ), mappingHandlePromise );
	}

	@Override
	public String toString() {
		return StubBackend.class.getSimpleName() + "[" + eventContext + "]";
	}

	public EventContext eventContext() {
		return eventContext;
	}

	@Override
	public void start(BackendStartContext context) {
		// Nothing to do
	}

	@Override
	public CompletableFuture<?> preStop() {
		// Nothing to do
		return CompletableFuture.completedFuture( null );
	}

	@Override
	public void stop() {
		behavior.onStopBackend();
	}

	@Override
	public <T> T unwrap(Class<T> clazz) {
		throw new AssertionFailure( getClass().getName() + " cannot be unwrapped" );
	}

	@Override
	public Optional<String> name() {
		return backendName;
	}

	@Override
	public Backend toAPI() {
		return this;
	}

	public StubBackendBehavior getBehavior() {
		return behavior;
	}

	public TimingSource timingSource() {
		return timingSource;
	}

	@Override
	public IndexManagerBuilder createIndexManagerBuilder(String indexName, String mappedTypeName,
			BackendBuildContext context, BackendMapperContext backendMapperContext,
			ConfigurationPropertySource propertySource) {
		this.behavior.onCreateIndex( new StubIndexCreateContext( indexName, propertySource ) );
		return new StubIndexManagerBuilder( this, indexName, mappedTypeName );
	}
}
