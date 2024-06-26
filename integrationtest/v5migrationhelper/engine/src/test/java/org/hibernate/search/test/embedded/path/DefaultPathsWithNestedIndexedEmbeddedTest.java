/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.search.test.embedded.path;

import org.hibernate.search.annotations.Analyze;
import org.hibernate.search.annotations.DocumentId;
import org.hibernate.search.annotations.Field;
import org.hibernate.search.annotations.Indexed;
import org.hibernate.search.annotations.IndexedEmbedded;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.AssociationInverseSide;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.ObjectPath;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.PropertyValue;
import org.hibernate.search.testsupport.TestForIssue;
import org.hibernate.search.testsupport.junit.SearchFactoryHolder;
import org.hibernate.search.testsupport.junit.SearchITHelper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Test the behavior when an {@literal @IndexedEmbedded} with default paths (i.e. "include everything")
 * has another, nested {@literal @IndexedEmbedded} with non-default paths (i.e. "include those paths only").
 *
 * @author Yoann Rodiere
 */
@TestForIssue(jiraKey = "HSEARCH-2547")
class DefaultPathsWithNestedIndexedEmbeddedTest {

	@RegisterExtension
	public final SearchFactoryHolder sfHolder = new SearchFactoryHolder( A.class, B.class, C.class );

	private final SearchITHelper helper = new SearchITHelper( sfHolder );

	@Test
	void testIndexAndSearch() {
		A a = new A();
		a.id = 0L;
		a.foo = "someValue";
		B b = new B();
		b.a = a;
		a.b = b;
		b.id = 1L;
		C c = new C();
		c.b = b;
		b.c = c;
		c.id = 2L;
		helper.add( c );

		helper.assertThatQuery( "b.a.foo", a.foo )
				.from( C.class )
				.hasResultSize( 1 );
	}

	@Indexed
	private static class A {
		@DocumentId
		private Long id;

		@Field(analyze = Analyze.NO)
		private String foo;

		@AssociationInverseSide(inversePath = @ObjectPath(@PropertyValue(propertyName = "a")))
		private B b;
	}

	private static class B {
		@DocumentId
		private Long id;

		@IndexedEmbedded(includePaths = "foo") // Include only "a.foo"
		private A a;

		@AssociationInverseSide(inversePath = @ObjectPath(@PropertyValue(propertyName = "b")))
		private C c;
	}

	@Indexed
	private static class C {
		@DocumentId
		private Long id;

		@IndexedEmbedded // Include every field of "b"
		private B b;
	}
}
