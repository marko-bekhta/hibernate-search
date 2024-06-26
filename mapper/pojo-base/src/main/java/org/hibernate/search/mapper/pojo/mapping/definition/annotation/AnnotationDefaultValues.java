/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.search.mapper.pojo.mapping.definition.annotation;

public final class AnnotationDefaultValues {

	/**
	 * This special value is reserved to mark the default of the indexNullAs option.
	 * The default behavior is to not index the null value.
	 */
	public static final String DO_NOT_INDEX_NULL = "__HibernateSearch_indexNullAs_default";

	/**
	 * This extreme value is both invalid and very unlikely to be used.
	 * So we use it to mark that the user has not set the value.
	 */
	public static final int DEFAULT_DECIMAL_SCALE = Integer.MAX_VALUE;

	/**
	 * This extreme value is both invalid and very unlikely to be used.
	 * So we use it to mark that the user has not set the value.
	 */
	public static final int DEFAULT_EF_CONSTRUCTION = Integer.MIN_VALUE;

	/**
	 * This extreme value is both invalid and very unlikely to be used.
	 * So we use it to mark that the user has not set the value.
	 */
	public static final int DEFAULT_M = Integer.MIN_VALUE;

	/**
	 * This extreme value is both invalid and very unlikely to be used.
	 * So we use it to mark that the user has not set the value.
	 */
	public static final int DEFAULT_DIMENSION = Integer.MIN_VALUE;


	private AnnotationDefaultValues() {
	}
}
