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

The thematic `*RegressionTest` classes retain comparisons against native Go for 66
zero-argument scalar/panic cases. Branch, slice-alias and named-number/interface
checks also replay seven generated concrete inputs in a native Go executable. Native scalar comparisons currently use textual representations; native
panic comparisons check occurrence, while symbolic sample expectations may also check
the payload. Native replay does not yet support arbitrary collection/struct inputs.

The five slow examples remain manual and bounded:

```sh
./gradlew :usvm-go:manualTest --configure-on-demand
```

The nonterminating `loopInfinite` example expects no completed executions within the
analysis budget. The remaining manual examples and `panicRecoverComplex` permit partial
instruction coverage but still enforce their semantic expectations.

The stronger symbolic expectations expose unresolved input-model and semantic defects.
They are kept as failing tests, without disabled tests or expected-failure wrappers.
The current local run has **181 default tests: 138 passed, 43 failed**, plus
**5 manual tests: 3 passed, 2 failed**, with no skipped tests. Detekt on Go main/test
sources and the project-list check pass. Failures are grouped as follows:

| Test package | Default failures |
| --- | ---: |
| `collections.slices` | 20 |
| `types` | 11 |
| `collections.maps` | 0 |
| `objects` | 4 |
| `strings` | 4 |
| `algorithms` | 2 |
| `arrays` | 1 |
| `pointers` | 1 |

The manual failures are `canVisitAllRooms` and `mapLoopLen`. Counts are local-run observations, not a
count of independent bugs or an exhaustive list; budgeted symbolic exploration and
model materialization may affect which witnesses are collected. This draft integration
requires those failures to be resolved before merging.

Nine previously failing default map tests now pass. New native regressions check
nil-map lookup, comma-ok, deletion, range and assignment panic with integer keys/values,
including lookup/deletion/range on named maps. A symbolic comma-ok regression requires
witnesses for nil maps, absent keys and present keys, with input-dependent values.
The two constant nil-map range tests permit partial instruction coverage because their
loop bodies are unreachable; they still require exactly one native-matching execution.

All 34 arithmetic/named-number tests now pass. Sixteen new regressions cover
non-nil named scalar inputs, unary operators and argument snapshots, numeric
interface assertions/round trips, unsigned integer-to-float conversion, finite
representable float-to-integer truncation, and exact float-literal export.
Twelve compare constant results with native Go; four have symbolic expectations.
The interface round-trip test also replays two generated inputs and checks that
passing a numeric argument by value preserves its original snapshot.

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
  array/slice/string models are capped at 10,000 elements.
- Unknown external calls and function parameters retain the prototype's mocking
  behavior. `GoFunctionReference` identifies a function input that cannot be replayed.
- General array/struct value-copy behavior, pointer/interface equality, reference
  map keys and zero values for composite/named map values need further semantic
  validation. Nil-map regressions currently cover integer keys/values.
- Package loading, standard-library integration and arbitrary repository workflows
  have not been validated end to end. Instruction coverage alone is not a semantic
  correctness check.
