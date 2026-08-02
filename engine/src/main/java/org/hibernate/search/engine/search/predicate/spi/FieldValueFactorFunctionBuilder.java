package org.hibernate.search.engine.search.predicate.spi;

import org.hibernate.search.engine.search.common.ValueModel;

public interface FieldValueFactorFunctionBuilder {

	void modifierNone();

	void modifierLog();

	void modifierLog1p();

	void modifierLog2p();

	void modifierLn();

	void modifierLn1p();

	void modifierLn2p();

	void modifierSquare();

	void modifierSqrt();

	void modifierReciprocal();

	void factor(float factor);

	void missing(Object value, ValueModel valueModel);

}
