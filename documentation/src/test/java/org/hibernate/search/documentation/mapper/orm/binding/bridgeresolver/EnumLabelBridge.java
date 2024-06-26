/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.search.documentation.mapper.orm.binding.bridgeresolver;

import org.hibernate.search.engine.environment.bean.BeanHolder;
import org.hibernate.search.mapper.pojo.bridge.ValueBridge;
import org.hibernate.search.mapper.pojo.bridge.runtime.ValueBridgeFromIndexedValueContext;
import org.hibernate.search.mapper.pojo.bridge.runtime.ValueBridgeToIndexedValueContext;

public class EnumLabelBridge<V> implements ValueBridge<V, String> {
	private final Class<V> enumType;
	private final BeanHolder<EnumLabelService> serviceHolder;

	public EnumLabelBridge(Class<V> enumType, BeanHolder<EnumLabelService> serviceHolder) {
		this.enumType = enumType;
		this.serviceHolder = serviceHolder;
	}

	@Override
	public String toIndexedValue(V value,
			ValueBridgeToIndexedValueContext context) {
		return serviceHolder.get().toLabel( value );
	}

	@Override
	public V fromIndexedValue(String value,
			ValueBridgeFromIndexedValueContext context) {
		return serviceHolder.get().fromLabel( enumType, value );
	}

	@Override
	public void close() {
		serviceHolder.close();
	}
}
