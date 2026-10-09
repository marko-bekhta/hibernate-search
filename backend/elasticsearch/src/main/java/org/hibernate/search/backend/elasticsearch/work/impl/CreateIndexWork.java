package org.hibernate.search.backend.elasticsearch.work.impl;

import java.util.Map;

import org.hibernate.search.backend.elasticsearch.client.common.json.spi.JsonProvider;
import org.hibernate.search.backend.elasticsearch.client.common.spi.ElasticsearchRequest;
import org.hibernate.search.backend.elasticsearch.client.common.spi.ElasticsearchResponse;
import org.hibernate.search.backend.elasticsearch.client.common.util.spi.URLEncodedString;
import org.hibernate.search.backend.elasticsearch.client.impl.ElasticsearchClientUtils;
import org.hibernate.search.backend.elasticsearch.json.spi.JsonCodecService;
import org.hibernate.search.backend.elasticsearch.lowlevel.index.aliases.impl.IndexAliasDefinition;
import org.hibernate.search.backend.elasticsearch.lowlevel.index.mapping.impl.RootTypeMapping;
import org.hibernate.search.backend.elasticsearch.lowlevel.index.settings.impl.IndexSettings;
import org.hibernate.search.backend.elasticsearch.work.result.impl.CreateIndexResult;
import org.hibernate.search.util.json.JsonObject;

public class CreateIndexWork extends AbstractNonBulkableWork<CreateIndexResult> {

	private static final String MAPPINGS_PROPERTY = "mappings";

	protected CreateIndexWork(Builder builder) {
		super( builder );
	}

	@Override
	protected CreateIndexResult generateResult(ElasticsearchWorkExecutionContext context, ElasticsearchResponse response) {
		int statusCode = response.statusCode();
		if ( ElasticsearchClientUtils.isSuccessCode( statusCode ) ) {
			return CreateIndexResult.CREATED;
		}
		else {
			// Can only happen if ignoreExisting() was called on the builder
			return CreateIndexResult.ALREADY_EXISTS;
		}
	}

	public static class Builder
			extends AbstractBuilder<Builder> {
		private final JsonProvider jsonProvider;
		private final URLEncodedString indexName;
		private final JsonObject payload = new JsonObject();

		public static Builder create(JsonProvider jsonProvider, URLEncodedString indexName) {
			return new Builder( jsonProvider, indexName );
		}

		private Builder(JsonProvider jsonProvider, URLEncodedString indexName) {
			super( ElasticsearchRequestSuccessAssessor.DEFAULT_INSTANCE );
			this.jsonProvider = jsonProvider;
			this.indexName = indexName;
		}

		public Builder aliases(Map<String, IndexAliasDefinition> aliases) {
			/*
			 * Serializing nulls is really not a good idea here, it triggers NPEs in Elasticsearch
			 * We better not include the null fields.
			 */
			JsonCodecService json = jsonProvider.getCodecNoSerializeNulls();
			payload.add( "aliases", org.hibernate.search.util.json.spi.JsonCodecs
					.map( json.getCodec( IndexAliasDefinition.class ) ).encode( aliases, false ) );
			return this;
		}

		public Builder settings(IndexSettings settings) {
			/*
			 * Serializing nulls is really not a good idea here, it triggers NPEs in Elasticsearch
			 * We better not include the null fields.
			 */
			JsonCodecService json = jsonProvider.getCodecNoSerializeNulls();
			payload.add( "settings", json.encode( settings ) );
			return this;
		}

		public Builder mapping(RootTypeMapping mapping) {
			JsonCodecService json = jsonProvider.getCodecNoSerializeNulls();

			payload.add( MAPPINGS_PROPERTY, json.encode( mapping ) );

			return this;
		}

		public Builder ignoreExisting() {
			this.resultAssessor = ElasticsearchRequestSuccessAssessor.builder()
					.ignoreErrorTypes( "index_already_exists_exception" )
					.build();
			return this;
		}

		@Override
		protected ElasticsearchRequest buildRequest() {
			ElasticsearchRequest.Builder builder =
					ElasticsearchRequest.put()
							.pathComponent( indexName );
			if ( payload.size() > 0 ) {
				builder.body( payload );
			}
			return builder.build();
		}

		@Override
		public CreateIndexWork build() {
			return new CreateIndexWork( this );
		}
	}
}
