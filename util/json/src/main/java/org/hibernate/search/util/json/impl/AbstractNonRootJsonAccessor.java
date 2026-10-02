package org.hibernate.search.util.json.impl;

import org.hibernate.search.util.json.JsonElement;
import org.hibernate.search.util.json.spi.JsonAccessor;

abstract class AbstractNonRootJsonAccessor<P extends JsonElement, T> implements JsonAccessor<T> {
	private final JsonAccessor<P> parentAccessor;

	public AbstractNonRootJsonAccessor(JsonAccessor<P> parentAccessor) {
		super();
		this.parentAccessor = parentAccessor;
	}

	protected JsonAccessor<P> getParentAccessor() {
		return parentAccessor;
	}

	@Override
	public String toString() {
		StringBuilder path = new StringBuilder();

		if ( parentAccessor != RootJsonAccessor.INSTANCE ) {
			path.append( parentAccessor.toString() );
		}

		appendRuntimeRelativePath( path );

		return path.toString();
	}

	protected abstract void appendRuntimeRelativePath(StringBuilder path);

	protected abstract void appendStaticRelativePath(StringBuilder path, boolean first);
}
