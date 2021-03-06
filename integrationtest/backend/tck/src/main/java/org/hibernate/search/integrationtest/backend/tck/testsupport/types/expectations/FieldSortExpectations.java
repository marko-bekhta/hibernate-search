/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.integrationtest.backend.tck.testsupport.types.expectations;

public class FieldSortExpectations<F> {

	private final F document1Value;
	private final F document2Value;
	private final F document3Value;
	private final F beforeDocument1Value;
	private final F betweenDocument1And2Value;
	private final F betweenDocument2And3Value;
	private final F afterDocument3Value;

	public FieldSortExpectations(F document1Value, F document2Value, F document3Value,
			F beforeDocument1Value, F betweenDocument1And2Value, F betweenDocument2And3Value, F afterDocument3Value) {
		this.document1Value = document1Value;
		this.document2Value = document2Value;
		this.document3Value = document3Value;
		this.beforeDocument1Value = beforeDocument1Value;
		this.betweenDocument1And2Value = betweenDocument1And2Value;
		this.betweenDocument2And3Value = betweenDocument2And3Value;
		this.afterDocument3Value = afterDocument3Value;
	}

	public boolean isFieldSortSupported() {
		return true;
	}

	public final F getDocument1Value() {
		return document1Value;
	}

	public final F getDocument2Value() {
		return document2Value;
	}

	public final F getDocument3Value() {
		return document3Value;
	}

	public final F getBeforeDocument1Value() {
		return beforeDocument1Value;
	}

	public final F getBetweenDocument1And2Value() {
		return betweenDocument1And2Value;
	}

	public final F getBetweenDocument2And3Value() {
		return betweenDocument2And3Value;
	}

	public final F getAfterDocument3Value() {
		return afterDocument3Value;
	}
}
