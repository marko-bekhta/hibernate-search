import java.lang.reflect.Method;
import java.util.List;

/** Standalone verification of the actual pinned sniffer binaries; no network access. */
public class SnifferCoexistenceProbe {
	public static void main(String[] args) throws Exception {
		Class<?> rest4 = Class.forName( "org.elasticsearch.client.sniff.ElasticsearchNodesSniffer" );
		Class<?> openSearch = Class.forName( "org.opensearch.client.sniff.OpenSearchNodesSniffer" );
		Class<?> rest4Scheme = Class.forName( rest4.getName() + "$Scheme" );
		Class<?> openSearchScheme = Class.forName( openSearch.getName() + "$Scheme" );
		Method readRest4 = rest4.getDeclaredMethod( "readHosts", org.apache.http.HttpEntity.class,
				rest4Scheme, com.fasterxml.jackson.core.JsonFactory.class );
		Method readOpenSearch = openSearch.getDeclaredMethod( "readHosts", org.apache.hc.core5.http.HttpEntity.class,
				openSearchScheme, tools.jackson.core.json.JsonFactory.class );
		readRest4.setAccessible( true );
		readOpenSearch.setAccessible( true );
		String json = "{\"nodes\":{\"id\":{\"name\":\"test\",\"version\":\"9.5.4\",\"roles\":[\"data\"],"
				+ "\"http\":{\"publish_address\":\"127.0.0.1:9200\",\"bound_address\":[\"127.0.0.1:9200\"]}}}}";
		List<?> rest4Nodes = (List<?>) readRest4.invoke( null, new org.apache.http.entity.StringEntity( json ),
				rest4Scheme.getEnumConstants()[0], new com.fasterxml.jackson.core.JsonFactory() );
		List<?> openSearchNodes = (List<?>) readOpenSearch.invoke( null,
				new org.apache.hc.core5.http.io.entity.StringEntity( json ), openSearchScheme.getEnumConstants()[0],
				new tools.jackson.core.json.JsonFactory() );
		if ( rest4Nodes.size() != 1 || openSearchNodes.size() != 1 ) {
			throw new AssertionError( "Expected one node from each pinned sniffer" );
		}
		System.out.println( "REST4/Core " + new com.fasterxml.jackson.core.JsonFactory().version()
				+ " and OpenSearch/Core " + new tools.jackson.core.json.JsonFactory().version()
				+ " sniffers parse nodes concurrently: PASS" );
	}
}
