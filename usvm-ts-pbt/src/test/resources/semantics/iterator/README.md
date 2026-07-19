# Exact synchronous iterator semantic fixture v1

This directory freezes the dependency-neutral contract produced by
`WP-SEM-ITERATOR`. It is an executable specification, not a claim that the
concrete EtsIR interpreter or the symbolic engine already implements it.

## Exact subset

`iterator-semantics-v1.json` contains 26 cases for:

- `Symbol.iterator` acquisition on arrays, strings, Maps and Sets;
- iterator `next()` results with explicit `{ value, done }` encoding and sticky
  completion;
- sparse arrays, Unicode string code points, Map entry order and Set
  SameValueZero de-duplication;
- the fact that built-in iterator objects are themselves iterable;
- `for-of` over arrays, strings, Maps, Sets and a deterministic custom iterable;
- normal exhaustion, `break`, function `return`, body `throw`, a missing
  `return` method and direct iterator `return()` for the supported closing
  subset.

Every expected value and trace is frozen in JSON. Node runs the
JavaScript-compatible TypeScript source directly. The Kotlin test implements a
separate iterator state machine and must produce the same result/trace JSON; it
does not call production interpreter or symbolic-runtime code.

`capability.labels` contains only the closed WP-CAP enum. Concepts such as
`iterator_next`, `string_code_points`, `iterator_close` and
`custom_iterable_subset` live in `semanticTags`; both validators reject unknown
labels and tags.

## Nine frozen broad targets

The validator reconstructs all 23 `internal-pbt-usvm` broad
`reached=true/replayConfirmed=false` targets, filters the three frozen iterator
methods, and requires set equality with these nine IDs. It also joins every ID
to the immutable manifest SHA, source origin and the separate `usvm-only`
observation, so a missing target cannot disappear silently.

| Method | Frozen targets | Iterator-only terminal expectation |
|---|---|---|
| `find_min.ts::%dflt::%AM0$%dflt/1` | `#s14:0->15`, `#s14:1->16`, `#s18:0->19` | 3 `replay_confirmed` |
| `03-array/12-flatten-arrays.ts::%dflt::flattenRecursive/1` | `#s8:0->9`, `#s8:1->10`, `#s13:1->17` | 2 `replay_confirmed`; `#s13:1->17` is `exact_capability_mismatch` because stmt 11 is `Array.isArray` and needs `builtin_call` |
| `arrays.ts::%dflt::forEach/2` | `#s7:0->8`, `#s7:1->9`, `#s12:0->13` | 2 `replay_confirmed`; `#s12:0->13` is `exact_capability_mismatch` because stmt 10 is a callback `ptr_call` and needs `callable` plus `unresolved_pointer_call` |

The legacy artifacts record target reach/replay booleans but not a per-target
first-divergence statement. The fixture therefore says
`firstDivergence: not_recorded_in_legacy_artifact`; the exact Symbol.iterator
gate statement comes from a pinned frontend dump and is not misrepresented as
legacy telemetry.

## What `25/25 Unsupported Symbol.iterator` means

This is an event count, not 25 unique targets. In each of the frozen
`typescript-collections` reports (`internal-pbt` and the PBT prefix of
`internal-pbt-usvm`), `arrays.ts::forEach/2` executed 25 concrete cases and all
25 ended with the unsupported reason
`instance method: Symbol.iterator on VArray`. The two reports repeat the same
per-run 25/25 shape; they are neither summed to 50 nor converted into 25 target
IDs. JSON records `countUnit: pbt_execution_outcome` and
`isUniqueTargetCount: false`, and both validators check the source report names,
SHA-256 values and counters.

The Node collection source fixture completes 25 analogous executions without
an iterator error. Removing the production interpreter's 25/25 failures remains
an acceptance gate for `A-SEM-CONCRETE`, not a result claimed by this spec.

## Explicit boundary

The exact subset excludes async iteration, generator/yield and `yield*` state
machines, mutation during iteration, reentrant `next`/`return`, Proxy traps,
invalid primitive iterator results, abrupt/invalid `return` results and iterator
helper methods. Generator cases carry both `iterator` and `spread_yield` labels
and remain explicit capability mismatches; ordinary iterator support never
claims generator support.

## Verification

From the repository root:

```bash
node usvm-ts-pbt/src/test/resources/semantics/iterator/iterator-spec-runner.cjs --validate
node --test usvm-ts-pbt/src/test/resources/semantics/iterator/iterator-spec.test.cjs
./gradlew :usvm-ts-pbt:test \
  --tests org.usvm.ts.pbt.semantics.iterator.IteratorSemanticsSpecTest
node usvm-ts-pbt/benchmarks/baselines/2026-07-19/scripts/validate-baseline.mjs
```

`A-SEM-CONCRETE` and `A-SEM-SYMBOLIC` should consume this contract independently
and add Node -> concrete EtsIR -> symbolic witness replay checks. Updating a
frozen expected value requires a version bump; regenerating expected output from
the implementation under test is not allowed.
