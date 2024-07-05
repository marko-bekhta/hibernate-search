/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.search.engine.cfg.spi;

import java.time.Instant;
import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import org.hibernate.search.util.common.annotation.Incubating;

@Incubating
public final class NormalizeUtils {

	private static final long ALMOST_WHOLE_DAY = 3600_000_000_000L * 24 - 1;

	private NormalizeUtils() {
	}

	public static OffsetTime normalizeOffsetTime(Long encoded) {
		Instant instant = Instant.EPOCH.plus( encoded, ChronoUnit.NANOS );
		if ( encoded < 0 ) {
			return instant.atOffset( ZoneOffset.ofHours( 24 - instant.atOffset( ZoneOffset.UTC ).getHour() ) )
					.toOffsetTime();
		}

		if ( encoded > ALMOST_WHOLE_DAY ) {
			return instant.atOffset( ZoneOffset.ofHours( -instant.atOffset( ZoneOffset.UTC ).getHour() - 1 ) )
					.toOffsetTime();
		}

		return instant.atOffset( ZoneOffset.UTC ).toOffsetTime();
	}

	public static OffsetTime normalizeOffsetTime(OffsetTime value) {
		long nod = value.toLocalTime().toNanoOfDay();
		long offsetNanos = value.getOffset().getTotalSeconds() * 1_000_000_000L;
		return normalizeOffsetTime( nod - offsetNanos );
	}

}
