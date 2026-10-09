# Task 5: reflection-free codecs and Search JSON backend migration

Task 5 migrates ordinary Elasticsearch processing to Search JSON trees and explicit
codecs. Task 6's demand-aware publishers, custom per-attempt response consumers,
transport cancellation and scheduling policies remain separate work.

## Implementation

`build/json-processor` replaces the Gson processor. It retains compile-time
inheritance, annotation, getter/setter and field-order analysis, and generates
constructors and direct accessor calls over Search nodes. `JsonCodec`,
`JsonCodecRegistry` and `JsonCodecs` in `util/json` provide explicit registrations,
deferred typed references for recursive model graphs, and scalar/list/string-map
composition. They expose no Gson or Jackson types and provide no runtime field
inspection or reflective fallback. Unsupported generic, array and primitive model
field types fail compilation rather than selecting a reflective mapper.

All 14 historically generated schema types and the dynamic, routing, named-template
and format representations are migrated. `IndexMetadata` additionally has a
generated diagnostic codec; its enclosing `ExistingIndexMetadata` diagnostic tree
is constructed explicitly. Known Java fields retain sorted order and extras retain
insertion order. Member-null omission applies recursively inside extra trees;
null array positions remain present. The serialize-nulls service retains explicit
nulls. Routing OPTIONAL remains read-only, dynamic values remain strings, named
templates require exactly one member, and format trailing-segment behavior remains
unchanged.

Schema, document, validation, predicate, sort, aggregation, projection and work
paths use Search nodes. Metadata decode and schema copies use direct codecs.
Aliases are encoded with an explicitly composed map codec. ES 7 aggregation long
values retain their string representation, including JSON-null handling. Native
values and raw responses retain node identity; transformers retain structural
copy-on-first-body-access isolation. Schema export retains compact formatting and
streams cursor bytes directly to the file.

Accessor consumers now select `unwrapSingletonArrays()` at intermediate object
and terminal scalar/object locations. Array access and array indexing retain their
normal container behavior. Task 2's deliberate corrections remain: clearer
exceptions for adapted absence/multiple-member arrays, strict JSON grammar and
corrected numeric equality. Characterization tests were migrated accordingly;
legacy Gson model observations remain a distinct historical fixture. Direct native String fixture inputs now spell the same JSON using standard double
quotes, and malformed custom-file assertions check contextual backend errors plus
the new parser category. The historical TestElasticsearchClient fixture constructor
retains its single-quoted mapping/settings/alias convenience through the deprecated
direct tree converter. This test-only legacy-input boundary parses the supplied
fixture with Gson, converts once to Search nodes, and performs no text reparse or
request/response conversion. It avoids treating legacy fixture syntax as a change
to the strict production parser. It must migrate with the converter when the
compatibility bridge is removed.

## API and compatibility

Primary native predicate, sort, aggregation and mapping inputs use Search nodes.
Deprecated Gson input default methods perform one direct tree conversion and call
the primary overload. Raw projections, response bodies, explanations, transformer
bodies, native field values, schema export and client SPI now use Search types.
These return-type/generic/SPI changes are accepted source and binary breaks; no
legacy output facade was added. String input methods remain and use the character
parser for the original supplied String.

The deprecated public `org.hibernate.search.backend.elasticsearch.gson.GsonJsonConversion`
provides element/object/array overloads in both directions. Conversion traverses
nodes, preserves valid number lexemes, order and nulls, and creates independent
mutable containers without serialization/reparsing. It incurs traversal and
allocation costs; Java null remains Java null. Literal null arguments to overloaded
inputs may require an explicit cast. The converter and all Gson input bridges are
scheduled for removal together in a future breaking release.

Production Gson references are limited to the five files in
[gson-compatibility.tsv](elasticsearch-async-json-task5/gson-compatibility.tsv).
The backend retains the dependency for those signatures/implementations. The three
REST client modules no longer declare Gson. The build enforcer's unrelated Gson
use and benchmark/test-only historical fixtures are outside this backend allowlist.
The TestElasticsearchClient legacy fixture boundary is recorded separately in
[test-fixture-compatibility.tsv](elasticsearch-async-json-task5/test-fixture-compatibility.tsv).
Obsolete production providers, adapters, accessors and paged writers were removed.
Historical provider, models, generated adapters and output implementations are
captured under the benchmark module's `legacy` package so reusable measurements
remain buildable without retaining dead backend code.

## Necessary Task 5 transport seam

All four existing client adapters compile and operate with the new SPI. Their
existing response InputStreams feed parser sessions in bounded 8 KiB reads, with
explicit HTTP charset selection and contextual parsing errors. Empty/whitespace
streams remain absent bodies: a library-neutral `JsonEmptyInputException` lets the
boundary distinguish that case while the parser itself still rejects missing roots.
Custom schema resources use the same byte-fed route, then registered model decode;
callers retain stream ownership and close streams on success/failure.

Entities use the existing replayable `ElasticsearchJsonBody`/`JsonBody` output
primitives and bounded cursor storage. Encoder byte counts advance the adapter's
own buffer, preserving old encoder lifecycle behavior. Signing streams and repeated
reads are independent cursor traversals. The bounded length probe yields a stable
known length for bodies that fit and `-1` for larger bodies; completing output does
not replace that value. The old ability to learn a large length during signing is
intentionally removed rather than reintroducing complete-body serialization.

This seam does not implement Task 6: JDK still maps `ofInputStream()` and publishes
from an input-stream supplier; REST clients still use their default buffered
response consumers. The REST defaults' response caps/client-owned buffering,
compression wrappers and error-constructor String materialization remain external
limitations documented by the earlier audit. Per-attempt consumers, demand,
cancellation, response-limit application and transport wakeup policy still need
Task 6 verification. Disabled success logging retains its guard; explicit diagnostic
String formatting and raw String projection/DSL contracts remain supported.

## Route audit

[parsing-routes.tsv](elasticsearch-async-json-task5/parsing-routes.tsv) records the
current String, byte-stream and existing-tree entry points.
[generated-codecs.tsv](elasticsearch-async-json-task5/generated-codecs.tsv) records
all 15 generated codecs: 58 direct field decodes and encodes, with no text-parse
route. Original caller Strings select the character parser. Original
bytes select bounded parser sessions. Existing response/schema trees select direct
registered codecs or structural copies. Generated code and compatibility conversion
contain no tree-to-text-to-parser route. File export uses cursor bytes directly.
The formatter's bounded UTF-8-to-character adapter serves explicit String/Writer
outputs, never response input or codec decoding. No new ordinary helper retains a
complete serialized request/response copy. See the updated
[internal-route audit](elasticsearch-json-internal-path-audit.md).

## Verification

The full reactor succeeds with `./mvnw clean install -DskipTests -DskipITs`:
3,147 goals, 2,904 executed and 243 restored from the build cache. This compiles
production, test and documentation consumers, ORM and Java-module consumers,
benchmark sources, and applies the configured style, forbidden-API, dependency and
Moditect checks. The resulting util/json module requires Jackson Core and util/common,
exports the Search node/SPI packages, and contains no Gson or Databind requirement.
The report-coverage dependency list now includes util/json, fixing the omission found
by the full reactor.

Focused tests run with:

```sh
./mvnw test -pl build/json-processor,util/json,backend/elasticsearch,backend/elasticsearch-client/elasticsearch-rest5-client,backend/elasticsearch-client/opensearch-rest-client,backend/elasticsearch-aws -DskipITs
```

The source-boundary test initially over-rejected an existing non-mapping `Array`
operation; after narrowing it to reflective field/method mapping, the affected
backend/client modules were rerun successfully. The processor's final source
format/name cleanup was checked with `./mvnw test -pl build/json-processor
-Dformat.skip=false -DskipITs`. All 33 final nonempty Surefire XML reports have zero
failures, errors or skips:

| Module | Cases |
| --- | ---: |
| JSON codec processor | 5 |
| util/json | 105 |
| Elasticsearch backend, including REST 4 entity matrix | 486 |
| REST 5 entity matrix | 112 |
| OpenSearch entity matrix | 112 |
| **Total** | **820** |

The same selected live fixtures succeed against the repository-pinned Elasticsearch
9.5.4 and OpenSearch 3.8.0 Docker images, with all four client executions on each:

```sh
./mvnw clean verify -pl integrationtest/backend/elasticsearch -Dit.test=ElasticsearchExtensionIT,ElasticsearchFieldTypesIT,ElasticsearchFieldAttributesIT,ElasticsearchCustomIndexMappingIT,ElasticsearchCustomIndexSettingsIT,ElasticsearchSearchQueryIT,ElasticsearchSearchQueryRequestTransformerIT,ElasticsearchIndexSchemaManagerExporterIT,ElasticsearchJsonCodecConcurrencyIT,ElasticsearchContentLengthIT -Dtest.elasticsearch.distribution=elastic
# Repeat with -Dtest.elasticsearch.distribution=opensearch
```

[live-tests.tsv](elasticsearch-async-json-task5/live-tests.tsv) records 80 nonempty
Failsafe XML reports, 640 reported cases, zero failures/errors and eight expected
AWS-profile skips: **632 executed live cases pass**. Coverage includes native
predicate/sort/aggregation/mapping, raw result/explain/response identity, transformer
isolation, native field types, schema copies/export, codec concurrency and bounded
content-length framing. These are selected live fixtures, not a claim that the
whole backend integration suite ran.

The focused REST factory methods `simple_http`, `error`, `unparseable`,
`multipleHosts_failover_serverError` and `clientInstance` pass for REST 4, REST 5
and OpenSearch: 15 cases. Each class runs in its matching client execution; the
repository intentionally excludes the other client dependencies from that execution.
For example:

```sh
./mvnw -pl integrationtest/backend/elasticsearch dependency:properties failsafe:integration-test@it failsafe:verify@it '-Dit.test=ClientRest4ElasticsearchClientFactoryIT#simple_http+error+unparseable+multipleHosts_failover_serverError+clientInstance' -Dtest.elasticsearch.distribution=elastic
# REST 5: ClientRest5ElasticsearchClientFactoryIT and @it-elasticsearch-rest5-client
# OpenSearch: ClientOpenSearchElasticsearchClientFactoryIT and @it-opensearch-client
```

[adapter-seams.tsv](elasticsearch-async-json-task5/adapter-seams.tsv) records the
final fixture counts. The large-body AWS post-processing fixture also passes with all four clients,
using dummy static credentials against local WireMock and the pinned local version
probe. It checks signing-stream traversal followed by actual chunked request output;
it does not validate an AWS endpoint or independently verify the SigV4 signature.
The standalone invocations use fresh summary files because Failsafe otherwise
merges prior standalone executions:

```sh
task5_signing_dir=$(mktemp -d)
HIBERNATE_SEARCH_AWS_STATIC_CREDENTIALS_ACCESS_KEY_ID=task5-test-access-key \
HIBERNATE_SEARCH_AWS_STATIC_CREDENTIALS_SECRET_ACCESS_KEY=task5-test-secret \
./mvnw -pl integrationtest/backend/elasticsearch dependency:properties \
  failsafe:integration-test@it failsafe:integration-test@it-opensearch-client \
  failsafe:integration-test@it-elasticsearch-rest5-client failsafe:integration-test@it-jdk-client \
  failsafe:verify@it failsafe:verify@it-opensearch-client \
  failsafe:verify@it-elasticsearch-rest5-client failsafe:verify@it-jdk-client \
  '-Dit.test=ElasticsearchContentLengthIT#payloadJustAboveBufferSize_requestPostProcessing' \
  -Dtest.elasticsearch.distribution=elastic \
  -Dtest.elasticsearch.connection.aws.signing.enabled=true \
  -Dtest.elasticsearch.connection.aws.region=us-east-1 \
  -Dtest.elasticsearch.connection.aws.credentials.type=static \
  -Dfailsafe.client.elasticsearch.summaryFile="$task5_signing_dir/rest4.xml" \
  -Dfailsafe.client.opensearch.summaryFile="$task5_signing_dir/opensearch.xml" \
  -Dfailsafe.client.elasticsearch.rest5.summaryFile="$task5_signing_dir/rest5.xml" \
  -Dfailsafe.client.jdk.summaryFile="$task5_signing_dir/jdk.xml"
```

The historical single-quoted vector mapping helper is exercised by
`ElasticsearchIndexSchemaManagerValidationMappingBaseIT#vector_success_1+vector_success_2`
on Elasticsearch 9.5.4 and OpenSearch 3.8.0 with REST 4: eight cases pass, covering
both create-or-validate and validate, plus nested vector method parameters. These
standalone runs use `dependency:properties`, `failsafe:integration-test@it`,
`failsafe:verify@it` and fresh `failsafe.client.elasticsearch.summaryFile` paths.
Together the additional factory, signing and vector seams contribute **27 passing
cases**, with zero skips. The test-only compatibility fixture boundary leaves the
production character parser and transport byte routes unchanged.

The final test-support module is separately clean-built with
`./mvnw clean install -pl util/internal/integrationtest/backend/elasticsearch
-DskipTests -DskipITs`, including its configured style and forbidden-API checks.

`./mvnw -Pdist -pl distribution validate -DskipTests -DskipITs` succeeds.
The shaded benchmark jar lists all 45 JMH entries with `java -jar
integrationtest/performance/backend/elasticsearch/target/benchmarks.jar -l`.
No new timing campaign was run: Task 4's raw evidence, runner and provisionally
accepted throughput limitations remain unchanged.

JDK 25.0.4.1 compiles production at release 17. No JDK 17 runtime, native-image,
AWS endpoint or independent SigV4 signature verification, full distribution ZIP or aggregated Javadoc
execution is claimed. Task 6's transport policies and Task 7's full migration
documentation remain separate checkpoints.
