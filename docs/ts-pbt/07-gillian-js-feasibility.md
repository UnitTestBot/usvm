# Gillian-JS feasibility for the TS PBT platform

Snapshot inspected: `GillianPlatform/Gillian`
`b195dfc39a99d98f4d3c292537b6c572473653c3` (2026-07-18 checkout).

## What is reusable

- JS-2-GIL targets ECMAScript 5 and has explicit CommonJS parsing support.
- Whole-program symbolic testing supports `symb`, `symb_number`,
  `symb_string`, `symb_bool`, `Assume`, and `Assert`.
- The engine already has a JSON UI and a solver-backed counterexample path for
  failed assertions.
- This makes Gillian useful for a transpiled ES5 common subset and especially
  for directed, assertion-based branch reachability.

## Why it is not yet an ETC producer

Upstream `wpst --json-ui` serializes the symbolic state of every result as
`null`. It computes a concrete model only for the first failed assertion. A
normal successful path therefore has no concrete input witness to put into ETC.
Parsing the pretty-printed counterexample would be brittle and would still
cover only assertion failures.

The minimal clean fork patch is in `GillianCore/command_line/wpst_console.ml`:
for each successful final state, call the existing `SState.sat_check_f` with an
empty extra constraint and serialize the substitution next to `Exec_res`.
Directed mode can instead inject `Assert(false)` at the source span mapped to a
target EtsIR successor, but this needs one run per branch and careful source
rewriting.

## Environment result

The current tree requires OCaml 5.3+, opam, Dune 3.16, pinned Git packages,
Z3, SQLite, and GMP. The active macOS ARM host has Z3 but no OCaml, opam, Dune,
or Docker, so the engine was not built and no runtime comparison is reported.
Upstream includes a Debian/OCaml 5.3 Dockerfile; that is the preferred
reproducible route for the next campaign.

## Decision

Keep Gillian in the plan, but not as the main external baseline. ExpoSE is the
main runnable DSE comparison. Gillian enters quantitative results only after:

1. ES5 transpilation/bundling succeeds automatically for a measured corpus
   fraction;
2. the model-export patch yields ETC values;
3. every witness replays in Node and in the EtsIR interpreter;
4. unsupported ES6+/runtime features are reported separately from timeouts and
   missed branches.

The manifest classifier under
`usvm-ts-pbt/external-tools/gillian-adapter` measures the first, type-level
gate without pretending that heap-shaped inputs are automatic.
