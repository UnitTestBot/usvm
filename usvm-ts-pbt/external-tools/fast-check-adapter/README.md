# fast-check raw-v2 adapter

This package is the property-based input producer for the TypeScript hybrid
campaign. It pins `fast-check` 4.9.0 and exports canonical ETC v2 cases for the
single Kotlin EtsIR replay pipeline. It does not replay cases, compute final
coverage, emit residual targets, or treat imported USVM/PBT examples as
mutation seeds.

## Run

Install the exact lockfile and invoke the unified adapter CLI:

```bash
npm ci
node src/cli.mjs run \
  --target-manifest /campaign/target-manifest.json \
  --source-targets /campaign/source-targets.jsonl \
  --method-ids /campaign/method-ids.txt \
  --initial-etc /campaign/initial.etc.jsonl \
  --run-config /campaign/run-config.json \
  --harness /campaign/harness.mjs \
  --out-dir /campaign/raw/fast-check
```

`--initial-etc` and `--harness` are optional and must be omitted when absent.
ETC v1 is rejected with an instruction to use the repository-owned
`artifact-contract convert-v1-etc` command; the adapter does not carry another
v1 converter or a private copy of the full ETC codec.

The roadmap-style standalone form is also supported. It synthesizes the same
run identity fields from the pinned runtime:

```bash
node src/cli.mjs run \
  --target-manifest /campaign/target-manifest.json \
  --source-targets /campaign/source-targets.jsonl \
  --method-ids /campaign/method-ids.txt \
  --seed 20260719 \
  --budget-ms 10000 \
  --export-replay-grace-ms 1000 \
  --out-dir /campaign/raw/fast-check
```

When `--run-config` is given, optional CLI seed/budget/grace values must match
it exactly. The v2 deadline relation is enforced:

```text
grace = min(5000, max(1000, floor(budgetMs / 10)))
explorationDeadlineMs = budgetMs - grace
hardResultDeadlineMs = budgetMs
```

Adapter-specific typed run-config flags are:

- `fastCheckRunsPerMethod` (or `runsPerMethod`), default 1000;
- `fastCheckPathsByMethod` (or `pathsByMethod`), a methodId-to-path object;
- `logCapBytes`, default 16 MiB.

`--path` is a convenient single-method override. Both campaign and per-method
seeds, every invocation phase, and fast-check's exact counterexample path are
written into ETC metadata. A root counterexample has fast-check's legitimate
empty path in metadata and remains reproducible from its seed; ETC's optional
`path` field is omitted because the frozen schema requires it to be non-empty.

## Raw output boundary

The output directory must be empty. On success, unsupported configuration,
tool failure, and timeout, the adapter writes exactly:

```text
corpus.etc.jsonl
native-coverage.json
run-meta.json
stderr.log
```

`stdout` is one JSON protocol event. Diagnostics are bounded in `stderr.log`.
Native claims are empty unless a harness explicitly supplies honest tool-native
claims; they are diagnostic-only and never EtsIR coverage. `run-meta.json`
records startup/generation/export/total monotonic milliseconds, the pinned
commits, exit status, timeout, over-budget time, log truncation, and the complete
funnel.

Imported v2 ETC cases for selected methods are copied first with
`generatedAtMs=0`, `origin=example`, `replayPrefix=true`, and
`mutationSeed=false`. Materializable cases are also passed to fast-check as
`examples`. Unsupported callable/cycle/class/accessor values remain explicit
replay-prefix cases and are counted as rejected rather than silently coerced.
Random cases start at `generatedAtMs>=1`, so replay sorting by
`(generatedAtMs, caseId)` cannot move them ahead of the prefix. A deadline
interruption writes a valid `timeout_partial_corpus` instead of discarding work.

## Harness API

The manifest inference handles primitives, literals, unions, tuples, optional
and rest parameters, arrays including sparse arrays, object literals,
`Record<string, T>`, `Map`, and `Set`. Unknown reference types use bounded plain
records. Arbitrary JavaScript functions are generated for callable signatures
but exported as explicit `unrepresentable/function` unless the harness maps
them to a source-level callable reference.

A harness can add execution and exact receiver construction:

```js
import { target, Receiver } from "./compiled-project.js";

export function toCorpusCase(args, { method }) {
  if (method.entryKind !== "instance") return { arguments: args };
  return {
    receiver: { threshold: 10 },
    arguments: args,
    receiverPlan: {
      className: "Receiver",
      callable: {
        modulePath: "src/receiver.ts",
        exportName: "Receiver",
        callableKind: "class",
      },
      arguments: [10],
    },
  };
}

export async function invoke({ receiver, arguments: args }, { method, phase }) {
  return target.call(receiver, ...args) !== false;
}

export function resolveCallable(reference) {
  if (reference.exportName === "Receiver") return Receiver;
  throw new Error(`unknown callable ${reference.exportName}`);
}
```

Optional `construct(reference, args)` can replace `resolveCallable` plus
`Reflect.construct` for initial receiver plans. Optional
`getNativeCoverageClaims(context)` may return native diagnostics. Mapping
errors, missing instance receivers, aliases, holes, special numbers, raw
functions, symbols, cycles, accessors, and class instances all have explicit
support/reject behavior covered by golden tests.

## Verification and coverage gate

```bash
npm test
npm pack --dry-run --json
```

The tests generate an independent raw directory and run the shared Kotlin
`ArtifactContractCli validate raw-run` against it. They also cover special ETC
values, receiver plans, deterministic seed/path shrinking, complete accounting,
bounded logs, protocol-only stdout, and timeout partial output.

The frozen 2026-07-19 evidence records the pre-v2 fast-check baseline as
213/236 primitive edges (fast-check+USVM 216/236). This adapter change does not
claim a fresh 213/236 measurement: the saved campaign inputs are legacy v1,
while the v2 launcher/replay campaign must be assembled by the integration and
benchmark work packages. The acceptance gate for that fresh A-BENCH run is at
least 212/236 under the frozen budget. Unit/golden validity is not presented as
campaign coverage.

## Upstream pin and license

- Repository: `https://github.com/dubzzz/fast-check`
- Version/tag: `4.9.0` / `v4.9.0`
- Audited revision: `0d3c2547dce556f72413607849377530d18ea283`
- SPDX license: MIT
- LICENSE blob: `879582464487c55389c243110ca18e014db7319e`
- Root NOTICE: absent and not required by the upstream audit

`package-lock.json` pins 4.9.0 exactly. The adapter exports concrete data only;
it does not vendor or redistribute fast-check source code.
