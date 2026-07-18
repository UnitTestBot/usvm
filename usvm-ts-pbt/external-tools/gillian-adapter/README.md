# Gillian-JS feasibility adapter

Gillian-JS is not treated as a drop-in corpus generator. The current upstream
whole-program symbolic tester accepts ES5 plus `symb*`, `Assume`, and `Assert`
constructs, while its `--json-ui` intentionally serializes final states as
`null`. Countermodels are produced for failed assertions, not for every
successfully explored path.

The first adapter stage classifies which manifest entries can at least receive
automatic symbolic declarations after TypeScript→ES5 transpilation/bundling:

```bash
npm run feasibility -- --manifest targets.json --out gillian-feasibility.json
```

Primitive `number`, `boolean`, `string`, and untyped parameters map to
`symb_number()`, `symb_bool()`, `symb_string()`, and `symb()`. Instance
receivers, arrays, rest parameters, unions, and class-backed objects are marked
`custom-harness`; they are never silently reduced to primitives.

Tested source snapshot: Gillian commit
`b195dfc39a99d98f4d3c292537b6c572473653c3`. It requires OCaml 5.3, opam,
Dune 3.16, pinned Git dependencies, Z3, SQLite and GMP. This host currently has
Z3 but no OCaml/opam/Dune toolchain or Docker, so no Gillian execution is
claimed. A reproducible build should use upstream `docker/Dockerfile.build` or
an isolated opam switch rather than mutate the USVM environment.

Two viable witness-export patches remain:

1. change `wpst_console.ml` so `--json-ui` runs `SState.sat_check_f` for each
   successful final state and serializes the resulting substitution;
2. instrument each mapped source branch with a failing assertion and export its
   countermodel (directed reachability, but one Gillian run per target).

Until one is implemented and replayed through ETC, Gillian belongs in the
feature-gap/common-subset report, not in the quantitative non-inferiority table.
