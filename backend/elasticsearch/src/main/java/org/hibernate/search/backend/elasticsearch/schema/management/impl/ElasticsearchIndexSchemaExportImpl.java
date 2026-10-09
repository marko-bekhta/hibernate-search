package org.hibernate.search.backend.elasticsearch.schema.management.impl;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.hibernate.search.backend.elasticsearch.client.common.spi.ElasticsearchRequest;
import org.hibernate.search.backend.elasticsearch.json.spi.JsonCodecService;
import org.hibernate.search.backend.elasticsearch.logging.impl.ElasticsearchMiscLog;
import org.hibernate.search.backend.elasticsearch.schema.management.ElasticsearchIndexSchemaExport;
import org.hibernate.search.util.json.JsonArray;
import org.hibernate.search.util.json.JsonElement;
import org.hibernate.search.util.json.JsonObject;

public class ElasticsearchIndexSchemaExportImpl implements ElasticsearchIndexSchemaExport {

	private final JsonCodecService userFacingCodec;
	private final String indexName;
	private final ElasticsearchRequest request;

	public ElasticsearchIndexSchemaExportImpl(JsonCodecService userFacingCodec, String indexName,
			ElasticsearchRequest request) {
		this.userFacingCodec = userFacingCodec;
		this.indexName = indexName;
		this.request = request;
	}

	@Override
	public void toFiles(Path targetDirectory) {
		JsonObject queryParams = new JsonObject();
		for ( Map.Entry<String, String> entry : request.parameters().entrySet() ) {
			queryParams.addProperty( entry.getKey(), entry.getValue() );
		}
		List<JsonObject> parts = request.bodyParts();
		JsonElement body = parts.size() == 1
				? parts.get( 0 )
				: parts.stream().collect( JsonArray::new, JsonArray::add, JsonArray::addAll );

		write( targetDirectory, "create-index.json", body );
		write( targetDirectory, "create-index-query-params.json", queryParams );
	}

	private void write(Path path, String filename, JsonElement data) {
		try ( OutputStream outputStream = Files.newOutputStream( Files.createDirectories( path ).resolve( filename ) ) ) {
			userFacingCodec.writeCompact( data, outputStream );
		}
		catch (IOException e) {
			throw ElasticsearchMiscLog.INSTANCE.unableToExportSchema( indexName, e.getMessage(), e );
		}
	}

	@Override
	public Map<String, String> parameters() {
		return Collections.unmodifiableMap( request.parameters() );
	}

	@Override
	public List<JsonObject> bodyParts() {
		return Collections.unmodifiableList( request.bodyParts() );
	}

}
