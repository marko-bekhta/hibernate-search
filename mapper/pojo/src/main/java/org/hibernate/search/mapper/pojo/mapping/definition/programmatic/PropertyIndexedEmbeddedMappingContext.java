/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.mapper.pojo.mapping.definition.programmatic;

import java.util.Arrays;
import java.util.Collection;

import org.hibernate.search.engine.backend.document.model.dsl.ObjectFieldStorage;
import org.hibernate.search.mapper.pojo.extractor.ContainerExtractorPath;

public interface PropertyIndexedEmbeddedMappingContext extends PropertyMappingContext {

	PropertyIndexedEmbeddedMappingContext prefix(String prefix);

	PropertyIndexedEmbeddedMappingContext storage(ObjectFieldStorage storage);

	PropertyIndexedEmbeddedMappingContext maxDepth(Integer depth);

	default PropertyIndexedEmbeddedMappingContext includePaths(String ... paths) {
		return includePaths( Arrays.asList( paths ) );
	}

	PropertyIndexedEmbeddedMappingContext includePaths(Collection<String> paths);

	/**
	 * @param extractorName The name of the container extractor to use.
	 * @return {@code this}, for method chaining.
	 * @see org.hibernate.search.mapper.pojo.extractor.builtin.BuiltinContainerExtractors
	 */
	default PropertyIndexedEmbeddedMappingContext withExtractor(String extractorName) {
		return withExtractors( ContainerExtractorPath.explicitExtractor( extractorName ) );
	}

	/**
	 * Indicate that no container extractors should be applied,
	 * not even the default ones.
	 * @return {@code this}, for method chaining.
	 */
	default PropertyIndexedEmbeddedMappingContext withoutExtractors() {
		return withExtractors( ContainerExtractorPath.noExtractors() );
	}

	/**
	 * @param extractorPath A {@link ContainerExtractorPath}.
	 * @return {@code this}, for method chaining.
	 */
	PropertyIndexedEmbeddedMappingContext withExtractors(ContainerExtractorPath extractorPath);

}
