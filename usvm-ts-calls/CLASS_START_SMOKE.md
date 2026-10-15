# Raw class startup smoke

This diagnostic loads the complete frozen target class through the bundled TypeScript frontend,
materializes its IR bodies, and starts a real `TsMachine`. It does not require observation domains,
native replay admission, or serializable class arguments. It makes no native coverage claim.

Build the installed distribution with `./gradlew :usvm-ts-calls:installDist`. Run the frozen cohort:

```sh
python3 usvm-ts-calls/scripts/run-class-start-smoke.py \
  --calls usvm-ts-calls/build/install/usvm-ts-calls/bin/usvm-ts-calls \
  --manifest /absolute/path/startup-manifest.json \
  --output /absolute/path/fresh-startup-results
```

Each class has an isolated JVM and output directory. The driver retains all manifest classes in
`summary.json`, including pending, acquisition failure, frontend failure, timeout, and kernel failure
rows. The default external child deadline is 60 seconds; `--child-timeout-seconds` controls this
deadline independently of the symbolic budget in the manifest. Timeout kills the entire process
group, including a frontend subprocess.

## Startup criterion

`started` requires both a real initial state with a model and an interpreter callback for an original
executable statement of the selected target class. The generic callback runs inside `TsInterpreter.step`,
after step preparation and before statement dispatch, including field definitions. Source attribution
does not run on exception-propagation or failed preparation paths. Queue selection alone cannot establish startup.
Synthetic default returns, NOPs, declarations without initializers, foreign classes, and missing source
spans cannot establish it either. A first unsupported residual call can establish startup even when its
successful transition count is zero; this is recorded as `STARTED_WITH_LIMITATION`.

`originalTargetStatementsEntered` counts source interpreter entries. `targetSuccessfulTransitions`
counts the subset that complete a feasible interpreter transition. The first entry, source location,
initial-state count, and identity are saved durably to `progress.json` before the callback returns.
If a child is killed later, the driver preserves that proven entry and records the process limitation.
`unknown-calls.json` retains ordered model/fallback decisions. Final engine stop reasons, unsupported
paths, source drift, and stage durations are preserved separately.

`qualifyingStarted` additionally requires `acquisitionComplete` in both the manifest case and its frozen
external acquisition receipt, with all source/config/dependency bytes still matching. Installation
with a `FROZEN` receipt and `acquisitionComplete=false` only supplies diagnostic startup evidence.
The acquisition workflow must establish the complete runtime graph before enabling qualification.

## Frozen manifest

Schema version 1 stores `toolRevision`, `nativeFrontendRevision`, `ledgerSha256`, `expectedClassCount`
(default 200), `budgetMillis` (default 2000), `solverLimitMillis` (default 250), `collectedStatesLimit`
(default 10), `seed` (default 17), `profile` (default `FROZEN_STOP`), and `classes`.

Each case has a safe `classId`, its exact original `identity`, `projectRevision`, absolute `sourceRoot`,
relative `sourceFile`, `className`, and `inputSha256` map. The map must include the complete target source
and every acquired input consumed by the frozen scope. Optional `frontendConfig` freezes the resolved
compiler configuration used in the copied scope. Optional `acquisitionReceipt` is relative to the
manifest directory; `acquisitionSha256` freezes its bytes. Receipt checks bind project revision, source
root, original class identity/source bytes, and the acquired snapshot hashes. A complete case requires
this receipt and its explicit completion evidence.

The CLI validates the built tool and bundled frontend identities against the manifest. The driver
freezes hashes of its own script, launcher, runtime jars, Java, and Node, plus JVM options and environment.
A resume verifies source and receipt bytes before reusing a final result; changed runtime or manifest
identity requires a new output directory. An interrupted child without a final driver receipt also
requires a fresh attempt directory.

The shared cache stores immutable frontend IR only, keyed by frozen inputs, resolved compiler config,
frontend revision and actual provider binary hash, and project revision. It verifies every cached file before reuse. Scenes, machines,
initial states, and solver states are always created separately for each class.

Runtime target lookup requires the exact file, class name, and `CLASS` declaration category. A merged
TypeScript interface remains in the full scene and is listed in the receipt; it cannot compete as the
runtime target. All target IR bodies remain loaded and reported. Compiler-lowered anonymous closures
are marked `LOWERED_CLOSURE_REACHABLE_VIA_OWNER`; they run through their owning member with its allocated
closure environment rather than receiving a fabricated independent closure argument. Declared methods,
constructors, and initialization bodies remain independent entries. The source-mapped IR universe keeps
the complete class, including these dependent closure bodies.

Both `ETS_FRONTEND_SCRIPT` and `ETS_FRONTEND_DIR` overrides are rejected. The exploratory gate additionally
requires the exact tool/frontend revisions and runtime byte hashes that produced the completed startup
screen; changing the engine, frontend, shared driver, Java, or Node requires a fresh startup screen.

## Exploratory four-profile probe

After a completed startup screen proves at least 180 qualifying starts from the exact original 200
classes, run the exploratory diagnostic with:

```sh
python3 usvm-ts-calls/scripts/run-class-symbolic-probe.py \
  --calls usvm-ts-calls/build/install/usvm-ts-calls/bin/usvm-ts-calls \
  --startup-manifest /absolute/path/startup-manifest.json \
  --startup-summary /absolute/path/startup-results/summary.json \
  --output /absolute/path/fresh-symbolic-probe
```

This reuses the isolated startup driver and raw loading path through `class-symbolic-probe`. It freezes
all 200 original identities and their ordering in each of `EMPTY_STOP`, `EMPTY_FRESH`, `FROZEN_STOP`, and
`FROZEN_FRESH`, for 800 recorded cells. Every class has a 30-second engine search budget, seed 17, a
250ms solver-check limit, 25ms entry-point quanta, CLASS coverage scope, and no terminal-state cap.
The driver copies and verifies frozen acquisition receipts; it does not run dependency installation.
An unsupported or failed class remains in the same denominator. The current build's complete model
catalog and runtime bytes are frozen before any profile runs.

Unlike diagnostic startup, interpreter step failures are retained through the machine's failure
metadata while other paths continue. Returned, exceptional, unsupported terminal, stopped-prefix,
and remaining queued states are counted separately. Diagnostic snapshots retain method provenance,
statement text, symbolic result, and complete ordered path constraints. Each snapshot is streamed to
`child/states.jsonl.gz`; `constraintIds` and `symbolicResultId` refer to the exact diagnostic strings in
`child/expressions.jsonl.gz`. Expression identity is interned across states, so a shared constraint is
rendered once without truncating its contents or reducing the set of states. The final result contains
only journal references, SHA-256, compressed sizes, record counts, and completion flags; no snapshots
are embedded a second time. Journals contain concatenated complete gzip members, flushed and fsynced
before each state is acknowledged. Standard streaming gzip readers decode them in order. A killed
child retains its earlier completed members; a missing final result or a false completion flag remains
an incomplete measurement. Snapshot failures remain explicit. Remaining states are drained after the
machine has computed its actual stop reason.
Unknown-call events are appended to `unknown-calls.jsonl`. These diagnostic snapshots are not concrete
native witnesses or resumable machine states.

The raw report freezes the original target's source-mapped IR point universe before search and records
actual interpreter entries in that universe. The driver rejects any changed universe between the four
profiles. This diagnostic is distinct from executable original-source/native replay coverage; the
summary reports native class coverage as unavailable. Setup, actual analysis elapsed time, budget
overruns, solver checks/statuses, entry-point quantum overruns, and engine/unsupported outcomes remain
visible. Missing raw metrics are unavailable rather than fabricated zeroes. The external child deadline
defaults to 120 seconds and does not create a 120-second measurement budget.
