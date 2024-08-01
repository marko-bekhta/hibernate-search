/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.search.mapper.pojo.massindexing.impl;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.search.mapper.pojo.logging.impl.Log;
import org.hibernate.search.mapper.pojo.massindexing.MassIndexingEntityFailureContext;
import org.hibernate.search.mapper.pojo.massindexing.MassIndexingFailureContext;
import org.hibernate.search.mapper.pojo.massindexing.MassIndexingFailureHandler;
import org.hibernate.search.util.common.impl.Closer;
import org.hibernate.search.util.common.impl.SuppressingCloser;
import org.hibernate.search.util.common.logging.impl.LoggerFactory;

public class PojoMassIndexingFailSafeFailureHandlerWrapper implements MassIndexingFailureHandler {

	private static final Log log = LoggerFactory.make( Log.class, MethodHandles.lookup() );

	private final MassIndexingFailureHandler delegate;

	private final List<Runnable> callbacks = new ArrayList<>();

	public PojoMassIndexingFailSafeFailureHandlerWrapper(MassIndexingFailureHandler delegate) {
		this.delegate = delegate;
	}

	@Override
	public void handle(MassIndexingFailureContext context) {
		try {
			delegate.handle( context );
		}
		catch (Throwable t) {
			log.failureInMassIndexingFailureHandler( t );
			executeCallbacks();
		}
	}

	@Override
	public void handle(MassIndexingEntityFailureContext context) {
		try {
			delegate.handle( context );
		}
		catch (Throwable t) {
			log.failureInMassIndexingFailureHandler( t );
			executeCallbacks();
		}
	}

	@Override
	public long failureFloodingThreshold() {
		try {
			return delegate.failureFloodingThreshold();
		}
		catch (Throwable t) {
			log.failureInMassIndexingFailureHandler( t );
			return MassIndexingFailureHandler.super.failureFloodingThreshold();
		}
	}

	public void addCallback(Runnable callback) {
		callbacks.add( callback );
	}

	private void executeCallbacks() {
		try ( Closer<RuntimeException> closer = new Closer<>() ) {
			closer.pushAll( Runnable::run, callbacks );
		}
	}
}
