# TypeScript PBT corpus: development checkpoint v1

This is the early **development** input for #405, not the completed #356 benchmark. The machine-readable case and provenance inventory is [`development/v1/manifest.json`](development/v1/manifest.json). No comparative search run has used this version. The fixtures are TypeScript exports; #395 owns executable `PropertyManifest` registration. This metadata does not introduce another oracle language.

## Reproduce the original-runtime checks

From `usvm-ts-pbt/corpus/development/v1`:

```sh
npm ci --ignore-scripts --no-audit --no-fund
npm run validate
```

The lockfile pins fast-check 4.9.0, tsx 4.23.12 and TypeScript 5.9.2. Validation uses 500 deterministic generated inputs per correct property with seed 20260930, plus explicit witnesses. This checks the intended-correct implementations and known constructed counterexamples; it does not validate symbolic admission, branch saturation, discovery likelihood, or comparative effectiveness. Keep the source's original generator and the bounded pilot generator separate in the manifest. The upper array/number bounds are pilot support choices, not facts inferred from observations.

## Selection and analysis units

The first source-linked family is es-toolkit's generated chunk property. It keeps all three assertions: flattened order, full non-last chunks, and nonempty chunks no larger than `size`. The second is fast-check's own duplicate-removal test. That upstream test intentionally uses a faulty identity implementation to check generation bias; its oracle is useful here, but the fault is **source-authored controlled**, not a real defect in fast-check. Our Set-based deduplicator is a locally authored correct baseline, not an upstream implementation. The two source projects each contribute one property family. The three curated scalar mechanisms are one constructed project and do not increase the count of independent real projects. The Boolean fixture translations preserve the stated oracle checks, but native suite registration/import through #395 remains unverified. There is no claim of representative sampling or a measured improvement.

The pilot admits an upstream property only if its original predicate is meaningful, the relevant implementation and source license/revision are recoverable, and a bounded numeric/dense-array support can be represented without changing the assertion. We exclude oracle replacements such as no-throw checks, opaque generator construction, and cases needing #400–#403 from the **core direct-comparison denominator**, while preserving them in the full #356 inventory. A false specification is never scored as a faulty implementation. The `1.8` floating-point round trip is an excluded semantic diagnostic. The exact supported denominator is determined per mode after #395 registration and capability checks; missing modes are reported `unsupported`, never as no-fault results.

The curated parity fixture has a controlled branchless numeric perturbation at input 7. Its parity oracle fails there after ordinary input 6 passes. It is a **candidate** saturated-coverage mechanism until #382 verifies exact real-runtime branch traces and #395/#352 verify symbolic support. The absolute-value fixture separates a passing hypothesis refutation from a property failure. The constant-output fixture must remain in the pilot even if feedback yields no benefit. Source-authored and curated faults are counted separately; no real-world implementation defect has been admitted yet. Each controlled fault has an explicit non-equivalence witness in the manifest; witnesses are validation only and must stay out of search seeds and relation training.

## Split and revision rule

All v1 fixtures, their witnesses, settings, outcomes and any future tuning are development data. Freeze a later held-out set by **project and property family** before inspecting its results; neither another mutant of these functions nor a later version of the same family can enter held-out. Select later projects by an outcome-independent published inventory and eligibility rule. Keep excluded/unsupported cases visible. Any change to v1 selection, bounds, oracle, mutant or seed plan creates v2, retaining v1 and its runs. Final held-out collection, #400–#403 strata, known real defects and validated mutants remain work for the full #356/#357 delivery.

## License and adaptation boundary

The es-toolkit implementation is adapted from its pinned MIT source. The fast-check test oracle is translated from a Vitest length assertion into equivalent Boolean equality; its deliberate faulty implementation comes from the source, while our Set-based correct implementation does not. The upstream license URLs and copyright notices are in the manifest. The `adaptation` entries record estimated manual glue and exact semantic changes; timed person-effort has **not** been measured. Before any published empirical comparison, register and execute the original assertion callback through #395, record actual edit time and annotations, and keep the same oracle/support across all eligible modes.
