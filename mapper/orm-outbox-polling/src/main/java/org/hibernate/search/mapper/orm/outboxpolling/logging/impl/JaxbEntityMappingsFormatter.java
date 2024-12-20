/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.search.mapper.orm.outboxpolling.logging.impl;

import org.hibernate.boot.jaxb.mapping.spi.JaxbEntityMappingsImpl;
import org.hibernate.search.mapper.orm.outboxpolling.mapping.impl.JaxbMappingHelper;

/**
 * Used with JBoss Logging's {@link org.jboss.logging.annotations.FormatWith} to format
 * {@link JaxbEntityMappingsImpl} objects using marshaling.
 */
public final class JaxbEntityMappingsFormatter {

	private final JaxbEntityMappingsImpl mappings;

	public JaxbEntityMappingsFormatter(JaxbEntityMappingsImpl mappings) {
		this.mappings = mappings;
	}

	@Override
	public String toString() {
		return JaxbMappingHelper.marshall( mappings );
	}
}
