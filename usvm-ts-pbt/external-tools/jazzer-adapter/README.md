# Jazzer.js raw-artifact adapter

This package turns deterministic Jazzer.js byte inputs into canonical External
Test Corpus (ETC) v2 cases for local TypeScript-library coverage experiments.
It does not award coverage, produce a replay report, or classify residual
branches. The unified Kotlin EtsIR replay pipeline remains the only coverage
authority.

## Pinned tool and license

- upstream: <https://github.com/CodeIntelligenceTesting/jazzer.js>;
- package: `@jazzer.js/core` `4.0.0` (exact package-lock pin and integrity);
- upstream revision for release `v4.0.0`:
  `f3eb99cb0ea20fe45356535b1f60f86741974e72`;
- SPDX license: `Apache-2.0`; no NOTICE file is required by that pinned release.

No install is needed when `node_modules` is already populated. Otherwise use
`npm ci`; do not replace the exact pin with a range.

## One run contract

The canonical CLI consumes a target manifest v2 and run config v2. The run
config owns the seed, total budget, exploration/export deadlines, tool commit,
and typed adapter flags.

```bash
npm run fuzz -- \
  --run-config /tmp/run-config.json \
  --manifest /tmp/target-manifest.json \
  --method 'src/math.ts::%dflt::magic/1' \
  --harness /tmp/harness.cjs \
  --instrument /absolute/path/to/compiled/project/ \
  --coverage-corpus /tmp/jazzer-coverage \
  --crash-corpus /tmp/jazzer-crashes \
  --initial-etc /tmp/internal-pbt.etc.jsonl \
  --initial-etc /tmp/usvm-witnesses.etc.jsonl \
  --raw-run /tmp/jazzer-raw-run
```

`--raw-run` must be empty. It receives exactly:

- `corpus.etc.jsonl` — every successfully decoded coverage/crash byte input,
  scheduled for unified replay;
- `native-coverage.json` — diagnostic libFuzzer corpus membership, never final
  EtsIR coverage;
- `run-meta.json` — timings, completion status, producer identity, seed-import
  and raw-corpus conservation accounting;
- `stderr.log` — bounded combined process diagnostics.

Timeout (`timeout_partial_corpus`) and non-zero tool completion (`tool_failure`)
still export all byte files persisted before termination and write all four
artifacts. `run-meta.json.accounting.casesHandedToReplay` is a handoff count;
actual replay attempts and confirmed edges are reported only by Kotlin replay.

The supported run-config flags are `sync` and `ignoreExceptions` (booleans),
`maxLength` (non-negative integer), and `logCapBytes` (1 through 16 MiB). The
default log cap is 1 MiB.

## Harness and ETC-v2 seed envelope

Legacy arbitrary bytes keep the original typed decoder byte-for-byte. This is
important for the frozen Jazzer primitive corpus. ETC-v2 imports use a distinct
`USVM-ETC-V2` envelope, so they can losslessly preserve special numbers,
sparse arrays, plain object graphs, `Map`, `Set`, aliases, and structured
receivers without changing legacy byte meaning.

The minimal CommonJS harness remains:

```js
exports.invoke = (args) => target(...args);
exports.toCorpusCase = (args) => ({ receiver: undefined, arguments: args });
```

An ETC seed with a receiver additionally requires:

```js
exports.invokeCase = ({ receiver, arguments: args }) =>
  Reflect.apply(target, receiver, args);
```

Portable callable and constructor references are never guessed. A harness may
materialize them explicitly:

```js
exports.materializeCallable = (reference, path) => resolveKnownExport(reference);
exports.materializeConstructorPlan = (reference, args, path) =>
  Reflect.construct(resolveKnownExport(reference), args);
```

Without the corresponding hook, import produces a stable
`unsupported_callable:...` or `unsupported_constructor_plan:...` rejection.
Arbitrary closures and class instances from mutated bytes are exported as
typed `unrepresentable` values, so downstream replay records an explicit
rejection instead of silently substituting `undefined`.

Seed import is also available separately:

```bash
node src/seed-corpus.cjs \
  --manifest /tmp/target-manifest.json \
  --method '<methodId>' \
  --harness /tmp/harness.cjs \
  --external-inputs /tmp/pbt.etc.jsonl \
  --external-inputs /tmp/usvm.etc.jsonl \
  --out /tmp/jazzer-coverage
```

Only canonical ETC-v2 JSONL is accepted. The importer reports
`importedCases = exportedCases + rejectedCases` and deduplicates identical
byte envelopes by SHA-256 while retaining case-level accounting.

## Verification

```bash
npm test

JAVA_HOME=/path/to/jdk21 ../../../gradlew \
  -p ../../.. \
  :usvm-ts-pbt:test \
  --tests org.usvm.ts.pbt.external.ArtifactContractTest

JAVA_HOME=/path/to/jdk21 ../../../gradlew \
  -p ../../.. \
  -I usvm-ts-pbt/external-tools/jazzer-adapter/contract-validator.init.gradle \
  :usvm-ts-pbt:validateJazzerRawRun \
  -DjazzerRawRun=/tmp/jazzer-raw-run
```

The Node suite freezes one legacy byte corpus, round-trips the ETC-v2 special
value/receiver golden, checks exact rejects, verifies conservation, and covers
success, timeout, and non-zero partial completion. The historical primitive
baseline is 211/236; a fresh 42-method campaign and the acceptance floor of
210/236 remain an `A-BENCH` campaign gate. Decoder compatibility is protected
locally, but a fixture is not presented as a fresh coverage measurement.
