/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.search.backend.elasticsearch.lowlevel.index.mapping.impl;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

import org.hibernate.search.backend.elasticsearch.gson.impl.SerializeExtraProperties;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;

/**
 * An object representing Elasticsearch type mappings.
 * <p>
 * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/mapping.html#mapping">Mapping</a>.
 */
/*
 * CAUTION: JSON serialization is controlled by a specific adapter, which must be
 * updated whenever fields of this class are added, renamed or removed.
 */
public abstract class AbstractTypeMapping {

	private DynamicType dynamic;

	/**
	 * Must be null when we don't want to include it in JSON serialization.
	 */
	protected Map<String, PropertyMapping> properties;

	@SerializeExtraProperties
	private Map<String, JsonElement> extraAttributes;

	public DynamicType getDynamic() {
		return dynamic;
	}

	public void setDynamic(DynamicType dynamic) {
		this.dynamic = dynamic;
	}

	public Map<String, PropertyMapping> getProperties() {
		return properties == null ? null : Collections.unmodifiableMap( properties );
	}

	private Map<String, PropertyMapping> getInitializedProperties() {
		if ( properties == null ) {
			properties = new TreeMap<>();
		}
		return properties;
	}

	public void addProperty(String name, PropertyMapping mapping) {
		getInitializedProperties().put( name, mapping );
	}

	public void removeProperty(String name) {
		getInitializedProperties().remove( name );
	}

	public Map<String, JsonElement> getExtraAttributes() {
		return extraAttributes;
	}

	public void setExtraAttributes(Map<String, JsonElement> extraAttributes) {
		this.extraAttributes = extraAttributes;
	}

	@Override
	public String toString() {
		return new GsonBuilder().setPrettyPrinting().create().toJson( this );
	}

	/**
	 * Merge this mapping with another mapping generated by Hibernate Search:
	 *
	 * <ol>
	 * <li>The value for the {@code dynamic} mapping parameter will be taken from the {@code other} unless {@code this} provides a non-null value.</li>
	 * <li>The mapping parameter {@code properties} will be merged, using properties defined in both {@code this} and {@code other}.</li>
	 * <li>Mapping parameters other than {@code dynamic} and {@code properties} will be those from {@code this};
	 * those from {@code other} will be ignored.</li>
	 * <li>If a property is defined on both sides, it will be merged recursively as described above.</li>
	 * </ol>
	 *
	 * @param other The other mapping.
	 */
	public void merge(AbstractTypeMapping other) {
		if ( other == null ) {
			// nothing to do
			return;
		}

		if ( this.dynamic == null ) {
			this.dynamic = other.dynamic;
		}

		Map<String, PropertyMapping> otherProperties = other.properties;
		if ( otherProperties == null ) {
			return;
		}

		for ( Map.Entry<String, PropertyMapping> entry : otherProperties.entrySet() ) {
			String name = entry.getKey();
			PropertyMapping thisProperty = properties == null ? null : properties.get( name );
			PropertyMapping otherProperty = entry.getValue();
			if ( thisProperty != null ) {
				// Merge common properties recursively
				thisProperty.merge( otherProperty );
			}
			else {
				// Add missing properties
				addProperty( name, otherProperty );
			}
		}
	}
}
