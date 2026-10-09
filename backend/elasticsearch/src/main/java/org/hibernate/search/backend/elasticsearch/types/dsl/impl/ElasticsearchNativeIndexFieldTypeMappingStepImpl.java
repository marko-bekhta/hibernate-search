package org.hibernate.search.backend.elasticsearch.types.dsl.impl;

import org.hibernate.search.backend.elasticsearch.json.spi.JsonCodecService;
import org.hibernate.search.backend.elasticsearch.lowlevel.index.mapping.impl.PropertyMapping;
import org.hibernate.search.backend.elasticsearch.types.dsl.ElasticsearchNativeIndexFieldTypeMappingStep;
import org.hibernate.search.backend.elasticsearch.types.dsl.ElasticsearchNativeIndexFieldTypeOptionsStep;
import org.hibernate.search.util.json.JsonObject;

class ElasticsearchNativeIndexFieldTypeMappingStepImpl
		implements ElasticsearchNativeIndexFieldTypeMappingStep {

	private final ElasticsearchIndexFieldTypeBuildContext buildContext;

	ElasticsearchNativeIndexFieldTypeMappingStepImpl(ElasticsearchIndexFieldTypeBuildContext buildContext) {
		this.buildContext = buildContext;
	}

	@Override
	public ElasticsearchNativeIndexFieldTypeOptionsStep<?> mapping(JsonObject jsonObject) {
		JsonCodecService json = buildContext.getUserFacingCodec();
		PropertyMapping mapping = json.decode( jsonObject, PropertyMapping.class );
		return new ElasticsearchNativeIndexFieldTypeOptionsStepImpl( buildContext, mapping );
	}

	@Override
	public ElasticsearchNativeIndexFieldTypeOptionsStep<?> mapping(String jsonString) {
		JsonCodecService json = buildContext.getUserFacingCodec();
		return mapping( json.decode( jsonString, JsonObject.class ) );
	}

}
