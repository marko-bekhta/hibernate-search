/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.search.engine.search.reference.scorefunction;

import org.hibernate.search.engine.search.reference.FieldReference;
import org.hibernate.search.util.common.annotation.Incubating;

@Incubating
public interface ScoreFunctionFieldReference<SR> extends FieldReference<SR> {
}
