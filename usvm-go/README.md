# Experimental Go frontend

This module imports the SSA/JacoDB Go prototype from `buraindo/go-jacodb`
(commit `717bd41613fbc4bb8d8a0af43f3900bab824dcb8`) and adapts it to the current USVM core.
It is an experimental frontend, with the validation and limitations below.

## Build and tests

Use a Go installation that supports `GOTOOLCHAIN` and Java 11 or later. Gradle pins
Go to **1.22.3**, matching `src/main/go/go.mod`. The pinned `x/tools v0.24.0` exporter
is incompatible with Go 1.26. The Go-specific JacoDB API is pinned separately to
`816194b963`; it does not change other frontends' JacoDB dependencies.

From the repository root:

```sh
./gradlew :usvm-go:check :usvm-go:detektMain :usvm-go:detektTest --configure-on-demand
```

The test task generates SSA JSON and the native oracle under `usvm-go/build/generated/go`.
No checked-in dumps or pre-existing `out` directory are required. Tests have finite
machine/solver timeouts, close machines, and are bounded by a 15-minute Gradle timeout.

## Test organization and symbolic expectations

Tests follow the Java frontend's `TestRunner` pattern under `org.usvm.samples`:

- `arithmetic`, `arrays`, `collections.maps`, `collections.slices`, `strings`;
- `controlflow`, `calls`, `exceptions`, `globals`, `objects`, `pointers`, `types`, `algorithms`;
- `unsupported`, `serialization`, and `runner` for infrastructure contracts.

All 103 original examples have explicit tests, including the five manual examples.
`SampleCoverageTest` checks that every exported method is registered exactly once,
and that each zero-argument regression has an independently generated native oracle.

`GoMethodTestRunner` reuses the shared `TestRunner` with `checkDiscoveredProperties`
and `checkMatches`. Expectations relate resolved symbolic inputs to results, for example:

```kotlin
checkDiscoveredProperties(
    method = "max2",
    analysisResultsNumberMatcher = eq(count = 2),
    { a: Number, b: Number, result: GoResult -> a.toLong() > b.toLong() && result.long == a.toLong() },
    { a: Number, b: Number, result: GoResult -> a.toLong() <= b.toLong() && result.long == b.toLong() },
)
```

Every expected property must be discovered, and every collected execution must satisfy
at least one supplied expectation. `checkMatches` additionally requires a one-to-one
match between executions and expectations. The runner collects all terminated states,
with a 100-state limit and finite timeouts; these limits do not prove exhaustive analysis.

`GoResult` distinguishes success from panic and exposes the panic payload. Mutation
checks can inspect argument snapshots before and after execution. Pointers and interfaces
retain structured values and interface dynamic types; these snapshots do not preserve
object identity or the complete alias graph. Function parameters remain mocked: the
`call` example checks that mock contract, rather than replaying an actual function body.

The thematic `*RegressionTest` classes compare results with native Go for **108
zero-argument scalar/panic cases**. Branch, slice-alias, named-number/interface and
composite-copy checks replay 19 generated concrete inputs in a native Go executable.
`GoExamplesReplayTest` additionally replays generated array, slice, pointer and object
inputs against the original example functions, checking return values, panic occurrence
and argument snapshots after execution. The manual map test replays the original
`mapLoopLen` function in the same way. Replay supports these explicitly registered
methods; it does not reconstruct arbitrary alias graphs or function inputs.

Native scalar comparisons use textual representations. Native panic comparisons check
occurrence, while symbolic sample expectations may also check the payload.

The five slow examples remain manual and bounded:

```sh
./gradlew :usvm-go:manualTest --configure-on-demand
```

The nonterminating `loopInfinite` example expects no completed executions within the
analysis budget. The remaining manual examples and `panicRecoverComplex` permit partial
instruction coverage but still enforce their semantic expectations.
`assertCreatureFailNoComma` also permits partial instruction coverage: its `Person` to
`Building` assertion always panics, so the following return is unreachable. Every
collected execution must still satisfy its panic expectation.

The current local validation passes **237 default tests** and **5 manual tests**, with
no failures or skipped tests. Detekt on Go main/test sources, `validateProjectList`
and `git diff --check` pass. These counts describe a bounded local run, not an exhaustive
proof or a count of independent semantics supported.

The reproduced defects are now covered by symbolic expectations and selected native
comparisons:

- Nil-map lookup, comma-ok, deletion, range and assignment panic, including named maps.
  Absent lookups return type-specific zero values, including structs, arrays and named
  scalars. Map insertion and lookup copy struct/array values. Integer keys/values have
  native regressions; a symbolic test requires nil, absent-key and present-key witnesses.
- Named scalars, unary operators, interface assertions, argument snapshots, unsigned
  integer-to-float conversion, finite representable float-to-integer truncation and
  exact float-literal export. All 34 arithmetic/named-number tests pass.
- Input shapes and snapshots for arrays, structs, named values, interfaces, maps and
  slices; valid representation tags are constraints rather than artificial Go panics.
- Array/struct copying on assignment, calls, interface boxing, map insertion/lookup,
  `copy` and `append`, including nested structs. Native regressions check independent
  value fields and shared pointees inside copied structs. Composite slice copies read
  a source snapshot and advance one element per machine step, supporting overlapping
  ranges and symbolic lengths within the normal analysis budgets.
- Deferred calls capture arguments when registered and keep a separate defer stack for
  each invocation. Native regressions cover repeated calls, value arguments and recursion.
- Pointer conversions preserve nil and a shared pointee. Native regressions check
  aliasing in both conversion directions and round-trip equality.
- Comma-ok assertions produce composite zero values; failed non-comma assertions panic.
  Interface calls explore admissible concrete receivers and typed nil pointer panics.
  Value and pointer method sets are exported separately; a value does not acquire
  pointer-receiver methods. Pointers to interfaces do not acquire interface methods.
  Named nilable values compare their payload with nil, including failed assertions.
- Model resolution refines oversized collection witnesses within the same path constraints.
  If no witness fits the materialization limit, resolution reports unsupported rather
  than truncating the value and presenting it as a successful concrete result.
- Slice bounds use direct comparisons without overflowing `limit + 1`; narrow signed
  indices must be nonnegative before widening. Stores through nil pointers panic.
  The map-iteration expectation follows the source's zero-initialized keys, independently
  checked by replay.

`generateGoImports` can export the import examples for investigation. The original
import/standard-library exploratory factories depended on manually prepared dumps;
they are not included in the default suite.

## Representation and boundaries

The exporter records the target `int` width, raw string bytes, and floating-point
constants with enough digits to reproduce their float32/float64 values. Arrays and slices
share storage by element type. Slice headers record backing storage, offset, length
and capacity; `copy` and `append` operate on these views. Go pointers to fields or
array elements are frontend-local metadata, without changing core expression
transformers. State merging is rejected when frontend metadata differs.

Passing these tests does **not** establish complete Go semantics or arbitrary-project
support. In particular:

- Channels, goroutines, `select`, generic multi-conversions and unsafe pointer
  conversions are unsupported. Unsupported callees are reported explicitly.
- String comparison requires at least one concrete length. Rune conversion and
  complete UTF-8 string iteration are not implemented. Resolved strings are decoded
  as UTF-8, so arbitrary invalid byte sequences are not preserved in result text.
- Numeric conversion regressions cover finite, representable float-to-integer
  inputs. NaN, infinity and out-of-range float-to-integer results have not been
  validated; such results may depend on the Go target.
- Collection sizes use a nonnegative BV32 domain; symbolic sizes are restricted to
  that domain. Input slices currently model capacity as length, and materialized
  array/slice/string/map models must fit 10,000 elements for concrete resolution.
  Oversized models are re-solved within the same path; paths requiring larger values
  or exceeding the refinement budget are reported as unsupported.
- Unknown external calls and function parameters retain the prototype's mocking
  behavior. `GoFunctionReference` identifies a function input that cannot be replayed.
- Selected array/struct copies and scalar-pointer conversions have native validation.
  General pointer/interface identity and equality and reference map keys still need
  broader validation. Map-zero regressions cover selected struct, array and named scalar
  values; nil-map regressions currently cover integer keys/values.
- Package loading, standard-library integration and arbitrary repository workflows
  have not been validated end to end. Instruction coverage alone is not a semantic
  correctness check.
