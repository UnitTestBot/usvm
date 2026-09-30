# helpers4 `isNonEmpty` development v2 candidate

Source: [`helpers4/typescript` at `9deb33a6f6cdab3c03404217aa8e87626930733e`](https://github.com/helpers4/typescript/tree/9deb33a6f6cdab3c03404217aa8e87626930733e). `isNonEmpty.ts` and `upstream-isNonEmpty.spec.ts` are unchanged copies of the pinned [`isNonEmpty.ts`](https://github.com/helpers4/typescript/blob/9deb33a6f6cdab3c03404217aa8e87626930733e/helpers/array/isNonEmpty.ts) and [`isNonEmpty.spec.ts`](https://github.com/helpers4/typescript/blob/9deb33a6f6cdab3c03404217aa8e87626930733e/helpers/array/isNonEmpty.spec.ts); their Git blob IDs were checked against upstream and are recorded in [`candidate.json`](candidate.json). Copyright (C) 2025 baxyz; SPDX-License-Identifier: LGPL-3.0-or-later. The upstream LGPL license and the referenced GPL v3 text are included under `licenses/`. `bounded-isNonEmpty.spec.ts` is our clearly separate support restriction and does not modify the source assertion.

Run from this directory:

```sh
npm ci --ignore-scripts --no-audit --no-fund
npm run validate
```

The original suite checks (1) `isNonEmpty(arr) === true` for `fc.array(fc.anything(), {minLength:1})`, (2) false for an empty array, and (3) `arr[0]` is defined for nonempty integer arrays. The candidate **core subset is only the first assertion**, with dense integer arrays of length 1..8 and entries -16..16. The implementation is the unchanged upstream `value != null && value.length > 0`, including its TypeScript type-predicate annotation. This subset narrows support and changes the generator distribution; the native original suite is retained. The second assertion is not silently counted as supported: indexed access and `toBeDefined()` need separate #395 conformance. No fault or mutant is admitted for this case.

Status: source and native runtime semantics pinned; direct EtsIR mapping and symbolic search of the original implementation/predicate are **unverified**. #395 can register the original one-assertion callback for the bounded subset, but input-domain projection alone does not establish execution capability. Until an exact capability check passes, classify the case `concrete-only/conditional`, outside the common paired symbolic denominator. This candidate is a simple control for capability and overhead, not evidence that relational feedback helps. It does not alter frozen development v1 or held-out membership.
