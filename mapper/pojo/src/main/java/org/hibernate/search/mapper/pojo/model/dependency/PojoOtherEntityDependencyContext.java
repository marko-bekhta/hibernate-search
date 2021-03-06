/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.mapper.pojo.model.dependency;

import org.hibernate.search.mapper.pojo.model.path.PojoModelPath;
import org.hibernate.search.mapper.pojo.model.path.PojoModelPathValueNode;

/**
 * @hsearch.experimental This type is under active development.
 *    Usual compatibility policies do not apply: incompatible changes may be introduced in any future release.
 */
public interface PojoOtherEntityDependencyContext {

	/**
	 * Declare that the given path is read by the bridge at index time to populate the indexed document.
	 *
	 * @param pathFromOtherEntityTypeToUsedValue The path from the entity type to the value used by the bridge,
	 * as a String. The string is interpreted with default value extractors: see {@link PojoModelPath#parse(String)}.
	 * @return {@code this}, for method chaining.
	 * @throws org.hibernate.search.util.common.SearchException If the given path cannot be applied to the entity type.
	 * @see #use(PojoModelPathValueNode)
	 * @hsearch.experimental This feature is under active development.
	 *    Usual compatibility policies do not apply: incompatible changes may be introduced in any future release.
	 */
	default PojoOtherEntityDependencyContext use(String pathFromOtherEntityTypeToUsedValue) {
		return use( PojoModelPath.parse( pathFromOtherEntityTypeToUsedValue ) );
	}

	/**
	 * Declare that the given path is read by the bridge at index time to populate the indexed document.
	 * <p>
	 * Every component of this path will be considered as a dependency,
	 * so it is not necessary to call this method for every subpath.
	 * In other words, if the path {@code "myProperty.someOtherPropety"} is declared as used,
	 * Hibernate Search will automatically assume that {@code "myProperty"} is also used.
	 *
	 * @param pathFromBridgedTypeToUsedValue The path from the entity type to the value used by the bridge.
	 * @return {@code this}, for method chaining.
	 * @throws org.hibernate.search.util.common.SearchException If the given path cannot be applied to the entity type.
	 * @hsearch.experimental This feature is under active development.
	 *    Usual compatibility policies do not apply: incompatible changes may be introduced in any future release.
	 */
	PojoOtherEntityDependencyContext use(PojoModelPathValueNode pathFromBridgedTypeToUsedValue);

}
