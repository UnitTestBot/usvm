# Module semantics contract v1

This directory is a dependency-neutral executable contract for TypeScript/ESM
module semantics. It contains no interpreter implementation and deliberately
does not create a shared runtime package. The `.mjs` inputs use syntax accepted
by both JavaScript ESM and TypeScript; Node is the reference implementation.

`module-semantics-v1.json` freezes nine cases:

- dependency-first, once-only file initialization;
- namespace, default and named imports;
- cross-file re-exports and live bindings;
- `util.defaultEquals` as a callable namespace export, exercised through a
  reduced copy of the relevant `typescript-collections` array APIs;
- explicit namespace-member absence;
- missing named import, ambiguous star re-export and cycle/TDZ failures.

`undefined` is never a binding fallback. It appears only in the positive
explicit-absence case, where the contract names the absent export. Missing and
ambiguous named imports link-fail; premature cyclic access initialization-fails.

Run the standalone Node differential from the repository root:

```bash
node --test usvm-ts-pbt/src/test/resources/semantics/module/module-spec.test.cjs
```

The Kotlin test validates the same Node protocol, hard-fails open CAP labels or
implicit absence, and joins all 11 historical namespace/callable witnesses to
the immutable 2026-07-19 broad denominator, target manifest and observations.
Those witnesses are shared with `WP-SEM-CALLABLE`, but this package claims only
module initialization, namespace/import resolution and cross-file binding. The
publication union must deduplicate them by exact `branchId`.

The source callable binding is marked `exact`: the frozen source-selection
artifact identifies one exported free function. EtsIR-to-TypeScript mapping is
marked `unmapped`, not `exact`, because the frozen v1 campaign has source ranges
but no emitted-JS v2 source-map artifact. The EtsIR origin itself is the frozen
branch ID verbatim, never a synthetic substitute.
