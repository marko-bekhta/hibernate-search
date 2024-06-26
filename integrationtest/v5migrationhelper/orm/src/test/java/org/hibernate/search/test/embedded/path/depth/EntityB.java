/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */

package org.hibernate.search.test.embedded.path.depth;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;

import org.hibernate.search.annotations.Field;
import org.hibernate.search.annotations.IndexedEmbedded;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.AssociationInverseSide;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.ObjectPath;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.PropertyValue;

/**
 * @author Davide D'Alto
 */
@Entity
class EntityB {

	@Id
	@GeneratedValue
	public int id;

	@Field
	public String insideThreshold;

	@OneToOne
	@AssociationInverseSide(inversePath = @ObjectPath(@PropertyValue(propertyName = "b")))
	public EntityA a;

	@OneToOne
	@IndexedEmbedded
	public EntityC indexed;

	@OneToOne
	@IndexedEmbedded
	public EntityC skipped;

	public EntityB() {
	}

	public EntityB(EntityC inpath, EntityC indepth) {
		this.indexed = inpath;
		inpath.b = this;

		if ( indepth != null ) {
			this.skipped = indepth;
			indepth.b = this;
		}
	}

}
