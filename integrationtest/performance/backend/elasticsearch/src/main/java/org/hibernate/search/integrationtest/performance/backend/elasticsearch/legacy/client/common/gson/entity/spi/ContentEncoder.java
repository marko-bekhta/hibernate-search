
package org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.client.common.gson.entity.spi;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.hibernate.search.util.common.annotation.Incubating;

@Incubating
public interface ContentEncoder {

	int write(ByteBuffer src) throws IOException;

	void complete() throws IOException;

	boolean isCompleted();

	default <T> T unwrap(Class<T> clazz) {
		return clazz.cast( this );
	}

}
