/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.engine.mapper.mapping.building.impl;

import java.util.Optional;

import org.hibernate.search.engine.backend.document.model.dsl.impl.IndexSchemaNestingContext;
import org.hibernate.search.engine.backend.document.model.spi.IndexFieldInclusion;
import org.hibernate.search.engine.mapper.mapping.building.spi.IndexedEmbeddedDefinition;
import org.hibernate.search.engine.mapper.mapping.building.spi.IndexedEmbeddedPathTracker;


class ConfiguredIndexSchemaNestingContext implements IndexSchemaNestingContext {

	private static final ConfiguredIndexSchemaNestingContext ROOT =
			new ConfiguredIndexSchemaNestingContext( IndexSchemaFilter.root(), "", "" );

	public static ConfiguredIndexSchemaNestingContext root() {
		return ROOT;
	}

	private final IndexSchemaFilter filter;
	private final String prefixFromFilter;
	private final String unconsumedPrefix;

	private ConfiguredIndexSchemaNestingContext(IndexSchemaFilter filter, String prefixFromFilter,
			String unconsumedPrefix) {
		this.filter = filter;
		this.prefixFromFilter = prefixFromFilter;
		this.unconsumedPrefix = unconsumedPrefix;
	}

	@Override
	public String toString() {
		return new StringBuilder( getClass().getSimpleName() )
				.append( "[" )
				.append( "filter=" ).append( filter )
				.append( ",prefixFromFilter=" ).append( prefixFromFilter )
				.append( ",unconsumedPrefix=" ).append( unconsumedPrefix )
				.append( "]" )
				.toString();
	}

	@Override
	public <T> T nest(String relativeName, LeafFactory<T> factory) {
		String nameRelativeToFilter = prefixFromFilter + relativeName;
		String prefixedRelativeName = unconsumedPrefix + relativeName;
		boolean included = filter.isPathIncluded( nameRelativeToFilter );
		return factory.create( prefixedRelativeName,
				included ? IndexFieldInclusion.INCLUDED : IndexFieldInclusion.EXCLUDED );
	}

	@Override
	public <T> T nest(String relativeName, CompositeFactory<T> factory) {
		String nameRelativeToFilter = prefixFromFilter + relativeName;
		String prefixedRelativeName = unconsumedPrefix + relativeName;
		boolean included = filter.isPathIncluded( nameRelativeToFilter );
		if ( included ) {
			ConfiguredIndexSchemaNestingContext nestedFilter =
					new ConfiguredIndexSchemaNestingContext( filter, nameRelativeToFilter + ".", "" );
			return factory.create( prefixedRelativeName, IndexFieldInclusion.INCLUDED, nestedFilter );
		}
		else {
			return factory.create( prefixedRelativeName, IndexFieldInclusion.EXCLUDED,
					IndexSchemaNestingContext.excludeAll() );
		}
	}

	@Override
	public <T> T nestUnfiltered(UnfilteredFactory<T> factory) {
		return factory.create( IndexFieldInclusion.INCLUDED, unconsumedPrefix );
	}

	public <T> Optional<T> addIndexedEmbeddedIfIncluded(
			IndexedEmbeddedDefinition definition,
			IndexedEmbeddedPathTracker pathTracker,
			NestedContextBuilder<T> contextBuilder) {
		IndexSchemaFilter composedFilter = filter.compose( definition, pathTracker );

		// NOTE: shouldNotBeExcluded() has side effect of marking paths as encountered.
		if ( !composedFilter.isEveryPathExcluded() && shouldNotBeExcluded( definition ) ) {
			String prefixToParse = unconsumedPrefix + definition.relativePrefix();
			int afterPreviousDotIndex = 0;
			int nextDotIndex = prefixToParse.indexOf( '.', afterPreviousDotIndex );
			while ( nextDotIndex >= 0 ) {
				String objectName = prefixToParse.substring( afterPreviousDotIndex, nextDotIndex );
				contextBuilder.appendObject( objectName );

				afterPreviousDotIndex = nextDotIndex + 1;
				nextDotIndex = prefixToParse.indexOf( '.', afterPreviousDotIndex );
			}
			String composedUnconsumedPrefix = prefixToParse.substring( afterPreviousDotIndex );

			ConfiguredIndexSchemaNestingContext nestedContext =
					new ConfiguredIndexSchemaNestingContext( composedFilter, "", composedUnconsumedPrefix );
			return Optional.of( contextBuilder.build( nestedContext ) );
		}
		else {
			return Optional.empty();
		}
	}

	private boolean shouldNotBeExcluded(IndexedEmbeddedDefinition definition) {
		String prefixToParse = unconsumedPrefix + definition.relativePrefix();
		int afterPreviousDotIndex = 0;
		int nextDotIndex = prefixToParse.indexOf( '.', afterPreviousDotIndex );
		boolean shouldInclude = true;
		while ( nextDotIndex >= 0 ) {
			// Make sure to mark the paths as encountered in the filter
			String objectNameRelativeToFilter = prefixToParse.substring( 0, nextDotIndex );

			//TODO: to Yoann: I am not convinced if this is a correct way to address it or not, but based on the tests
			// we have for prefixes I've guessed that if we have a prefixed embedded then `unconsumedPrefix` will have that prefix in it
			// and we not necessarily need to "include" a path-with-prefix in the filter if we want to have a property to be included ( DeepPathWithLeadingPrefixCase )

			boolean included =
					// NOTE: isPathIncluded has a side effect: it marks the path as encountered
					// first let's see if the object name as it is, is included by a filter:
					filter.isPathIncluded( objectNameRelativeToFilter ) ||
							// now if we had a prefixed path we want to check if the path was actually included (without a prefix)
							// but we don't want to mark that path as encountered as we'd be reporting an incorrect path.
							// if we don't have a prefix than the path will be the same as in the previous check and
							// we've already marked it as encountered:
							filter.isPathIncluded(
									objectNameRelativeToFilter.substring( unconsumedPrefix.length() ), false
							);
			shouldInclude = shouldInclude && included;

			afterPreviousDotIndex = nextDotIndex + 1;
			nextDotIndex = prefixToParse.indexOf( '.', afterPreviousDotIndex );
		}

		return shouldInclude;
	}

	public interface NestedContextBuilder<T> {

		void appendObject(String objectName);

		T build(ConfiguredIndexSchemaNestingContext nestingContext);

	}
}
