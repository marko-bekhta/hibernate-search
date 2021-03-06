/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.engine.logging.spi;

public class SimpleNameClassFormatter {

	private final String stringRepresentation;

	public SimpleNameClassFormatter(Class<?> clazz) {
		this.stringRepresentation = clazz.getSimpleName();
	}

	@Override
	public String toString() {
		return stringRepresentation;
	}
}
