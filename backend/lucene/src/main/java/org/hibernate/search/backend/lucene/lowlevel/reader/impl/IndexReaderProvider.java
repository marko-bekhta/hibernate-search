/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.search.backend.lucene.lowlevel.reader.impl;

import java.io.IOException;

import org.apache.lucene.index.DirectoryReader;

public interface IndexReaderProvider {

	/**
	 * Closes and drops any cached resources (index readers in particular).
	 * <p>
	 * Should be used when stopping the index, to clean up upon error,
	 * or simply to force the creation of a new reader (refresh) on the next call to {@link #getOrCreate()}.
	 */
	void clear() throws IOException;

	/**
	 * @return A ready-to-use index reader, with its reference count already increased.
	 * Callers are responsible for calling {@link DirectoryReader#decRef()} when they are done with the index reader.
	 * Callers <strong>must not</strong> call {@link DirectoryReader#close()},
	 * as the index reader may be shared.
	 */
	DirectoryReader getOrCreate() throws IOException;

	default DirectoryReader getCurrentForTests() throws IOException {
		return getOrCreate();
	}
}
