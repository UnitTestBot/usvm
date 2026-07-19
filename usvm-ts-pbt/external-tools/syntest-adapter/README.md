# SynTest-JavaScript / DynaMOSA raw adapter

This package is the schema-v2 boundary for the SynTest-JavaScript comparison
arm. It generates one harness per eligible manifest method, exports exact
source branch objectives to an upstream wrapper, extracts concrete generated
tests into ETC v2, and writes exactly:

- `corpus.etc.jsonl`
- `native-coverage.json`
- `run-meta.json`
- `stderr.log`

SynTest native coverage is diagnostic. It never changes the paper numerator;
only the unified Kotlin concrete EtsIR replay can confirm an edge. In
particular, coverage within the eligible subset must not be presented as
coverage of the frozen `42 methods / 236 edges` denominator.

## Unified CLI

```text
node src/cli.mjs \
  --target-manifest target-manifest.json \
  --source-targets source-targets.jsonl \
  --method-ids method-ids.txt \
  --run-config run-config.json \
  --out-dir raw-run
```

All four inputs must use artifact contract v2. The output directory must be
empty. The adapter reads the canonical schemas from
`usvm-ts-pbt/artifact-contract/v2` and can invoke the shared Kotlin validator
through `flags.commonArtifactValidatorCommand` once A-INT supplies a launcher.

## Upstream runner protocol

No SynTest checkout or npm package is vendored. A run config may set
`flags.syntest.command` to a string array naming an external pinned wrapper.
For every eligible method the adapter invokes:

```text
<command...> --request <request.json> --result <checkpoint.json>
```

The request contains the generated ESM harness, DynaMOSA selection, exact
source objective ranges, seed, and method budget. The wrapper checkpoints a
normalized result containing `cases` and `objectives`; the adapter reads that
checkpoint even after timeout or nonzero exit, preserving partial valid
corpora. JSON-process results use `encodedArguments`/`encodedReceiver` with ETC
v2 values so `NaN`, infinities, `-0`, holes, maps, sets, callable references,
and aliases cannot be lost. Unsupported values reject that case explicitly.

An initial ETC corpus is passed only when both a path is configured and the
wrapper declares `upstreamCapabilities.initialCorpus=true`. This is an initial
concrete population, not a claim that SynTest is a mutational engine.

Without a configured wrapper the adapter still emits a valid
`unsupported_configuration` raw run and marks the fresh external campaign as
deferred to A-BENCH. It does not fabricate tests or coverage.

## Frozen primitive evidence

`npm run fixtures` derives adapter-local v2 fixtures from the immutable
2026-07-19 baseline. Tests prove the complete funnel on an injectable runner:

```text
42 selected -> 42 eligible -> 42 harnessed -> 42 attempted
             -> raw cases -> exported ETC -> unified replay handoff
```

The golden fixture has 236 exact source mappings. Replay and confirmed counts
remain `null` in raw metadata until the Kotlin pipeline runs.

## Pin, license, and rollback

The canonical upstream is pinned to
`syntest-framework/syntest-javascript@53547145f16c3ce4eefb8b479b124ba581d70e2d`.
Its audited Apache-2.0 `LICENSE` and `NOTICE` are preserved byte-for-byte under
`upstream/`; no upstream code is copied here. Rolling back this pilot is
isolated to removing `external-tools/syntest-adapter`; no shared production or
contract file is modified.

Run checks with:

```text
npm run check
```
