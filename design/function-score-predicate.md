# Function Score Predicate — Design Document

## Overview

Add a `functionScore()` predicate to Hibernate Search that allows modifying document relevance
scores using scoring functions. This bridges Elasticsearch's `function_score` query and
Lucene's `FunctionScoreQuery`.

## API Design

### Entry point

New method on `SearchPredicateFactory`:

```java
// With explicit inner predicate
f.functionScore(f.match().field("title").matching("robot"))
    .fieldValueFactor("popularity")
    .boostMode().multiply();

// Defaults to matchAll
f.functionScore()
    .fieldValueFactor("popularity")
    .boostMode().multiply();
```

### DSL Flow — Bool-style clause pattern

Functions are added as clause methods on `FunctionScoreClausesStep`, following the same
flow pattern as `bool().must().should()`. Each function method returns a config step that
**extends the clauses step**, so you can break out to add another function or set options
at any point (once required params are satisfied).

```
FunctionScoreClausesStep<SR, S extends ClausesStep<SR,S>>
    (extends PredicateFinalStep, PredicateScoreStep<S>)
  ├── .fieldValueFactor("field") → FieldValueFactorConfigStep extends ClausesStep
  │     ├── .modifier() → ModifierStep → .log1p()/... → FieldValueFactorConfigStep
  │     ├── .factor(float) → FieldValueFactorConfigStep
  │     ├── .missing(value) → FieldValueFactorConfigStep
  │     ├── .missing(value, ValueModel) → FieldValueFactorConfigStep
  │     ├── .weight(float) → FieldValueFactorConfigStep  ← per-function weight
  │     └── .filter(pred) / .filter(lambda) → FieldValueFactorConfigStep
  │
  ├── .weight(float) → WeightConfigStep extends ClausesStep  ← standalone weight function
  │     └── .filter(pred) / .filter(lambda) → ClausesStep
  │
  ├── .scoreMode() → ScoreModeStep<S> → .sum()/... → S
  ├── .boostMode() → BoostModeStep<S> → .multiply()/... → S
  ├── .boost(float) → S
  └── .constantScore() → S
```

**Single self-typing**: follows bool's pattern — everything on one step, all methods
return `S` (the self type). Functions and options can be mixed in any order, same as
`bool().must()` and `.minimumShouldMatch()`. No separate `OptionsStep`.

**ES extension** is straightforward:

```
ElasticsearchFunctionScoreClausesStep<SR>
    extends FunctionScoreClausesStep<SR, ElasticsearchFunctionScoreClausesStep<SR>>
  └── .decay(), .scriptScore(), .randomScore(), .maxBoost(float), .minScore(float)
```

No dual self-typing needed. Config steps carry 3 type params (`<SR, S, T>`).

**Inner predicate**: `functionScore()` accepts both `PredicateFinalStep` and lambda
`Function<SearchPredicateFactory, PredicateFinalStep>`, consistent with `bool().must()`.
Same for `.filter()` on individual functions.

If a function has required parameters beyond its method argument, the returned step does
NOT extend the clauses step until those params are provided (compile-time enforcement).

**Nested interaction**: `functionScore` needs nesting awareness. If `fieldValueFactor`
targets a field inside a nested object, or the inner predicate uses `nested()`, the Lucene
implementation must handle nested document structure (via `AbstractLuceneNestablePredicate`
or equivalent).

At least one function must be added — building a `functionScore` with no functions is a
build-time error.

**Multi-valued fields**: for `fieldValueFactor`, follow ES behavior — use the first value.
For `decay` (future), ES explicitly supports `multi_value_mode` (min/max/avg/sum).

### Field references

All field-accepting methods have overloads for both string paths and type-safe field references:

```java
// String path
.fieldValueFactor("popularity")
    .missing(1)                                 // Object, ValueModel.MAPPING default

// Type-safe field reference
.fieldValueFactor(MyEntity_.popularity)
    .missing(Ranking.OK)                        // T, type-safe via field reference
    .missing(2.0, ValueModel.INDEX)             // raw index value
```

Adding the `ScoreFunctions` trait category affects the field reference / metamodel system:

1. **New reference interfaces** under `engine/.../reference/scorefunction/`:
   - `ScoreFunctionFieldReference<SR> extends FieldReference<SR>` (base marker)
   - `FieldValueFactorFieldReference<SR, T>` (per function type, TYPED_INPUT — `T` carries
     the domain type for type-safe `.missing(T)` with ValueModel conversion)
   - `DecayFieldReference<SR>` (future)

2. **`TraitReferenceMapping`** — new entries mapping trait strings to reference interfaces:
   ```
   "score-function:field-value-factor" → FieldValueFactorFieldReference, label "SF0", TYPED_INPUT
   ```

3. **Generated metamodel classes** gain new label segments — e.g. a numeric field's reference
   class goes from `TypedFieldReferenceP0P2P6R1` to `TypedFieldReferenceP0P2P6R1SF0`.

No changes needed to `TraitKind`, `MetamodelClassWriter`, or `TypedFieldReferenceDetails` —
these are generic enough to handle new traits automatically.

### Field type validation — ScoreFunction type keys

Score functions are a new category of query element, at the same level as predicates, sorts,
projections, and aggregations. They get their own trait namespace and type key registry:

```
IndexFieldTraits
  ├── Predicates        → PredicateTypeKeys        (MATCH, RANGE, PHRASE, ...)
  ├── Sorts             → SortTypeKeys             (FIELD, DISTANCE, ...)
  ├── Projections       → ProjectionTypeKeys        (FIELD, DISTANCE, ...)
  ├── Aggregations      → AggregationTypeKeys       (RANGE, TERMS, ...)
  └── ScoreFunctions    → ScoreFunctionTypeKeys     (FIELD_VALUE_FACTOR, DECAY, ...)
```

Each key is parameterized with its builder type and registered only on compatible field types:

| Key | Builder type | Registered on |
|-----|-------------|---------------|
| `FIELD_VALUE_FACTOR` | `FieldValueFactorFunctionBuilder` | all numeric types (integer, long, float, double, BigDecimal, BigInteger) |
| `DECAY` (future) | `DecayFunctionBuilder` | numeric, date, geo_point |

The `functionScore` predicate itself is global (on `SearchPredicateBuilderFactory`), but
individual functions validate their target fields through these keys via the standard
`scope.fieldQueryElement(fieldPath, ScoreFunctionTypeKeys.FIELD_VALUE_FACTOR)` call.
Incompatible fields fail early with the standard error:
*"Cannot use 'score-function:field-value-factor' on field 'title': not available for fields of this type."*

### Methods over enums

Score modes, boost modes, and modifiers are expressed as DSL methods, not enums.
This allows promoting features from backend extensions to the common API incrementally —
adding a method is non-breaking.

### Scoring semantic model

This defines how scores are computed. Both backends must produce the same results.

#### Per-function weight

Every function has an optional per-function weight (default = 1). The weight multiplies
the function's raw output:

```
weightedScore_i = rawFunctionScore_i × weight_i    (default weight_i = 1)
```

The `.weight(float)` method on config steps sets this multiplier. The standalone
`.weight(42)` on the clauses step is a function that produces score = 42 (with implicit
per-function weight = 1). This mirrors ES, where the `weight` JSON key serves dual
purpose — per-function multiplier when combined with another function, standalone
function when alone.

#### Score modes (how multiple functions combine)

Applied to the weighted scores of all matching functions:

```
multiply:  Π(weightedScore_i)
sum:       Σ(weightedScore_i)
avg:       Σ(weightedScore_i) / Σ(weight_i)          ← WEIGHTED average, NOT / N
first:     weightedScore of first function whose filter matches
max:       max(weightedScore_i)
min:       min(weightedScore_i)
```

Note: `avg` uses **weighted average** — the denominator is the sum of weights, not the
count of functions. Functions with higher weights contribute more to the average AND
pull the denominator, matching ES behavior.

#### Boost modes (how combined function score merges with query score)

```
multiply:  queryScore × combinedFunctionScore         (default)
replace:   combinedFunctionScore
sum:       queryScore + combinedFunctionScore
avg:       (queryScore + combinedFunctionScore) / 2
max:       max(queryScore, combinedFunctionScore)
min:       min(queryScore, combinedFunctionScore)
```

#### Complete scoring pipeline

```
1. Evaluate inner query → queryScore
2. For each function i with matching filter:
     weightedScore_i = rawFunctionScore_i × weight_i
3. Combine via scoreMode:
     combinedFunctionScore = scoreMode(weightedScore_1, weightedScore_2, ...)
4. Merge with query via boostMode:
     finalScore = boostMode(queryScore, combinedFunctionScore)
5. Apply predicate-level boost/constantScore (if set):
     finalScore = finalScore × predicateBoost

Edge cases:
  - Function skipped (filter doesn't match): excluded from scoreMode combination
  - No functions match: combinedFunctionScore = identity (1.0 for multiply, 0.0 for sum)
  - Field missing + no .missing(): function produces 0 (verify against ES behavior)
  - Field missing + .missing(v): substitute v, apply modifier(factor × v) normally
```

See the scoring semantic model above for the precise formulas.

```java
.boostMode().multiply()    // default
.boostMode().replace()
.boostMode().sum()
.boostMode().avg()
.boostMode().max()
.boostMode().min()

.scoreMode().multiply()    // default
.scoreMode().sum()
.scoreMode().avg()         // weighted average — Σ(score_i * w_i) / Σ(w_i)
.scoreMode().first()
.scoreMode().max()
.scoreMode().min()
```

#### Field value factor modifiers

```java
.modifier().none()         // no transformation (default)
.modifier().log()          // log(value)
.modifier().log1p()        // log(1 + value)
.modifier().log2p()        // log(2 + value)
.modifier().ln()           // ln(value)
.modifier().ln1p()         // ln(1 + value)
.modifier().ln2p()         // ln(2 + value)
.modifier().square()       // value^2
.modifier().sqrt()         // sqrt(value)
.modifier().reciprocal()   // 1/value
```

### Defaults

Both `scoreMode` and `boostMode` default to `multiply` (matching Elasticsearch/OpenSearch).

If the user doesn't call `.scoreMode()` / `.boostMode()`, the DSL doesn't call the
corresponding builder method. On the **ES backend**, the builder fields stay null — null
fields are not serialized to JSON, so ES applies its own `multiply` default. On the
**Lucene backend**, the builder initializes these fields to `multiply` internally as
the fallback default.

### Predicate-level boost and constantScore

`FunctionScoreClausesStep` extends `PredicateScoreStep`, providing standard predicate-level
score modification:

```java
f.functionScore(f.match().field("title").matching("robot"))
    .fieldValueFactor("popularity").modifier().log1p()
    .boostMode().multiply()
    .boost(2.0f)           // standard predicate boost, applied on top
    .constantScore();      // force constant score
```

This is separate from `boostMode` — `boost()`/`constantScore()` wrap the entire predicate
result, just like on any other predicate.

### Complete usage examples

```java
// Simple: boost by popularity
f.functionScore(f.match().field("title").matching("robot"))
    .fieldValueFactor("popularity")
        .modifier().log1p()
        .factor(2)
        .missing(1)
    .boostMode().multiply();

// Multiple functions with per-function weights and weighted avg
f.functionScore(f.match().field("title").matching("robot"))
    .fieldValueFactor("popularity").modifier().log1p().factor(2).weight(3)
    .weight(42).filter(f.match().field("featured").matching(true))
    .scoreMode().avg()       // weighted avg: (fvf*3 + 42*1) / (3 + 1)
    .boostMode().multiply();

// Multiple functions, sum mode
f.functionScore(f.match().field("title").matching("robot"))
    .fieldValueFactor("popularity").modifier().log1p().factor(2)
    .weight(42).filter(f.match().field("featured").matching(true))
    .scoreMode().sum()
    .boostMode().multiply();

// Weighted blend: relevance + dampened rank boost
f.functionScore(f.match().field("title").matching("robot"))
    .fieldValueFactor("rank")
        .factor(0.5f)
        .modifier().log1p()
    .boostMode().sum();

// Type-safe field reference with enum via bridge
f.functionScore(f.match().field("title").matching("robot"))
    .fieldValueFactor(MyEntity_.ranking)
        .missing(Ranking.OK)
        .modifier().log1p()
    .boostMode().multiply();

// Weight without filter (constant multiplier for all docs)
f.functionScore(f.match().field("title").matching("robot"))
    .weight(2.5f)
    .boostMode().multiply();

// Conditional weight with filter
f.functionScore(f.match().field("title").matching("robot"))
    .weight(42).filter(f.match().field("featured").matching(true))
    .boostMode().multiply();

// fieldValueFactor with per-function filter
f.functionScore(f.match().field("title").matching("robot"))
    .fieldValueFactor("popularity")
        .modifier().log1p()
        .filter(f.match().field("category").matching("books"))
    .boostMode().multiply();
```

### Elasticsearch extension

Additional functions available only through the ES extension:

```java
f.extension(ElasticsearchExtension.get())
    .functionScore(f.match().field("title").matching("robot"))
    .fieldValueFactor("popularity").modifier().log1p()
    .decay("date").gauss()
        .origin("now").scale("10d").offset("2d").decay(0.5)
    .scriptScore("_score * doc['boost'].value")
    .randomScore().seed(42).field("_seq_no")
    .scoreMode().sum()
    .boostMode().multiply()
    .maxBoost(42)
    .minScore(5);
```

## Decisions

| # | Decision | Rationale |
|---|----------|-----------|
| 1 | Global predicate, not field-scoped | `functionScore` wraps another predicate, like `bool`. Goes through `SearchPredicateBuilderFactory`, not `PredicateTypeKeys` |
| 2 | Bool-style clause flow | Functions are clause methods on a clauses step. Config steps extend clauses step for optional params, don't extend for required params. Consistent with existing DSL patterns |
| 3 | Methods over enums for modes/modifiers | Allows incremental promotion of features from ES extension to common API without breaking changes |
| 4 | Field references with overloads (TYPED_INPUT) | Both `String` field paths and type-safe field references. TYPED_INPUT because `.missing()` needs ValueModel conversion (e.g. enum via bridge → numeric) |
| 5 | All 6 boost modes in common API | Once custom Lucene `DoubleValuesSource` for `sum` exists, `avg`/`max`/`min` are trivial additions |
| 6 | All 6 score modes in common API | Same rationale — composite `DoubleValuesSource` handles all modes with a switch |
| 7 | New `ScoreFunctions` category in `IndexFieldTraits` | Score functions are their own concern (like predicates, sorts, projections, aggregations). Own trait namespace (`score-function:*`), own `ScoreFunctionTypeKeys` registry. Individual keys registered per field type for validation |
| 8 | Inner predicate defaults to matchAll | Optional argument to `functionScore()` |
| 9 | At least one function required | Building `functionScore` with no functions is a build-time error — pointless without functions |
| 10 | `.filter()` on all function types | Every function (fieldValueFactor, weight) supports an optional per-function filter, matching ES behavior |
| 11 | `weight` dual role: standalone function + per-function multiplier | `.weight(42)` on ClausesStep = standalone function (ES: `{"weight": 42}`). `.weight(3)` on config steps = per-function multiplier (ES: `{"field_value_factor": {...}, "weight": 3}`). Mirrors ES's overloading of the `weight` key |
| 12 | Defaults: scoreMode=multiply, boostMode=multiply | Match ES/OpenSearch defaults. ES backend: omit from JSON. Lucene backend: set internally |
| 13 | `boost()`/`constantScore()` via PredicateScoreStep | Standard predicate-level score modification, separate from boostMode. Applied on top of entire functionScore result |
| 14 | `.missing()` with ValueModel overloads | `.missing(value)` defaults to ValueModel.MAPPING; `.missing(value, ValueModel)` for explicit control. Needed for enum-via-bridge scenarios |
| 15 | OpenSearch fully compatible | `function_score` JSON is identical between ES and OpenSearch. Single implementation, no dialect handling needed |
| 16 | Single step, no ordering enforcement (bool pattern) | Everything on one `FunctionScoreClausesStep<SR, S>`. Functions and options mixable in any order, like bool. Avoids dual self-typing complexity |
| 17 | Single self-typing on ClausesStep | `ClausesStep<SR, S>` — one self-type. ES extension just extends and adds methods. Config steps carry 3 type params (`<SR, S, T>`) |
| 18 | Lambda support for inner predicate and filters | Consistent with `bool().must()` — accept both `PredicateFinalStep` and `Function<SearchPredicateFactory, PredicateFinalStep>` |
| 19 | Nesting awareness | `functionScore` handles nested object fields, same as other predicates via `AbstractLuceneNestablePredicate` |
| 20 | `float` for `factor` and `weight` | Consistent with `boost(float)` on `PredicateScoreStep`. Lucene also uses `float` for boost values |
| 21 | `scoreMode().first()` preserves insertion order | Lucene composite `DoubleValuesSource` evaluates functions in order, short-circuits on first filter match |
| 22 | Sub-step pattern (without `.end()`) for modifier/scoreMode/boostMode | Matches existing `missing().first()` pattern on sorts. Single mutually-exclusive choice, each option returns to parent. Keeps parent step interface clean, groups related options. No `.end()` needed |
| 23 | Multi-valued fields: use first value | Follows ES `field_value_factor` behavior. Future `decay` can add explicit `multi_value_mode` |
| 24 | All numeric types supported including BigDecimal/BigInteger | `FIELD_VALUE_FACTOR` registered on all numeric field types |
| 25 | Explicit scoring semantic model | Full pipeline documented: per-function weight (default 1), scoreMode with weighted avg formula, boostMode, predicate-level boost. Both backends must produce identical results |

## Discarded Alternatives

| Alternative | Why discarded |
|-------------|---------------|
| Enums for score/boost modes | Less extensible; harder to promote features from ES extension to common API |
| `.add(fn -> ...)` lambda pattern for functions | Bool-style clause methods are more natural and consistent with existing DSL |
| Single-function-only common API | Multi-function with `scoreMode` is achievable on Lucene via composite `DoubleValuesSource`; no reason to limit |
| `script_score` in common API | Inherently ES-specific (Painless scripts). Left to ES extension |
| `functionScore` as field-scoped predicate | It wraps a query, not a field. The individual functions reference fields |
| Score function keys in `PredicateTypeKeys` | Score functions are not predicates — they're a distinct concern. Own category keeps the type key system clean and extensible |
| UNTYPED field references for fieldValueFactor | `.missing()` needs ValueModel conversion for bridged types (e.g. enum → numeric). TYPED_INPUT required |
| Flat methods for modifier (`.modifierLog1p()`) | Sub-step pattern groups 10 options behind one entry point, keeps `ConfigStep` clean. Consistent with `missing().first()` on sorts |
| Separate `.constant(value)` function type | No clean ES mapping for `.constant(10).weight(2)` — ES overloads `weight` as both function and multiplier. Keeping `.weight()` as standalone function mirrors ES directly |
| Functions-before-options ordering (dual self-typing) | Required `ClausesStep<SR, S, O>` with 4-param generics on config steps, separate `OptionsStep`, complex ES extension wiring. Bool pattern (single step) is much simpler for negligible benefit |

## Backend Implementation

### Lucene

Custom components needed:

1. **Composite `DoubleValuesSource`** — wraps N `DoubleValuesSource` instances, combines
   per `scoreMode`. Each source can have an optional filter (Lucene `Query`) and weight.
   Must preserve insertion order for `scoreMode().first()` (ordered evaluation with
   short-circuit on first matching filter).

2. **Boost mode handling** — Lucene's `FunctionScoreQuery` natively supports `multiply`
   (`boostByValue`) and `replace` (constructor). For `sum`/`avg`/`max`/`min`, use
   `FunctionScoreQuery` in **replace** mode with a `DoubleValuesSource` that internally
   reads the inner query score via `DoubleValuesSource.SCORES` and computes the final
   combined value itself (e.g. for `sum`: `SCORES + functionValue`, for `avg`:
   `(SCORES + functionValue) / 2`). The source must return `needsScores() = true` to
   access `SCORES`. The "replace" is technical — the replacement value already
   incorporates the original score.

3. **`fieldValueFactor`** — **cannot** use `DoubleValuesSource.fromDoubleField()` directly.
   All Lucene numeric doc values are stored as raw longs with type-specific encoding:
   - Integer: raw long is the int value widened to long
   - Long: raw long is the value directly
   - Float: raw long is `NumericUtils.floatToSortableInt()` widened to long
   - Double: raw long is `NumericUtils.doubleToSortableLong()`

   Must use type-aware decoding via `LuceneNumericDomain.sortedDocValueToDouble(long)`.
   Prefer the aggregation pattern (`codec::sortedDocValueToDouble` as a `Function<Long, Double>`)
   over extending `DoubleMultiValuesToSingleValuesSource` with new factory methods — it
   already handles all four numeric types in one method, no branching needed.
   **Revisit exact approach before starting implementation.**

   The `FieldValueFactorFunctionBuilder` Lucene implementation gets the codec/domain from
   the field type (available through `ScoreFunctionTypeKeys.FIELD_VALUE_FACTOR` resolution)
   and uses it to create the correctly-typed source.

   The `missing` value goes through the convert-and-encode pipeline (ValueModel) before
   being used as the `DoubleValues.withDefault()` fallback. `modifier` and `factor` are
   applied as a wrapping `DoubleValuesSource`: `modifier(factor * fieldValue)`.

4. **Missing-value semantics** — critical for correctness. Lucene's `FunctionScoreQuery`
   has different behavior depending on construction:
   - `new FunctionScoreQuery(query, source)` → score = **0** if source has no value
   - `boostByValue(query, source)` → **original score preserved** if source has no value

   This distinction is observable with missing fields and filters. The design must define
   explicit behavior for each edge case:

   | Scenario | Behavior |
   |----------|----------|
   | Field missing + `.missing()` set | Use missing value as substitute (apply modifier + factor) |
   | Field missing + no `.missing()` | Match ES: function produces 0 (or error — verify ES behavior) |
   | Per-function filter doesn't match | Function is **skipped** — does not participate in scoreMode combination |
   | No functions match any filter | scoreMode result = identity value (1.0 for multiply, 0.0 for sum/avg/max/min) |
   | Inner query score = 0 | boostMode applies normally (multiply by 0 = 0, sum adds function score, replace ignores it) |

   **Required test matrix** — all combinations of:
   - function matches / doesn't match (filter)
   - field exists / missing (with and without `.missing()`)
   - inner query score = 0 vs non-zero
   - boostMode: multiply, sum, replace (at minimum)
   - scoreMode: multiply, sum, avg (at minimum)

   The Lucene implementation must choose the right `FunctionScoreQuery` construction to
   match ES semantics for each boostMode. `boostByValue` for multiply (preserves score on
   missing), constructor for replace, custom source wrapping `SCORES` for sum/avg/max/min.

5. **Nesting awareness** — if `fieldValueFactor` targets a field inside a nested object,
   the `DoubleValuesSource` must be nested-aware (nested docs are separate Lucene documents).
   Follow the pattern from `DoubleMultiValuesToSingleValuesSource` which already accepts a
   `NestedDocsProvider`.

### Elasticsearch / OpenSearch

The `function_score` query JSON is identical between Elasticsearch and OpenSearch — same
structure, same functions, same parameters. A single implementation works for both
distributions. No dialect-specific handling or version gating needed (available in all
supported versions: ES 7.10+, OpenSearch 1.3+).

Direct mapping to `function_score` query JSON:

```json
{
  "function_score": {
    "query": { ... },
    "functions": [
      {
        "field_value_factor": {
          "field": "popularity",
          "modifier": "log1p",
          "factor": 2,
          "missing": 1
        },
        "weight": 3,
        "filter": { ... }
      },
      { "weight": 42, "filter": { ... } }
    ],
    "score_mode": "avg",
    "boost_mode": "multiply"
  }
}
```

ES mapping for `.weight()`:
- `.fieldValueFactor("popularity").weight(3)` → `{"field_value_factor": {...}, "weight": 3}` —
  per-function multiplier alongside another function
- `.weight(42)` → `{"weight": 42}` — standalone weight function (ES treats `weight` as
  the function when no other function is present)

`score_mode` and `boost_mode` are omitted from JSON when using the default (`multiply`).

## Testing

Follow existing predicate testing patterns:

- **TCK tests** (common API) — nested test classes, top-level index definitions, iterate
  over all compatible field types. Cover: single function, multiple functions, all score
  modes, all boost modes, all modifiers, missing values, per-function filters, field type
  validation (error on non-numeric), boost/constantScore, interaction with nested predicates.
- **Backend-specific TCK tests** — ES extension features (scriptScore, randomScore, decay,
  maxBoost, minScore).
- **Unsupported type tests** — verify early failure with clear error message when using
  fieldValueFactor on keyword/text/date/geo fields.

### Score matrix tests (predicate-specific)

Mathematical tests that verify exact scores across both backends. These catch backend
discrepancies quickly.

**Single function — all boostMode combinations (queryScore=10, functionScore=2):**

| queryScore | functionScore | scoreMode | boostMode | expected |
|------------|---------------|-----------|-----------|----------|
| 10 | 2 | multiply | multiply | 20 |
| 10 | 2 | multiply | replace | 2 |
| 10 | 2 | multiply | sum | 12 |
| 10 | 2 | multiply | avg | 6 |
| 10 | 2 | multiply | max | 10 |
| 10 | 2 | multiply | min | 2 |

**Multiple functions — all scoreMode combinations (f1=2, f2=3):**

| f1 | f2 | scoreMode | combined |
|----|-----|-----------|----------|
| 2 | 3 | multiply | 6 |
| 2 | 3 | sum | 5 |
| 2 | 3 | avg | 2.5 |
| 2 | 3 | max | 3 |
| 2 | 3 | min | 2 |
| 2 | 3 | first | 2 (first matching) |

**Weighted average (f1=2 weight=3, f2=3 weight=4):**

| f1 | w1 | f2 | w2 | scoreMode | combined |
|----|----|----|-----|-----------|----------|
| 2 | 3 | 3 | 4 | avg | (2×3 + 3×4) / (3+4) = 18/7 ≈ 2.571 |
| 2 | 3 | 3 | 4 | sum | 2×3 + 3×4 = 18 |
| 2 | 3 | 3 | 4 | multiply | (2×3) × (3×4) = 72 |

**Edge cases:**

| scenario | scoreMode | boostMode | expected |
|----------|-----------|-----------|----------|
| field missing + `.missing(5)` | multiply | multiply | queryScore × modifier(factor × 5) |
| field missing + no `.missing()` | multiply | multiply | queryScore × 0 = 0 (verify vs ES) |
| filter doesn't match (1 of 2 functions) | sum | multiply | queryScore × matchingFunctionScore |
| no functions match any filter | sum | multiply | queryScore × 0 = 0 (verify vs ES) |
| inner query score = 0 | multiply | sum | 0 + functionScore |
