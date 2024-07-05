/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.search.engine.cfg.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hibernate.search.engine.cfg.spi.NormalizeUtils.normalizeOffsetTime;

import java.time.LocalTime;
import java.time.OffsetTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class NormalizeUtilsTest {

	@Test
	void test() {
		assertThat( normalizeOffsetTime( time( 0, 1, 0 ) ) )
				.isEqualTo( time( 0, 1, 0 ) );
		assertThat( normalizeOffsetTime( time( 0, 1, 18 ) ) )
				.isEqualTo( time( 0, 1, 18 ) );
		assertThat( normalizeOffsetTime( time( 0, 1, -18 ) ) )
				.isEqualTo( time( 18, 1, 0 ) );
		assertThat( normalizeOffsetTime( time( 18, 1, 18 ) ) )
				.isEqualTo( time( 0, 1, 0 ) );
		assertThat( normalizeOffsetTime( time( 17, 1, 18 ) ) )
				.isEqualTo( time( 0, 1, 1 ) );
		assertThat( normalizeOffsetTime( time( 6, 1, -18 ) ) )
				.isEqualTo( time( 23, 1, -1 ) );
		assertThat( normalizeOffsetTime( time( 7, 1, -18 ) ) )
				.isEqualTo( time( 23, 1, -2 ) );
	}

	private OffsetTime time(int hour, int minute, int offset) {
		return OffsetTime.of( LocalTime.of( hour, minute ), ZoneOffset.ofHours( offset ) );
	}
}
