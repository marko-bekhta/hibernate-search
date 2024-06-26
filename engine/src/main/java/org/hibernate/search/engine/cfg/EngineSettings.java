/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.search.engine.cfg;

import org.hibernate.search.engine.environment.bean.BeanReference;
import org.hibernate.search.engine.reporting.FailureHandler;
import org.hibernate.search.engine.reporting.impl.LogFailureHandler;

/**
 * Configuration properties for the Hibernate Search engine.
 */
public final class EngineSettings {

	private EngineSettings() {
	}

	/**
	 * The prefix expected for the key of every Hibernate Search configuration property.
	 */
	public static final String PREFIX = "hibernate.search.";

	/**
	 * The root property for properties of the default backend, e.g. "hibernate.search.backend.type = elasticsearch".
	 */
	public static final String BACKEND = PREFIX + Radicals.BACKEND;

	/**
	 * The root property for properties of named backends, e.g. "hibernate.search.backends.myBackend.type = elasticsearch".
	 */
	public static final String BACKENDS = PREFIX + Radicals.BACKENDS;

	/**
	 * How to report the results of configuration property checking.
	 * <p>
	 * Configuration property checking will detect an configuration property that is never used,
	 * which might indicate a configuration issue.
	 * <p>
	 * Expects a {@link ConfigurationPropertyCheckingStrategyName} value,
	 * or a String representation of such value.
	 * <p>
	 * Defaults to {@link Defaults#CONFIGURATION_PROPERTY_CHECKING_STRATEGY}.
	 */
	public static final String CONFIGURATION_PROPERTY_CHECKING_STRATEGY =
			PREFIX + Radicals.CONFIGURATION_PROPERTY_CHECKING_STRATEGY;

	/**
	 * The {@link FailureHandler} instance that should be notified
	 * of any failure occurring in a background process
	 * (mainly index operations).
	 * <p>
	 * Expects a reference to a bean of type {@link FailureHandler}.
	 * <p>
	 * Defaults to {@link Defaults#BACKGROUND_FAILURE_HANDLER}, a logging handler.
	 */
	public static final String BACKGROUND_FAILURE_HANDLER = PREFIX + Radicals.BACKGROUND_FAILURE_HANDLER;

	/**
	 * Configuration property keys without the {@link #PREFIX prefix}.
	 */
	public static class Radicals {

		private Radicals() {
		}

		public static final String BACKEND = "backend";
		public static final String BACKENDS = "backends";
		public static final String CONFIGURATION_PROPERTY_CHECKING_STRATEGY = "configuration_property_checking.strategy";
		public static final String BACKGROUND_FAILURE_HANDLER = "background_failure_handler";
	}

	/**
	 * Default values for the different settings if no values are given.
	 */
	public static final class Defaults {

		public static final ConfigurationPropertyCheckingStrategyName CONFIGURATION_PROPERTY_CHECKING_STRATEGY =
				ConfigurationPropertyCheckingStrategyName.WARN;

		public static final BeanReference<? extends FailureHandler> BACKGROUND_FAILURE_HANDLER =
				BeanReference.of( FailureHandler.class, LogFailureHandler.NAME );

		private Defaults() {
		}

	}
}
