/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.search.integrationtest.mapper.orm.automaticindexing.array;

import java.sql.Types;
import java.util.Arrays;
import java.util.List;

import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OrderColumn;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.GenericField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;
import org.hibernate.search.util.impl.test.annotation.TestForIssue;

@TestForIssue(jiraKey = "HSEARCH-3997")
class AutomaticIndexingDoubleArrayIT
		extends AbstractAutomaticIndexingArrayIT<
				AutomaticIndexingDoubleArrayIT.IndexedEntity,
				double[],
				Double> {

	public AutomaticIndexingDoubleArrayIT() {
		super( new DoubleArrayModelPrimitives() );
	}

	private static class DoubleArrayModelPrimitives
			implements ArrayModelPrimitives<IndexedEntity, double[], Double> {

		private static final List<Double> VALUES = Arrays.asList( 0.0, 0.12, 156.52, 9486546.58 );

		@Override
		public String getIndexName() {
			return IndexedEntity.NAME;
		}

		@Override
		public Class<IndexedEntity> getIndexedClass() {
			return IndexedEntity.class;
		}

		@Override
		public IndexedEntity newIndexed(int id) {
			return new IndexedEntity( id );
		}

		@Override
		public double[] newArray(int size) {
			return new double[size];
		}

		@Override
		public void setElement(double[] array, int arrayIndex, int valueOrdinal) {
			array[arrayIndex] = VALUES.get( valueOrdinal );
		}

		@Override
		public Class<Double> getExpectedIndexFieldType() {
			return Double.class;
		}

		@Override
		public Double getExpectedIndexFieldValue(double[] array, int arrayIndex) {
			return array[arrayIndex];
		}

		@Override
		public void setSerializedArray(IndexedEntity indexed, double[] array) {
			indexed.setSerializedArray( array );
		}

		@Override
		public double[] getSerializedArray(IndexedEntity indexed) {
			return indexed.getSerializedArray();
		}

		@Override
		public void setElementCollectionArray(IndexedEntity indexedEntity, double[] array) {
			indexedEntity.setElementCollectionArray( array );
		}

		@Override
		public double[] getElementCollectionArray(IndexedEntity indexedEntity) {
			return indexedEntity.getElementCollectionArray();
		}
	}

	@Entity(name = IndexedEntity.NAME)
	@Indexed(index = IndexedEntity.NAME)
	public static class IndexedEntity {
		static final String NAME = "Indexed";

		@Id
		private Integer id;

		@GenericField
		@JdbcTypeCode(Types.VARBINARY)
		private double[] serializedArray;

		@GenericField
		@ElementCollection
		@OrderColumn
		private double[] elementCollectionArray;

		protected IndexedEntity() {
		}

		public IndexedEntity(Integer id) {
			this.id = id;
		}

		public Integer getId() {
			return id;
		}

		public double[] getSerializedArray() {
			return serializedArray;
		}

		public void setSerializedArray(double[] serializedArray) {
			this.serializedArray = serializedArray;
		}

		public double[] getElementCollectionArray() {
			return elementCollectionArray;
		}

		public void setElementCollectionArray(double[] elementCollectionArray) {
			this.elementCollectionArray = elementCollectionArray;
		}
	}

}
