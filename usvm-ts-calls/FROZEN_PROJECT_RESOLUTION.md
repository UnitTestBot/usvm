# Frozen runtime source resolution

Calls class preparation supports offline imports into `.ts` runtime sources inside
one frozen project root:

- Relative `.ts`, extensionless and `.js`-to-absent-`.js`/existing-`.ts` imports.
- Local aliases through the nearest `tsconfig.json`, including relative JSON/JSONC
  `extends` chains, offline installed config package `tsconfig` entries/subpaths,
  `baseUrl`, `paths` and inherited compiler settings. The complete chain and
  config package metadata are frozen; packages must already be inside the root.
- Package self references and already-installed in-root workspace symlinks, when
  explicit package `exports` or `main` selects actual `.ts` runtime source.
  `types`/`.d.ts` entries are kept distinct from runtime entries.

A package source must have a stable project identity outside physical `node_modules`.
This first subset does not admit installed JavaScript packages, declaration-only
runtime dependencies, packages outside the frozen root, export patterns or different
`import`/`require` implementations (including mode-only exports). Package `imports` without a checked config alias, missing config packages, unresolved
external modules, dynamic imports, JSON `require` and dependency cycles remain
explicit acquisition failures. Initialization and class/domain checks still apply.
No dependency installation or network resolution runs during measurement.

`inspect-local-closure` returns `files` (runtime source files) and, when resolution
metadata exists, `resolution.inputSha256` and a derived `resolution.tsconfig`.
Freeze **every entry** in `resolution.inputSha256` in the existing manifest
`sourceSha256` map. It includes runtime bytes, the config chain, ancestor package/
workspace metadata and existing npm/pnpm/yarn/bun lockfiles. Preparation rejects a
manifest that omits them. Original resolution is recomputed at persisted verification
boundaries, so newly introduced config/exports or retargeted workspace links also
invalidate resume. Source-only coverage IDs continue to use the original class bytes
and AST offsets; imports are not rewritten.

Frontend loading and native replay copy this runtime closure, then use the same
exact import-to-source aliases in their derived config. Replay passes the config
explicitly to both tsx loaders and uses no dependency links back to the original
checkout. An alias pointing at the target class therefore reaches its instrumented
copy instead of bypassing instrumentation. The prepared config is also verified on
resume. The generated root list contains only inspected runtime files; unrelated
`include`/`exclude` selections do not enlarge the frontend or coverage universe.

`CallsFrozenProjectResolutionTest` uses the unchanged real cubing.js `Stats` class
with generated alias and pinned package consumers. It preserves both public members,
checks frontend source identities, agreeing original/instrumented observations and
nonempty class counters. This is a resolution/replay contract probe, not admission
of a historically alias-blocked corpus class or a representative experiment result.
