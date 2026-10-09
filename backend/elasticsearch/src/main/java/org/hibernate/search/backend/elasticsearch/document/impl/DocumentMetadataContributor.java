package org.hibernate.search.backend.elasticsearch.document.impl;

import org.hibernate.search.util.json.JsonObject;

public interface DocumentMetadataContributor {

	/**
	 * Contributes metadata to the indexed document.
	 *
	 * @param document The indexed document.
	 * @param tenantId The tenant id.
	 * @param id The document id.
	 */
	void contribute(JsonObject document, String tenantId, String id);

}
