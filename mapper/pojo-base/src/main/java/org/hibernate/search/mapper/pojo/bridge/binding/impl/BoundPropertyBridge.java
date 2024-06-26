/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.search.mapper.pojo.bridge.binding.impl;

import org.hibernate.search.engine.environment.bean.BeanHolder;
import org.hibernate.search.mapper.pojo.automaticindexing.building.impl.PojoIndexingDependencyCollectorPropertyNode;
import org.hibernate.search.mapper.pojo.bridge.PropertyBridge;
import org.hibernate.search.mapper.pojo.model.dependency.impl.PojoPropertyIndexingDependencyConfigurationContextImpl;
import org.hibernate.search.mapper.pojo.model.impl.PojoModelPropertyRootElement;

public final class BoundPropertyBridge<P> {
	private final BeanHolder<? extends PropertyBridge<? super P>> bridgeHolder;
	private final PojoModelPropertyRootElement<P> pojoModelRootElement;
	private final PojoPropertyIndexingDependencyConfigurationContextImpl<P> pojoDependencyContext;

	BoundPropertyBridge(BeanHolder<? extends PropertyBridge<? super P>> bridgeHolder,
			PojoModelPropertyRootElement<P> pojoModelRootElement,
			PojoPropertyIndexingDependencyConfigurationContextImpl<P> pojoDependencyContext) {
		this.bridgeHolder = bridgeHolder;
		this.pojoModelRootElement = pojoModelRootElement;
		this.pojoDependencyContext = pojoDependencyContext;
	}

	public BeanHolder<? extends PropertyBridge<? super P>> getBridgeHolder() {
		return bridgeHolder;
	}

	public PropertyBridge<? super P> getBridge() {
		return bridgeHolder.get();
	}

	public void contributeDependencies(PojoIndexingDependencyCollectorPropertyNode<?, P> dependencyCollector) {
		pojoModelRootElement.contributeDependencies( dependencyCollector );
		pojoDependencyContext.contributeDependencies( dependencyCollector );
	}
}
