/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.backend.lucene.types.codec.impl;

import static java.time.temporal.ChronoField.HOUR_OF_DAY;
import static java.time.temporal.ChronoField.MINUTE_OF_HOUR;
import static java.time.temporal.ChronoField.NANO_OF_SECOND;
import static java.time.temporal.ChronoField.SECOND_OF_MINUTE;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.ResolverStyle;
import java.util.Locale;

import org.hibernate.search.backend.lucene.document.impl.LuceneDocumentBuilder;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.index.IndexableField;

public final class LuceneLocalTimeFieldCodec extends AbstractLuceneNumericFieldCodec<LocalTime, Long> {

	static final DateTimeFormatter FORMATTER = new DateTimeFormatterBuilder()
			.appendValue( HOUR_OF_DAY, 2 )
			.appendLiteral( ':' )
			.appendValue( MINUTE_OF_HOUR, 2 )
			.optionalStart()
			.appendLiteral( ':' )
			.appendValue( SECOND_OF_MINUTE, 2 )
			.optionalStart()
			.appendFraction( NANO_OF_SECOND, 3, 9, true )
			.toFormatter( Locale.ROOT )
			.withResolverStyle( ResolverStyle.STRICT );

	public LuceneLocalTimeFieldCodec(boolean projectable, boolean sortable, LocalTime indexNullAsValue) {
		super( projectable, sortable, indexNullAsValue );
	}

	@Override
	void doEncodeForProjection(LuceneDocumentBuilder documentBuilder, String absoluteFieldPath, LocalTime value,
			Long encodedValue) {
		documentBuilder.addField( new StoredField( absoluteFieldPath, FORMATTER.format( value ) ) );
	}

	@Override
	public LocalTime decode(Document document, String absoluteFieldPath) {
		IndexableField field = document.getField( absoluteFieldPath );

		if ( field == null ) {
			return null;
		}

		String value = field.stringValue();

		if ( value == null ) {
			return null;
		}

		return LocalTime.parse( value, FORMATTER );
	}

	@Override
	public Long encode(LocalTime value) {
		return value == null ? null : value.toNanoOfDay();
	}

	@Override
	public LuceneNumericDomain<Long> getDomain() {
		return LuceneNumericDomain.LONG;
	}
}
