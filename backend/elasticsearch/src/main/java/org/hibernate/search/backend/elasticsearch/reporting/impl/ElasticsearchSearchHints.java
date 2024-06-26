/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.search.backend.elasticsearch.reporting.impl;

import java.lang.invoke.MethodHandles;

import org.hibernate.search.engine.backend.reporting.spi.BackendSearchHints;
import org.hibernate.search.util.common.logging.impl.MessageConstants;

import org.jboss.logging.Messages;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageBundle;

@MessageBundle(projectCode = MessageConstants.PROJECT_CODE)
public interface ElasticsearchSearchHints extends BackendSearchHints {

	ElasticsearchSearchHints INSTANCE = Messages.getBundle( MethodHandles.lookup(), ElasticsearchSearchHints.class );

	@Message(
			value = "A JSON hit projection represents a root hit object and adding it as a part of the nested object projection might produce misleading results.")
	String jsonHitProjectionNestingNotSupportedHint();

	@Message(
			value = "A source projection represents a root source object and adding it as a part of the nested object projection might produce misleading results.")
	String sourceProjectionNestingNotSupportedHint();
}
