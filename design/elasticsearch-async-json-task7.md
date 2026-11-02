# Task 7: migration documentation and enforcement

Task 7 completes the async Elasticsearch JSON migration. All ordered implementation
checkpoints are complete, including Task 4's explicitly accepted performance tradeoffs.
The backend is async friendly: normal Search routes consume network chunks and produce
replayable output with bounded encoded storage. Materialized trees/scalar Strings,
synchronous token completion, signing and interceptors, and concrete pinned-client
exceptions remain. Completion does not establish constant memory or event-loop latency.

## User-facing documentation

The reference now includes [JSON trees and streaming](../documentation/src/main/asciidoc/public/reference/_backend-elasticsearch-json.adoc),
linked from existing native JSON sections. The
[migration guide](../documentation/src/main/asciidoc/migration/index.adoc) and
[What's New](../documentation/src/main/asciidoc/public/whats-new/index.adoc) describe the
new published utility and incompatible return-type/generic/client SPI changes.
Primary method names remain unchanged; examples use actual current Search JSON APIs.

The reference documents raw response/explain/source/hit/aggregation results,
transformer copy-on-first-access, native `JsonElement` field values, schema export
`List<JsonObject>` bodies, request/response/client-factory SPI services, strict native
String parsing, and the temporary deprecated Gson input bridges. The direct converter
allocates independent trees through traversal, preserves valid lexemes/order/nulls,
and is removed with all bridges in one future breaking release. Overloaded literal
null calls may require a cast. The test-only single-quoted `TestElasticsearchClient`
fixture boundary remains separate and must migrate when the bridge is removed.

Accessor documentation distinguishes typed navigation from async tree construction,
selects explicit adaptation at intermediate and terminal locations, and covers array
identity, missing/null, creation and write behavior. It retains intentional Task 2
corrections and numerical narrowing separately from type/array adaptation.

Ownership documentation covers bounded partial feed/position progress, EOF validation,
caller storage reuse, single-consumer sessions/cursors, cleanup, independent replay and
signing, request mutation discipline, partial sink consumption and scalar pause state.
The 8 KiB content-length probe leaves large lengths permanently `-1`. No worker hop
or latency guarantee is added; a future Vert.x transport is prepared but unimplemented.

Pinned-client limits are explicit: REST error constructors materialize a diagnostic
String using `EntityUtils.toString`; valid replay is normalized UTF-8 tree JSON rather
than original whitespace/encoding. Malformed diagnostics decode at most 8192 source
bytes plus a truncation marker and can replace a split code point. REST 4's 100 MiB
cap counts compressed input and now also cumulatively enforces unknown-length input;
HC5 caps count decompressed bytes before charset conversion. JDK has no default cap.
Gzip is incremental where not already decoded. Optional client request compression
still accumulates the complete compressed body. Normal OpenSearch requests use its
client-owned 8 KiB stream producer instead of Search `AsyncEntityProducer` callbacks.
HC5 cancellation can free Search state/cancel a future yet leave a stalled socket
open during the documented stock-client race; retries remain unchanged. Cleanup may
wait for active callback ownership. [Task 6](elasticsearch-async-json-task6.md) retains
source/probe provenance; [source-validation.tsv](elasticsearch-async-json-task7/source-validation.tsv)
records reverified source hashes and the relevant client source entries were reread.

## Automated protection and cleanup

`PublicApiDependencyTestUtils` uses the existing test-support Jandex dependency to
inspect compiled contracts without loading implementation classes. Checks cover
public/protected methods, constructors and fields, class and method generic bounds,
superclasses/interfaces, thrown types, annotation defaults and exposed annotations,
including CLASS-retained and type-use annotations. Implementations remain outside
the published contract. Each of util/json, backend, REST4, REST5, OpenSearch and AWS
runs its own compiled API check. Jackson 2/3 types are rejected everywhere in that
published scope; util/json additionally rejects Gson and its implementation types.
Ten negative compiled fixtures prove the checker rejects representative leaks.

The existing backend source check now scans all seven relevant production modules:
util/json, codec processor, backend, the three client modules and AWS. Gson references
are limited to the exact [five-file deprecated compatibility allowlist](elasticsearch-async-json-task7/gson-compatibility.tsv),
with no package-wide exemption. The allowlisted sources must still be deprecated and
document joint future removal. Negative fixtures include ordinary/whitespace-qualified
Gson imports, converter calls and reflective mapping operations. Generated codecs
remain neutral/direct-tree based. Existing unknown-registry-type runtime tests and
unsupported-processor-type compilation tests still fail rather than selecting reflection.
The generated audit remains [15 codecs, 58 direct decodes and 58 direct encodes](elasticsearch-async-json-task7/generated-codecs.tsv).

Scoped transitive Maven banned-dependency rules now reject Jackson 2/3 Databind in
the processor/backend/client/AWS modules; util/json retains its stronger Gson/Core2/
Databind ban. The build enforcer's unrelated Gson use, test/benchmark historical
implementations and ORM outbox polling's independent Databind requirement remain
legitimate boundaries. No repository-wide ban or Jackson upgrade is introduced.

Misleading `GsonEncoderDataStreamChannel`/`ClientRest5GsonContentProducer` names in
the two HC5 interceptor adapters become `EncoderDataStreamChannel`/
`AsyncEntityContentProducer`. This is an internal rename with unchanged behavior.
Task 5 already removed superseded production providers/adapters/accessors/paged
writers. The final source review found no additional obsolete writer to remove:
`JacksonJsonBodyWriter` remains the explicit synchronous comparison used by output
correctness tests and committed benchmark code. Legacy benchmark-local code and
historical evidence are preserved; no unrelated cleanup or runtime policy change is made.

## Verification actually run

[unit-tests.tsv](elasticsearch-async-json-task7/unit-tests.tsv) records the final
nonempty Surefire reports from the focused runs, before the full reactor clean.
All **906 cases pass**, with zero failures/errors/skips:

| Module | Cases |
| --- | ---: |
| JSON codec processor | 5 |
| util/json | 115 |
| Elasticsearch backend (including REST4 transport/entity fixtures) | 521 |
| REST4 module API | 1 |
| REST5 | 130 |
| OpenSearch | 130 |
| AWS module API | 1 |
| Documentation examples | 3 |

The focused commands were:

```sh
./mvnw clean install -pl util/internal/test/common -am -DskipTests -DskipITs -Dno-build-cache
./mvnw clean install -pl util/internal/test/common -DskipTests -DskipITs -Dno-build-cache
./mvnw clean test -pl util/json -DskipITs -Dno-build-cache
./mvnw clean install -pl util/json,build/json-processor,backend/elasticsearch,backend/elasticsearch-client/elasticsearch-rest4-client,backend/elasticsearch-client/elasticsearch-rest5-client,backend/elasticsearch-client/opensearch-rest-client,backend/elasticsearch-aws -DskipITs -Dno-build-cache
./mvnw clean install -pl backend/elasticsearch,backend/elasticsearch-client/elasticsearch-rest5-client,backend/elasticsearch-client/opensearch-rest-client,backend/elasticsearch-aws -DskipITs -Dno-build-cache
./mvnw clean verify -pl documentation -DskipITs -Dno-build-cache
./mvnw verify -pl documentation -DskipITs -Dno-build-cache
```

The first helper build caught the installed Jandex version's void `index` return;
the checker now uses `complete`. Initial checks also caught a source-pattern false
positive and style violations in test code; these were corrected and affected
modules rerun successfully. The utility/processor/REST4 successes from the initial
focused run and final backend/client/AWS rerun make up the table, without double
counting repeated runs. Documentation's final verify processes the reference,
migration guide and What's New HTML, compiles all documentation consumers and runs
the three included example tests. New JSON anchors/includes resolve in the HTML;
existing unrelated Asciidoctor INFO messages are not claimed fixed.

[additional-tests.tsv](elasticsearch-async-json-task7/additional-tests.tsv) records
**three more passing cases**, with zero skips. The new named-module fixture compiles
production at release 17 with only the existing backend requires, uses the model,
parser, accessor and request SPI, and runs on JDK 25. It asserts named modules,
implementation encapsulation, no opens and a nontransitive Core requirement:

```sh
./mvnw clean verify -pl integrationtest/java/modules/pojo-standalone-elasticsearch \
 '-Dit.test=JavaModulePathIT#jsonApiIsTransitiveAndImplementationIsEncapsulated' \
 -Dtest.elasticsearch.distribution=elastic -Dno-build-cache
```

The touched REST5/OpenSearch interceptors pass their dummy-credential signing/output
fixture against local WireMock with the pinned Elasticsearch 9.5.4 version probe:

```sh
HIBERNATE_SEARCH_AWS_STATIC_CREDENTIALS_ACCESS_KEY_ID=task7-test-access-key \
HIBERNATE_SEARCH_AWS_STATIC_CREDENTIALS_SECRET_ACCESS_KEY=task7-test-secret \
./mvnw -pl integrationtest/backend/elasticsearch dependency:properties \
 failsafe:integration-test@it-opensearch-client failsafe:integration-test@it-elasticsearch-rest5-client \
 failsafe:verify@it-opensearch-client failsafe:verify@it-elasticsearch-rest5-client \
 '-Dit.test=ElasticsearchContentLengthIT#payloadJustAboveBufferSize_requestPostProcessing' \
 -Dtest.elasticsearch.distribution=elastic \
 -Dtest.elasticsearch.connection.aws.signing.enabled=true \
 -Dtest.elasticsearch.connection.aws.region=us-east-1 \
 -Dtest.elasticsearch.connection.aws.credentials.type=static \
 -Dfailsafe.client.opensearch.summaryFile=/tmp/task7-os-signing.xml \
 -Dfailsafe.client.elasticsearch.rest5.summaryFile=/tmp/task7-rest5-signing.xml -Dno-build-cache
```

This verifies independent signing traversal followed by chunked output, not an AWS
endpoint or independent cryptographic signature validation. Task 6's 696 selected
live passes/eight skips, 54 factory/signing/vector seams and 32 final transport cases
remain prior evidence and are not claimed rerun in Task 7.

```sh
./mvnw clean install -DskipTests -DskipITs
./mvnw -Pdist -pl distribution process-resources -DskipTests -DskipITs -Dno-build-cache
./mvnw -Pdist -pl distribution validate -DskipTests -DskipITs -Dno-build-cache
java -jar integrationtest/performance/backend/elasticsearch/target/benchmarks.jar -l
```

The full clean reactor succeeds: **3152 goals, 2996 executed, 156 cached**. It compiles
production/tests/docs/ORM/module/benchmark consumers and runs configured style,
forbidden API, dependency/publication/BOM and Moditect checks; tests/ITs are skipped.
The new JPMS fixture is additionally clean-compiled/executed after its final edit.
Published flattened POMs, public BOM, distribution inclusion and descriptors retain
util/json correctly. No descriptor/BOM change was needed beyond prior wiring.
Aggregated Javadocs are actually built and contain JSON model/SPI pages; one existing
Jakarta Batch external-link module warning remains. Distribution validation passes.
The shaded performance jar is built by the reactor and lists all **45 JMH entries**.
The final boundary checker review also normalizes Windows path separators, without claiming
Windows execution. After reinstalling the helper, this targeted command reruns the
compiled API/source boundary checks (20 cases, not additional distinct suite cases):

```sh
./mvnw test -pl util/json,backend/elasticsearch,backend/elasticsearch-client/elasticsearch-rest4-client,backend/elasticsearch-client/elasticsearch-rest5-client,backend/elasticsearch-client/opensearch-rest-client,backend/elasticsearch-aws \
 -Dtest=JsonApiBoundaryTest,JsonCodecBoundaryTest -DskipITs -Dno-build-cache
```

Final formatting/checkstyle and `git diff --check` pass. Jackson Core stays at 3.2.2;
production compilation stays release 17 on host JDK 25.0.4.1.

No new timing matrix, full distribution ZIP, JDK 17 runtime, native-image, independent
AWS signature validation or whole backend live suite is claimed. No Task 7 requirement
remains unfulfilled; the accepted performance and pinned-client limits above remain
part of the completed project's documented scope.
