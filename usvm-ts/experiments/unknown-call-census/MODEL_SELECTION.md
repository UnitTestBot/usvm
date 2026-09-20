# Finite development model selection

Selection was frozen on 2026-09-19 before any held-out TS Calls evaluation. This is a development decision from a three-project pilot, not evidence that the selected models improve coverage.

## Evidence used

- Approximation audit #368 at `303409613c30ea5dcdabc1f97ac8092d753765b1`, which audits current source `41961f7b66c30c8a2a7507c67a79396f495f4520` and historical source `3728ba45ab092422e2cb2e57ed6fe2377425b622`.
- The frozen development manifest in `development-corpus.json` and the primary `EMPTY_FRESH` census at tool commit `c1e07845c393374f743220dfe7febb4bc208c3b4`, tree `bc524f2b9ba451b378db1bce362722a7b534c931`. It analyzed 97 entry functions from three pinned projects: 45 completed, 36 ended with explicit partial-analysis diagnostics, 16 reached the 30-second method timeout, and none ended with a boundary tool error. It observed 7,002 repeated events at 36 stable source sites in 26 containing functions.
- The sample covered 11 source files in TheAlgorithms/TypeScript, 37 in javascript-datastructures-algorithms, and 14 in typescript-collections. Stable sites were distributed 9, 7, and 20 across those projects. This replaces the obsolete lexicographic/BFS run, whose result is diagnostic only and is excluded from selection evidence.
- Stable-source-site prevalence rather than repeated loop-event totals. The largest groups were iterator `Symbol.iterator` and `next` (4 sites each), `Error` construction (4), `Object.keys` (3), iterator `has` (3), and the project callback `Heap.compare` (3). Built-in array `pop` occurred at one source site; no built-in `Array.shift` site was observed. The 1,918 repeated `pop` events and 3,630 repeated `FactoryDictionary.defaultFactoryFunction` events arise from repeated exploration and must not be read as independent prevalence observations.

The primary profile disables optional catalog models but leaves mandatory semantics and legacy pre-dispatch approximations unchanged. Therefore the census is an inventory of observed unknown-call decisions, not every approximate or unresolved call in the engine. The 36 partial analyses, 16 timeouts, three-project scope, and omitted call-resolution candidate tails limit prevalence interpretation. Additional development projects must be pinned before their results are inspected; held-out projects remain separate and cannot influence model selection.

## Decision

The finite modeled set is exactly:

```text
ts.array.pop
ts.array.shift
```

The control set is empty. Both existing models were implemented before this census and are retained as the bounded EtsIR-body and symbolic-memory-intrinsic mechanism pair from #368. They must not be described as census-selected. Their common experiment domain is the narrow dense, ordinary, mutable one-dimensional array domain in the #368 audit. Calls outside that admitted domain remain residual.

No new model family is admitted for the pilot:

- Application callbacks and dispatch limitations such as `BSTreeKV.compare` require shared call-resolution work, not optional treatment models.
- The corrected array `pop` observation confirms one development source site for the already selected mechanism. It does not retrospectively make that pre-census model census-selected.
- Iterator `next` needs stateful iterator representation and completion/alias validation.
- `Array.isArray` has one stable development site and still needs proven target provenance plus rank/proxy bounds.
- The remaining scattered standard calls do not establish both prevalence and a reviewed bounded semantic contract.

This is an acceptable no-new-family result. A later family requires a separate bounded implementation task, original-JavaScript validation, and a refrozen model set before held-out outcomes are inspected.

## Content identity

At accepted #380 source `3134d06515bca61ba2a357a67697ac8620b0e420`:

- `ArrayModels.ts` source SHA-256: `9f40d3abce58e3412a0206eabd9fdb0547e12c2ebd832ce48b260b3339518e26`.
- `TsArrayShiftIntrinsicModel.kt` SHA-256: `ff6dd634cf660c83e203b82c927a28e88859f0bc6b9f24bec2fa97d738dc9e11`.

The accepted run used JacoDB `ddb127d9ef`, the native `TS_FRONTEND`, Yices, OpenJDK 21.0.12, Node 26.5.0, random seed 0, `CLOSEST_TO_UNCOVERED_RANDOM`, no coverage-based early stop, a 900-second project budget, and a 30-second method budget. A class qualified when it had at least one ordinary method with at least eight IR statements. Up to 40 classes and 40 methods were selected per project by stable seeded ranks and class round-robin. The raw artifact SHA-256 is `a091dff2d393e131a83e76cc71af51dd38dcd821e2e01ee007cfe8aa56ab6c08`; the generated and standalone-regenerated summaries are byte-identical with SHA-256 `7c59fbfa09af54de1e74ed65f4c762629013688d109305a970b421ee32781d00`; the manifest SHA-256 is `4adba5861339782e1f514f01525561a67aa9f01a6abe914b3d371ff77e1c1bbb`.

Run metadata records `unknownCallModelSelection` as `NONE`. `ts.array.shift` has no EtsIR artifact. For `ts.array.pop`, the generated `etsIrHash` remains unavailable from the built-in wrapper and must not be invented.
