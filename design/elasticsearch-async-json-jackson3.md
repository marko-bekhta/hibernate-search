# Task 2 follow-up: Jackson 3

`hibernate-search-util-json` now uses managed `tools.jackson.core:jackson-core:3.2.2`. The Search model and public SPI remain library-neutral. This follow-up supersedes the original Task 2 checkpoint's Core 2 dependency choice; it does not start Task 3 or transport/backend migration.

## Dependencies and implementation

The build parent and platform-common BOM import `tools.jackson:jackson-bom:3.2.2` separately from the existing `com.fasterxml.jackson:jackson-bom:2.22.1`. The platform-common BOM also now lists `hibernate-search-util-json` in its manually maintained Search artifact list. Both published platform BOM variants inherit this management. Existing Core 2 dependencies remain because other libraries still need them.

Inspection of pinned Maven trees, source jars and binary constant pools produced this inventory:

| Pinned component | Actual JSON implementation | Result |
| --- | --- | --- |
| Elasticsearch REST client 9.5.4 | No Jackson imports/references in client classes | Client itself needs no Jackson |
| Elasticsearch REST4 sniffer 9.5.4 | `com.fasterxml.jackson.core` | Retain Core 2; Core 3 cannot satisfy those binary names |
| Elasticsearch REST5 client/sniffer 9.5.4 | Sniffer uses `jakarta.json.stream` | No client major-version switch required |
| OpenSearch REST/HTTP clients 3.8.0 | No Jackson imports/references in client classes | Clients themselves need no Jackson |
| OpenSearch sniffer 3.8.0 | `tools.jackson.core`, originally Core 3.2.1 | Already migrated upstream; now resolves managed Core 3.2.2 |
| AWS SDK 2.55.4 | `third-party-jackson-core`, relocated to `software.amazon.awssdk.thirdparty.jackson` | Keep its independently versioned shaded implementation |

The backend depends on Search's REST4 module, so REST5, OpenSearch and AWS adapter trees also inherit REST4/Core 2. Both unshaded major versions already existed in the repository through OpenSearch before this follow-up. We do not exclude or substitute a sniffer's independently required major version. The current Elasticsearch main sniffer source still imports Core 2; no client upgrades or sniffer replacements were needed here.

Core 3's Java 17 baseline matches Search's compilation target. The implementation adopts the new factory package and immutable builder configuration, empty read/write contexts, token/text/name APIs and unchecked Core exceptions. Public exception translation remains Search-owned. Diagnostic rendering still escapes surrogate code units and is separate from the unimplemented request-output SPI. Neither Databind nor reflective object mapping is introduced. Core alone adds no external runtime dependency; its optional fast-number parser is shaded.

The async feeder still stores the supplied `ByteBuffer` and uses absolute reads without advancing its position. The Search session explicitly advances caller position and replaces Core's reference with an empty buffer before returning. Strict UTF-8 validation, bounded charset transcoding, original-byte counting, end-of-input validation and cleanup contracts remain covered by the existing regression suite. Negative-zero lexeme recovery passes unchanged.

Stock Core 3.2.2 failed the escaped supplementary property-name split fixture during the temporary assessment. Besides the current-token state handled for Core 2.22.1, Core 3 can return `NOT_AVAILABLE` with low-surrogate escape bytes still available in the supplied buffer. The isolated internal parser subclass now normalizes the token and resumes only that pending-surrogate state until input is consumed. This retains no extra raw-body buffer and uses no reflection. The factory uses a separate internal creation method to avoid Core's generic intersection-return override warning under `-Werror`. The workaround relies on protected Core state and must be reevaluated on version upgrades using the split-point regression.

Module enforcement now rejects Core 2, Gson and Databind from both Jackson major versions in `util/json`. Signature tests reject both Jackson package prefixes, Gson and `.impl` types in model/SPI signatures; production-source checks reject Databind and reflective mapping.

## Verification

Verified on 2026-10-02 using Red Hat OpenJDK 25.0.4.1, Maven wrapper 3.9.16 and main compilation `--release 17`:

- JSON module clean verify: 75 cases, no failures/errors/skips; Checkstyle, compilation with `-Werror`, publication/dependency enforcement, sources, packaging and Moditect pass.
- Focused clean install: 15 modules, 504 goals, 454 cases, no failures/errors; 15 existing REST entity assumption skips. This covers JSON, common utilities, engine, backend, REST4, REST5, OpenSearch and AWS compilation/packaging. Existing transport cases still exercise Gson request entities; no new transport integration is claimed.
- Public/common/platform BOM installation: 30 goals pass, including all-public-artifact and group-artifact management enforcement. Platform-next installation: 10 goals pass.
- An independent consumer imports the published platform BOM and resolves Search JSON plus Core 2.22.1/Core 3.2.2 without explicit dependency versions.
- Distribution validation: 19 goals pass, including dependency convergence, aggregate Javadoc source-path and Lucene-next dependency-copy rules.
- Runtime trees: JSON has Core 3.2.2 and existing common dependencies only; no Core 2, Gson or Databind. OpenSearch resolves Core 3.2.2 alongside independently required Core 2.22.1; AWS retains its shaded dependency.
- External named-module consumer compiles/runs through model/parser/accessor SPI. An attempted `.impl` import is correctly rejected. Generated descriptor requires `tools.jackson.core` non-transitively and exports implementation only to the engine.
- The actual pinned REST4 and OpenSearch sniffer binaries concurrently parse a nodes fixture with their respective Core implementations. The standalone probe uses reflection solely to invoke package-private verification methods; production code does not use it.

The focused build cache was disabled. No full reactor, complete distribution ZIP/aggregate Javadocs, real-cluster integration, Java 17 runtime execution, native-image build or performance comparison was run. Only JDK 25 is installed. An initial BOM command used the nonexistent path `bom/platform-lucene-next`; it ran no goals and was corrected to the actual `bom/platform-next`. No outstanding check failed.

```bash
./mvnw -pl util/json spotless:apply clean verify -DskipITs -Dno-build-cache
./mvnw clean install \
  -pl util/json,backend/elasticsearch,backend/elasticsearch-client/elasticsearch-rest5-client,backend/elasticsearch-client/opensearch-rest-client,backend/elasticsearch-aws -am \
  -Dtest=JsonModelTest,JsonAccessorTest,JsonParserTest,JsonApiBoundaryTest,JsonParserCleanupTest,JsonAccessorCharacterizationTest,GsonCodecCharacterizationTest,GsonModelCharacterizationTest,ElasticsearchSearchRequestTransformerCharacterizationTest,ElasticsearchRawResultCharacterizationTest,ElasticsearchNativeJsonCharacterizationTest,ElasticsearchSchemaExportCharacterizationTest,GsonParsingTest,ClientRest4GsonHttpEntityTest,ClientRest5GsonHttpEntityTest,ClientOpenSearchGsonHttpEntityTest \
  -Dsurefire.failIfNoSpecifiedTests=false -DskipITs -Dno-build-cache
./mvnw -pl bom/public,bom/platform-common,bom/platform install -DskipTests -Dno-build-cache
./mvnw -pl bom/platform-next install -DskipTests -Dno-build-cache
./mvnw -Pdist -pl distribution validate -Dno-build-cache
./mvnw -f design/elasticsearch-async-json-jackson3/platform-consumer-pom.xml dependency:tree -Dscope=runtime
./mvnw -pl util/json,backend/elasticsearch-client/opensearch-rest-client,backend/elasticsearch-client/elasticsearch-rest5-client,backend/elasticsearch-aws \
  dependency:tree -Dscope=runtime -Dno-build-cache
./mvnw -pl util/json dependency:build-classpath -DincludeScope=runtime \
  -Dmdep.outputFile=/tmp/json3-runtime-classpath -Dno-build-cache
./mvnw -pl backend/elasticsearch-client/opensearch-rest-client dependency:build-classpath -DincludeScope=runtime \
  -Dmdep.outputFile=/tmp/json3-clients-classpath -Dno-build-cache
python3 design/elasticsearch-async-json-jackson3/verify.py \
  /tmp/json3-runtime-classpath /tmp/json3-clients-classpath
```

Evidence: [module](elasticsearch-async-json-jackson3/module-verification.log), [focused reactor](elasticsearch-async-json-jackson3/reactor-verification.log), [BOMs](elasticsearch-async-json-jackson3/bom-verification.log), [platform-next](elasticsearch-async-json-jackson3/next-bom-verification.log), [platform consumer](elasticsearch-async-json-jackson3/platform-consumer.log), [distribution](elasticsearch-async-json-jackson3/distribution-verification.log), [runtime dependencies](elasticsearch-async-json-jackson3/dependencies.log), [JPMS/sniffers](elasticsearch-async-json-jackson3/jpms-sniffer-verification.log). The [verification script](elasticsearch-async-json-jackson3/verify.py) and [sniffer probe](elasticsearch-async-json-jackson3/SnifferCoexistenceProbe.java) reproduce the external checks after dependency classpaths are regenerated. Historical Task 2/Core 2 logs remain unchanged.

## Future option: supporting Core 2 on demonstrated need

**This option is not implemented.** Pursue it only when a concrete consumer needs Search JSON to run without Core 3. Retaining Core 2 for a pinned sniffer does not itself justify a second Search parser adapter, and the option cannot replace the sniffers' independent major requirements.

A possible design would put two directly compiled adapters, one for each Core major, behind a library-neutral internal factory/interface. Each adapter would implement parser and diagnostic operations against its own Core API. The Search model/SPI would remain unchanged. Shared tree construction, validation and transcoding would retain their neutral boundaries. Missing-library adapter classes must remain unloaded until selected.

At startup, discover availability once and prefer Core 3 when both exist. Select Core 2 only when Core 3 is absent. With neither present, fail with an actionable dependency/configuration error. A present but incompatible, broken or incompletely linked Core 3 must fail clearly instead of silently falling back; only absence permits fallback. No per-token reflective dispatch, reflective field/method mapping, or reflective known-type codecs would be added.

The adapter layer would declare the Core dependencies optional, and its JPMS descriptor would use `requires static` for both Core module names. The backend would keep Core 3 as its default runtime dependency. Maven mediation, JPMS resolution and lazy class loading would need dedicated verification; optional declarations alone cannot guarantee missing-library execution works. Packaging as separate internal adapter artifacts could be considered if it makes class-loading isolation clearer.

Before adopting this option, require a test matrix with Core 2 only, Core 3 only, both and neither on classpath and module path. Include present-but-broken/incompatible Core 3, startup selection, isolated class loaders, Java 17 runtime and native-image reachability/class initialization. Run version-specific surrogate/negative-zero regressions, constraints, exact numeric lexemes, all buffer kinds/fragmentation, malformed/truncated input, ownership, cancellation and charset tests for each supported adapter. Reassess Core version-specific workarounds independently. Known-type codecs must remain directly compiled or generated and reflection-free.

## Primary sources

- [Jackson 3 migration guide](https://github.com/FasterXML/jackson/blob/main/jackson3/MIGRATING_TO_JACKSON_3.md): coordinates/packages, Java baseline, factory/API and exception changes.
- [Core 3.2.2 release](https://github.com/FasterXML/jackson/wiki/Jackson-Release-3.2.2).
- [Pinned Elasticsearch REST4 sniffer](https://github.com/elastic/elasticsearch/blob/v9.5.4/client/sniffer/src/main/java/org/elasticsearch/client/sniff/ElasticsearchNodesSniffer.java), [current main sniffer](https://github.com/elastic/elasticsearch/blob/main/client/sniffer/src/main/java/org/elasticsearch/client/sniff/ElasticsearchNodesSniffer.java).
- [Pinned REST5 sniffer](https://github.com/elastic/elasticsearch-java/blob/v9.5.4/rest5-client/src/main/java/co/elastic/clients/transport/rest5_client/low_level/sniffer/ElasticsearchNodesSniffer.java).
- [Pinned OpenSearch sniffer](https://github.com/opensearch-project/OpenSearch/blob/3.8.0/client/sniffer/src/main/java/org/opensearch/client/sniff/OpenSearchNodesSniffer.java).
