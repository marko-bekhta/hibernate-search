/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.search.util.common.annotation;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.CLASS;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Marks the annotated element as incubating.
 * <p>
 * Incubating features are still under active development.
 * The usual <a href="https://hibernate.org/community/compatibility-policy/">compatibility policy</a> does not apply:
 * the contract of incubating elements (e.g. types, methods etc.)
 * may be altered in a backward-incompatible way -- or even removed -- in subsequent releases.
 * <p>
 * You are encouraged to use incubating features so the development team can get feedback and improve them,
 * but you should be prepared to update code which relies on them as needed.
 *
 * @author Gunnar Morling
 */
@Documented
@Target({ TYPE, METHOD, FIELD })
@Retention(CLASS)
public @interface Incubating {
}
