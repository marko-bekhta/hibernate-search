/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.search.engine.reporting.impl;

import static org.hibernate.search.engine.logging.impl.EngineMiscLog.INSTANCE;

import java.util.List;

import org.hibernate.search.engine.common.EntityReference;
import org.hibernate.search.engine.reporting.EntityIndexingFailureContext;
import org.hibernate.search.engine.reporting.FailureContext;
import org.hibernate.search.engine.reporting.FailureHandler;

/**
 * @author Amin Mohammed-Coleman
 * @author Sanne Grinovero
 * @since 3.2
 */
public class LogFailureHandler implements FailureHandler {

	private static final int FAILURE_FLOODING_THRESHOLD = 100;

	public static final String NAME = "log";

	@Override
	public void handle(FailureContext context) {
		INSTANCE.exceptionOccurred( formatMessage( context ).toString(), context.throwable() );
	}

	@Override
	public void handle(EntityIndexingFailureContext context) {
		INSTANCE.exceptionOccurred( formatMessage( context ).toString(), context.throwable() );
	}

	@Override
	public long failureFloodingThreshold() {
		return FAILURE_FLOODING_THRESHOLD;
	}

	private StringBuilder formatMessage(FailureContext context) {
		final Throwable throwable = context.throwable();
		final Object failingOperation = context.failingOperation();

		final StringBuilder messageBuilder = new StringBuilder();

		messageBuilder.append( "Exception occurred " )
				.append( throwable )
				.append( "\n" );
		messageBuilder.append( "Failing operation:\n" );
		messageBuilder.append( failingOperation );
		messageBuilder.append( "\n" );

		return messageBuilder;
	}

	private StringBuilder formatMessage(EntityIndexingFailureContext context) {
		final List<EntityReference> entityReferences = context.failingEntityReferences();

		final StringBuilder messageBuilder = formatMessage( (FailureContext) context );

		if ( !entityReferences.isEmpty() ) {
			messageBuilder.append( "Entities that could not be indexed correctly:\n" );
			for ( EntityReference entityReference : entityReferences ) {
				messageBuilder.append( entityReference );
				messageBuilder.append( " " );
			}
		}

		return messageBuilder;
	}

}
