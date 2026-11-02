# Architecture checks

The `default` jQAssistant group includes `json-boundaries` from `rules/json.xml`.
These rules replace the JSON API boundary unit tests and their Jandex checker.
They distinguish public/protected signature dependencies from dependencies in
production implementation code. A public method returning a wrapper type does
not expose the wrapper's implementation or its own supertypes transitively.

The JSON rules cover the utility module, Elasticsearch backend, AWS integration,
the three REST clients, and the JSON annotation processor. The processor is
scanned when the `jqassistant` profile is enabled, including its generated
codecs when they are compiled as part of the backend. Tests are excluded.

Jackson 3 signature exposure is allowed only for the explicitly listed native
contracts and their nested types (plus backend logging generators). This exception
does not waive the restrictions on Jackson 2, backend dependencies or Gson.
Gson compatibility is restricted to individual existing backend classes, including
their nested classes. It does not exempt an entire package. Production dependencies
on reflective DTO mapping are checked separately, including `Class.getDeclared*`
calls. Unused imports, comments and textual mentions are not bytecode dependencies.

Run the full existing architecture checks with:

```sh
./mvnw clean install -Pjqassistant -DskipTests -DskipITs
```

A focused scan and analysis of the JSON rules is:

```sh
./mvnw -Pjqassistant \
  -pl build/json-processor,backend/elasticsearch-aws,backend/elasticsearch-client/elasticsearch-rest4-client,backend/elasticsearch-client/elasticsearch-rest5-client,backend/elasticsearch-client/opensearch-rest-client \
  -am clean verify -DskipTests -DskipITs \
  -Djqassistant.analyze.groups=json-boundaries
```

To apply the full default group to the resulting graph without rescanning:

```sh
./mvnw -N -Pjqassistant jqassistant:analyze -Djqassistant.analyze.groups=default
```

The report is `target/jqassistant/jqassistant-report.xml`.

## Compiled negative fixtures

The standalone `fixtures` project is excluded from the main reactor. It uses the
production utility artifact name to exercise the actual module-scoped rules and
has its own dependencies; it does not require installed Search snapshot artifacts.
Run it from the repository root:

```sh
./mvnw -f build/jqassistant/fixtures/pom.xml \
  clean compile jqassistant:scan jqassistant:analyze
```

**This command must fail:** the project deliberately contains forbidden
signatures and implementation dependencies. Inspect
`build/jqassistant/fixtures/target/jqassistant/jqassistant-report.xml` for the
specific failures. It runs the same `rules/json.xml`, with the same pinned
jQAssistant 2.9.1 Java scanner as the production check.

The signature constraint must report these fixture types:

- `GenericReturn`, `GenericInterface`, `GenericBound`, `MethodBound`, and `HandlerWithNativeBound`;
- `Superclass`, `NativeWrapper`, `Field`, `Constructor`, and `Throws`;
- `DirectArray`, `ArrayReturn`, and `WildcardArray`;
- `Annotated`, `ClassAnnotationOnly`, `ParameterAnnotation`, `AnnotationDefault`, and `AnnotationArrayDefault`;
- the native `spi.JsonCodec` fixture's **Gson** signature, while allowing its Jackson signature.

The implementation constraints must additionally report `GsonImplementationLeak`,
`BackendImplementationLeak`, `ReflectiveCall`, and `ReflectiveMapper`.

The signature constraint must **not** report `CleanSignature`, `PrivateUse`,
`Wrapper`, `CleanWrapper`, or `CleanNativeWrapper`. In particular, `NativeWrapper`
exposes a Jackson superclass in its own signature, but returning that wrapper
from `CleanNativeWrapper` does not make that interface expose Jackson directly.

## Java scanner limits

The pinned scanner preserves generic signatures, thrown types, CLASS-retained
declaration annotations, annotation class values, and scalar/array annotation
defaults. The compiled fixtures exercise those paths.

It does not scan type-use annotations: `TypeAnnotated` contains a Jackson class
value only in `List<@LibraryType(JsonParser.class) String>`, and is not reported.
It also does not scan record-component metadata: `RecordAnnotationOnly` contains
a class value in an annotation targeted only at `RECORD_COMPONENT`, and is not
reported. An annotation that javac also copies to a public accessor or constructor
parameter is still checked at that declaration site.

These are documented scanner limits, not exceptions in the rules. Recheck both
fixtures when upgrading jQAssistant; the old custom checker detected these two
forms. No custom scanner or parallel signature-checking implementation is kept.

## Validation recorded for the migration

The focused JSON reactor analysis passed. The standalone negative fixtures
produced 19 signature violations, two Gson implementation violations, two
reflective-mapping violations and one utility-to-backend violation, as expected.
The retained generated-code test and all six annotation processor tests passed.

Applying the full default group to the affected reactor graph at branch commit
`2010103c53` additionally found existing violations outside the new JSON rules:

| Existing constraint | Rows | Existing sources |
| --- | ---: | --- |
| `PublicTypesMayNotDeclareMethodsWithGetOrSetPrefix` | 1 | `JsonCodecService.getCodec` |
| `TypesMayNotDependOnImplementationTypeFromOtherModules` | 18 | The three REST clients depend on `JsonResponseBody`, `JsonGzipDecoder` and `JsonResponseConsumerScope` in the backend's `impl` package |
| `TypesShouldUseImplSuffixWithRestraint` | 2 | `ElasticsearchNativeIndexFieldTypeTypedMappingStepImpl` and `ElasticsearchNativeIndexFieldTypeTypedOptionsStepImpl` |

Those sources and the existing constraints were unchanged by this migration.
Enabling the JSON processor scan did not introduce additional default-rule
violations. The successful focused run does not imply that the full default
analysis passes.
