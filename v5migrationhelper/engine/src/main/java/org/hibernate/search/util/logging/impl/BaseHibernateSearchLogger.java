/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.search.util.logging.impl;

import org.jboss.logging.BasicLogger;
import org.jboss.logging.annotations.MessageLogger;

/**
 * Parent interface for all Loggers of the various compilations units
 * of Hibernate Search.
 * Sharing the same messages across all units should be avoided but
 * we don't want to strictly enforce this rule so to maintain historical
 * stability of logging messages defined before this split, or to allow
 * the occasional reasonable exception.
 *
 * The primary reason to avoid this is to encourage each module to define
 * more specific error messages and codes, but also to consider is that
 * blindly inheriting all messages from another module causes the Logger
 * annotation processor to generate a significant amount of code, which
 * is mostly unnecessary as most of those codes are unlikely to be actually
 * reused by other modules.
 *
 * @author Sanne Grinovero
 */
@MessageLogger(projectCode = "HSEARCH")
public interface BaseHibernateSearchLogger extends BasicLogger {

}
