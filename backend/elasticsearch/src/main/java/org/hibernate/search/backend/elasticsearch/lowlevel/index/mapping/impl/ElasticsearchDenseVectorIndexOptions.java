package org.hibernate.search.backend.elasticsearch.lowlevel.index.mapping.impl;

import java.util.Map;

import org.hibernate.search.backend.elasticsearch.json.impl.JsonName;
import org.hibernate.search.backend.elasticsearch.json.impl.JsonSerializable;
import org.hibernate.search.backend.elasticsearch.json.impl.SerializeExtraProperties;
import org.hibernate.search.backend.elasticsearch.json.spi.JsonProviderHelper;
import org.hibernate.search.util.json.JsonElement;

/**
 * An object representing Elasticsearch dense vector-specific index options attributes.
 *
 * See https://www.elastic.co/guide/en/elasticsearch/reference/current/dense-vector.html
 */
@JsonSerializable
public class ElasticsearchDenseVectorIndexOptions {

	private String type;

	private Integer m;

	@JsonName("ef_construction")
	private Integer efConstruction;

	@SerializeExtraProperties
	private Map<String, JsonElement> extraAttributes;

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public Integer getM() {
		return m;
	}

	public void setM(Integer m) {
		this.m = m;
	}

	public Integer getEfConstruction() {
		return efConstruction;
	}

	public void setEfConstruction(Integer efConstruction) {
		this.efConstruction = efConstruction;
	}

	public Map<String, JsonElement> getExtraAttributes() {
		return extraAttributes;
	}

	public void setExtraAttributes(Map<String, JsonElement> extraAttributes) {
		this.extraAttributes = extraAttributes;
	}

	@Override
	public String toString() {
		return JsonProviderHelper.toPrettyJson( this );
	}
}
