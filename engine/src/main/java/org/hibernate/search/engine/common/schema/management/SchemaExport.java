/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.search.engine.common.schema.management;

import java.nio.file.Path;

import org.hibernate.search.util.common.SearchException;
import org.hibernate.search.util.common.annotation.Incubating;

@Incubating
public interface SchemaExport {

	/**
	 * Writes the content of this export to a directory on the filesystem.
	 *
	 * @param targetDirectory The target directory to generate the output into.
	 */
	void toFiles(Path targetDirectory);

	/**
	 * Extends the export with the given extension,
	 * resulting in an extended export exposing more information.
	 *
	 * @param extension The extension to the export interface.
	 * @param <T> The type of export provided by the extension.
	 * @return The extended export.
	 * @throws SearchException If the extension cannot be applied (wrong underlying backend, ...).
	 */
	default <T> T extension(SchemaExportExtension<T> extension) {
		return extension.extendOrFail( this );
	}
}
