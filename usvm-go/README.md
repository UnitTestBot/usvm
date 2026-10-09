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

`GoSemanticRegressionTest` compares zero-argument scalar results and panic outcomes
with a native Go execution. It also asks USVM for branch witnesses, then executes
those concrete inputs in Go, including a write through an aliased slice after a fork.
The scalar cases cover shifts, bit operations, native integer width and unsigned
widening, slice length/capacity, aliasing, copy/append, array pointers, map size,
string byte content/order/conversion, and selected panic paths.

`GoUnsupportedTest` checks that goroutines and comparison of two strings with
symbolic lengths produce explicit unsupported results. `JacoDbTest` retains the
98 fast prototype examples as execution/coverage smoke checks; these do not assert
native output values. `panicRecoverComplex` remains an explicit partial-coverage
exception. `ModelTest` checks SSA JSON round-tripping.

The five slow prototype examples are manual and bounded:

```sh
./gradlew :usvm-go:manualTest --configure-on-demand
```

`generateGoImports` can export the import examples for investigation. The original
import/standard-library exploratory factories depended on manually prepared dumps;
they are not included in the default suite.

## Representation and boundaries

The exporter records the target `int` width and raw string bytes. Arrays and slices
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
- Collection sizes use a nonnegative BV32 domain; symbolic sizes are restricted to
  that domain. Input slices currently model capacity as length, and materialized
  array/slice/string models are capped at 10,000 elements.
- Unknown external calls and function parameters retain the prototype's mocking
  behavior. `GoFunctionReference` identifies a function input that cannot be replayed.
- General array/struct value-copy behavior, pointer/interface equality, reference
  map keys and nil-map operations need further semantic validation. The native
  regression suite does not claim coverage of these areas.
- Package loading, standard-library integration and arbitrary repository workflows
  have not been validated end to end. Instruction coverage alone is not a semantic
  correctness check.
