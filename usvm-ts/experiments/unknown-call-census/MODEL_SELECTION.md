# Finite development model selection

Selection frozen on 2026-09-19 before any held-out TS Calls evaluation. This is a development decision for a small pilot, not evidence that the selected models improve coverage.

## Evidence used

- Approximation audit #368 at `303409613c30ea5dcdabc1f97ac8092d753765b1`, which audits current source `41961f7b66c30c8a2a7507c67a79396f495f4520` and historical source `3728ba45ab092422e2cb2e57ed6fe2377425b622`.
- The frozen development manifest in `development-corpus.json` and its primary `EMPTY_FRESH` census. The post-commit local pilot attempted 79 functions from three pinned projects: 58 completed, 6 timed out, and 15 ended with a boundary tool error. It observed 768 repeated events at 41 stable sites in 23 containing functions. These counts are retained with the raw artifact and must be regenerated after transplanting the census onto the accepted integration head.
- Stable-site prevalence rather than repeated loop-event totals. The largest repeated groups were application callback or dispatch limitations: `BSTreeKV.compare` (390 events, 3 sites), iterator `next` (122 events, 2 sites), and `Array.isArray` (102 events, 1 site). Project-defined `Stack.pop` accounted for 14 events at 3 sites; it is not the built-in `Array.pop` target. No built-in `Array.shift` site was observed.

The primary profile disables optional catalog models but leaves mandatory semantics and legacy pre-dispatch approximations unchanged. Therefore the census is an inventory of observed unknown-call decisions, not every approximate or unresolved call in the engine. Tool errors, timeouts, and omitted call-resolution candidate tails also limit prevalence interpretation.

## Decision

The finite modeled set is exactly:

```text
ts.array.pop
ts.array.shift
```

The control set is empty. Both existing models were implemented before this census and are retained as the bounded EtsIR-body and symbolic-memory-intrinsic mechanism pair from #368. They must not be described as census-selected. Their common experiment domain is the narrow dense, ordinary, mutable one-dimensional array domain in the #368 audit. Calls outside that admitted domain remain residual.

No new model family is admitted for the pilot:

- `BSTreeKV.compare` and the project `Stack` methods require application-call/callback resolution shared by every profile, not optional treatment models.
- Iterator `next` needs stateful iterator representation and completion/alias validation.
- `Array.isArray` has one stable development site and still needs proven target provenance plus rank/proxy bounds.
- The remaining scattered standard calls do not establish both prevalence and a reviewed bounded semantic contract.

This is an acceptable no-new-family result. A later family requires a separate bounded implementation task, original-JavaScript validation, and a refrozen model set before held-out outcomes are inspected.

## Content identity

At audited source `41961f7b66c30c8a2a7507c67a79396f495f4520`:

- `ArrayModels.ts` source SHA-256: `9f40d3abce58e3412a0206eabd9fdb0547e12c2ebd832ce48b260b3339518e26`.
- `TsArrayShiftIntrinsicModel.kt` SHA-256: `ff6dd634cf660c83e203b82c927a28e88859f0bc6b9f24bec2fa97d738dc9e11`.
- Empty catalog fingerprint: `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`.

Recompute the source hashes on the accepted #380/integration head and record the exact engine commit/tree, built JAR hashes, JacoDB `ddb127d9ef`, native frontend, solver, Node and options. The catalog fingerprint identifies only the sorted ID set. `ts.array.shift` has no EtsIR artifact. For `ts.array.pop`, record the generated `etsIrHash` from the actual accepted runtime load when the downstream harness exposes it; until then it is explicitly unavailable rather than invented.
