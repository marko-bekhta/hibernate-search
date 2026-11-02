package org.hibernate.search.integrationtest.performance.backend.elasticsearch;

import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

/** Affinity-only setup for factory comparisons, without pre-initializing the factories. */
@State(Scope.Thread)
public class JsonBenchmarkAffinity {
	@Setup
	public void verify() {
		JsonBenchmarkSupport.verifyAffinity();
	}
}
