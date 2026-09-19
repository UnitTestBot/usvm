# Finite development model selection

Selection frozen on 2026-09-19 before any held-out TS Calls evaluation. This is a development decision for a small pilot, not evidence that the selected models improve coverage.

## Evidence used

- Approximation audit #368 at `303409613c30ea5dcdabc1f97ac8092d753765b1`, which audits current source `41961f7b66c30c8a2a7507c67a79396f495f4520` and historical source `3728ba45ab092422e2cb2e57ed6fe2377425b622`.
- The frozen development manifest in `development-corpus.json` and its corrected primary `EMPTY_FRESH` census at tool commit `b04a8ed16410fd3c5e8bc049304f9b649c11ee48`. It attempted the same 79 entry functions from three pinned projects: 43 completed, 35 ended with explicit partial-analysis diagnostics, one timed out, and none ended with a boundary tool error. It observed 82 repeated events at 26 stable source sites in 19 containing functions. The generated and standalone-regenerated summaries are byte-identical with SHA-256 `bf52a4cf5f03f867f594990c1f458ddb1d244c01679bf3a1fd852019aa3ce145`.
- Stable-source-site prevalence rather than repeated loop-event totals. The largest corrected groups were `BSTreeKV.compare` (24 events, 3 sites), the built-in array `pop` reached inside the restored project `Stack.pop` body (10 events, 1 site), `charAt` (7 events, 2 sites), `parseInt` (7 events, 2 sites), and callbacks (6 events, 3 sites). No built-in `Array.shift` site was observed. The high partial-analysis count limits prevalence interpretation.
- The first local pilot attempted the same functions but produced 768 events at 41 sites. Review found that its entry-file bound removed imported support files and that swallowed interpreter failures appeared completed. Its preserved raw artifact is preliminary diagnostic evidence only; in particular, its 52 project `Stack` resolution events were harness-induced and are excluded from selection evidence.

The primary profile disables optional catalog models but leaves mandatory semantics and legacy pre-dispatch approximations unchanged. Therefore the census is an inventory of observed unknown-call decisions, not every approximate or unresolved call in the engine. Tool errors, timeouts, and omitted call-resolution candidate tails also limit prevalence interpretation.

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

At accepted #380 source `3134d06515bca61ba2a357a67697ac8620b0e420` and corrected census tool tree `f9ba380951630b62ef55e7aae7d90f2fab55298b`:

- `ArrayModels.ts` source SHA-256: `9f40d3abce58e3412a0206eabd9fdb0547e12c2ebd832ce48b260b3339518e26`.
- `TsArrayShiftIntrinsicModel.kt` SHA-256: `ff6dd634cf660c83e203b82c927a28e88859f0bc6b9f24bec2fa97d738dc9e11`.

The corrected run used JacoDB `ddb127d9ef`, the native `TS_FRONTEND`, Yices, OpenJDK 21.0.12, Node 26.5.0, random seed 0, a 300-second project budget, a 5-second method budget, at most 20 entry files and 40 entry methods per project. Its run metadata records `unknownCallModelSelection` as `NONE`. `ts.array.shift` has no EtsIR artifact. For `ts.array.pop`, the generated `etsIrHash` remains unavailable from the built-in wrapper and must not be invented.
