/**
 * Search-owned, mutable JSON trees with immutable primitive and null values.
 * Missing object members return Java null; explicit JSON null uses {@link org.hibernate.search.util.json.JsonNull#INSTANCE}.
 * Objects preserve member insertion order and replace duplicate values in place.
 * Numeric primitives retain their JSON lexemes; equality compares exact numeric values and ignores decimal scale.
 * Numeric getters continue to narrow and truncate rather than imposing exact-integer conversion.
 * Submitted request trees must remain unchanged until every retry has completed.
 * Container copies are independent; immutable primitive values may be shared.
 */
package org.hibernate.search.util.json;
