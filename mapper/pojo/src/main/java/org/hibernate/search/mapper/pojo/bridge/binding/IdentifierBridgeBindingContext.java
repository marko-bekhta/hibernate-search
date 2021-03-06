/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.mapper.pojo.bridge.binding;

import org.hibernate.search.mapper.pojo.bridge.IdentifierBridge;
import org.hibernate.search.mapper.pojo.model.PojoModelValue;

/**
 * The context provided to the {@link IdentifierBridge#bind(IdentifierBridgeBindingContext)} method.
 */
public interface IdentifierBridgeBindingContext<T> {

	/**
	 * @return An entry point allowing to inspect the type of values that will be passed to this bridge.
	 * @hsearch.experimental This feature is under active development.
	 *    Usual compatibility policies do not apply: incompatible changes may be introduced in any future release.
	 */
	PojoModelValue<T> getBridgedElement();

}
