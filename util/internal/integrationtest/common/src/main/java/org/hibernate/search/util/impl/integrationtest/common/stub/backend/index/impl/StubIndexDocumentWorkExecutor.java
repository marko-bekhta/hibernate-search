/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.util.impl.integrationtest.common.stub.backend.index.impl;

import java.util.concurrent.CompletableFuture;

import org.hibernate.search.engine.backend.index.DocumentCommitStrategy;
import org.hibernate.search.engine.backend.index.spi.DocumentContributor;
import org.hibernate.search.engine.backend.index.spi.DocumentReferenceProvider;
import org.hibernate.search.engine.backend.index.spi.IndexDocumentWorkExecutor;
import org.hibernate.search.engine.backend.index.DocumentRefreshStrategy;
import org.hibernate.search.engine.mapper.session.context.spi.SessionContextImplementor;
import org.hibernate.search.util.impl.integrationtest.common.stub.backend.document.StubDocumentNode;
import org.hibernate.search.util.impl.integrationtest.common.stub.backend.document.impl.StubDocumentElement;
import org.hibernate.search.util.impl.integrationtest.common.stub.backend.index.StubIndexWork;

public class StubIndexDocumentWorkExecutor implements IndexDocumentWorkExecutor<StubDocumentElement> {
	private final StubIndexManager indexManager;
	private final SessionContextImplementor sessionContext;
	private final DocumentCommitStrategy commitStrategy;

	public StubIndexDocumentWorkExecutor(StubIndexManager indexManager, SessionContextImplementor sessionContext,
			DocumentCommitStrategy commitStrategy) {
		this.indexManager = indexManager;
		this.sessionContext = sessionContext;
		this.commitStrategy = commitStrategy;
	}

	@Override
	public CompletableFuture<?> add(DocumentReferenceProvider documentReferenceProvider,
			DocumentContributor<StubDocumentElement> documentContributor) {
		StubDocumentNode.Builder documentBuilder = StubDocumentNode.document();
		documentContributor.contribute( new StubDocumentElement( documentBuilder ) );

		StubIndexWork work = StubIndexWork.builder( StubIndexWork.Type.ADD )
				.tenantIdentifier( sessionContext.getTenantIdentifier() )
				.identifier( documentReferenceProvider.getIdentifier() )
				.routingKey( documentReferenceProvider.getRoutingKey() )
				.document( documentBuilder.build() )
				.commit( commitStrategy )
				.refresh( DocumentRefreshStrategy.NONE )
				.build();

		return indexManager.prepareAndExecuteWork( work );
	}
}
