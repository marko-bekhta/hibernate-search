/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.integrationtest.backend.tck.search.predicate;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.hibernate.search.engine.search.predicate.dsl.PredicateFinalStep;
import org.hibernate.search.engine.search.predicate.dsl.SearchPredicateFactory;
import org.hibernate.search.integrationtest.backend.tck.testsupport.util.extension.SearchSetupHelper;
import org.hibernate.search.util.impl.integrationtest.mapper.stub.BulkIndexer;
import org.hibernate.search.util.impl.integrationtest.mapper.stub.StubMappedIndex;

import org.junit.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.provider.Arguments;

public class NotPredicateBaseIT {

	@RegisterExtension
	public static SearchSetupHelper setupHelper = SearchSetupHelper.createGlobal();

	@BeforeAll
	public static void setup() {
		setupHelper.start()
				.withIndexes(
						ScoreIT.index
				)
				.setup();

		final BulkIndexer scoreIndexer = ScoreIT.index.bulkIndexer();
		ScoreIT.dataSet.contribute( scoreIndexer );

		scoreIndexer.join();
	}

	@Test
	public void takariCpSuiteWorkaround() {
		// Workaround to get Takari-CPSuite to run this test.
	}

	@Nested
	public static class ScoreIT extends AbstractPredicateScoreIT {
		private static final DataSet dataSet = new DataSet();

		private static final StubMappedIndex index = StubMappedIndex.withoutFields().name( "score" );

		public static List<? extends Arguments> params() {
			return Arrays.asList( Arguments.of( index, dataSet ) );
		}

		@Override
		protected PredicateFinalStep predicate(SearchPredicateFactory f, int matchingDocOrdinal,
				AbstractPredicateDataSet dataSet, StubMappedIndex index) {
			return f.not( f.id().matchingAny( ( (DataSet) dataSet ).docsExcept( matchingDocOrdinal ) ) );
		}

		@Override
		protected PredicateFinalStep predicateWithBoost(SearchPredicateFactory f, int matchingDocOrdinal, float boost,
				AbstractPredicateDataSet dataSet, StubMappedIndex index) {
			return f.not( f.id().matchingAny( ( (DataSet) dataSet ).docsExcept( matchingDocOrdinal ) ) )
					.boost( boost );
		}

		@Override
		protected PredicateFinalStep predicateWithConstantScore(SearchPredicateFactory f, int matchingDocOrdinal,
				AbstractPredicateDataSet dataSet, StubMappedIndex index) {
			return f.not( f.id().matchingAny( ( (DataSet) dataSet ).docsExcept( matchingDocOrdinal ) ) )
					.constantScore();
		}

		@Override
		protected PredicateFinalStep predicateWithConstantScoreAndBoost(SearchPredicateFactory f,
				int matchingDocOrdinal, float boost, AbstractPredicateDataSet dataSet, StubMappedIndex index) {
			return f.not( f.id().matchingAny( ( (DataSet) dataSet ).docsExcept( matchingDocOrdinal ) ) )
					.constantScore()
					.boost( boost );
		}

		private static class DataSet extends AbstractPredicateDataSet {

			private final List<Integer> ordinals = Arrays.asList( 0, 1, 2 );

			protected DataSet() {
				super( "singleRoutingKey" );
			}

			public void contribute(BulkIndexer scoreIndexer) {
				for ( Integer ordinal : ordinals ) {
					scoreIndexer.add( docId( ordinal ), routingKey, document -> {} );
				}
			}

			Collection<String> docsExcept(int docOrdinal) {
				return ordinals.stream()
						.filter( i -> !i.equals( docOrdinal ) )
						.map( this::docId )
						.collect( Collectors.toList() );
			}
		}
	}
}
