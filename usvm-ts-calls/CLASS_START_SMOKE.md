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
executable statement of the selected target class. Assignment, unresolved assignment call, standalone
call, conditional, explicit return, and throw callbacks run inside the corresponding interpreter visit,
before expression resolution or unknown-call dispatch. Queue selection alone cannot establish startup.
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
frontend revision, and project revision. It verifies every cached file before reuse. Scenes, machines,
initial states, and solver states are always created separately for each class.
