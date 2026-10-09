package org.hibernate.search.backend.elasticsearch.work.result.impl;

import org.hibernate.search.backend.elasticsearch.json.spi.JsonProviderHelper;
import org.hibernate.search.backend.elasticsearch.lowlevel.index.impl.IndexMetadata;

/**
 * An object representing an Elasticsearch index that actually exists on the cluster.
 */
public final class ExistingIndexMetadata {

	private final String primaryName;

	private final IndexMetadata metadata;

	public ExistingIndexMetadata(String primaryName, IndexMetadata metadata) {
		this.primaryName = primaryName;
		this.metadata = metadata;
	}

	@Override
	public String toString() {
		var value = new org.hibernate.search.util.json.JsonObject();
		value.addProperty( "primaryName", primaryName );
		value.add( "metadata", JsonProviderHelper.createUserFacingCodec().encode( metadata ) );
		return JsonProviderHelper.toPrettyJson( value );
	}

	/**
	 * @return The primary name of the index, i.e. its un-aliased name.
	 */
	public String getPrimaryName() {
		return primaryName;
	}

	public IndexMetadata getMetadata() {
		return metadata;
	}
}
