# Paper: Hybrid PBT and Targeted Symbolic Execution over ArkIR

LaTeX sources of the paper (acmart, `sigconf,nonacm`), written in the style of
the companion ICCQ paper "Path-Minimal Objects in ArkTS Symbolic Execution".

## Build

```bash
latexmk -pdf main.tex     # requires TeX Live with acmart; output: main.pdf (~6 pages)
```

Build artifacts are gitignored; only `main.tex`, `refs.bib` and this README
are tracked. This branch (`caelmbleidd/pbt_article`) is the ts_pbt branch plus
the paper commits — rebase it onto `caelmbleidd/ts_pbt` when the prototype
moves.

## Data provenance

- Table 1 (maths ablation) and the numbers in §3.3: produced by
  `usvm-ts-pbt/benchmarks/run-project.sh TheAlgorithms-TypeScript --include maths`
  (seed 0, pbt-iterations 1000, target-timeout 10 s, ts-frontend provider);
  raw per-mode JSON reports land in `usvm-ts-pbt/benchmarks/results/`.
- §3.1 differential findings: `ConcreteVsSymbolicDifferentialTest` +
  the research notes `docs/ts-pbt/02` and `04` (root causes, whitelist).
- The 64% -> 83% diagnosis story in §3.3: notes `02`/`03` and the
  `maths` vs `maths2`/`maths3` result sets.
- §3.2 micro-benchmarks: `usvm-ts-pbt/src/test/resources/pbt/HybridSamples.ts`
  and `HybridE2eTest`.

When re-running campaigns changes the numbers, update: the abstract (last
sentence), Table 1 + the three observations in §3.3, and the corpus paragraph
of §3.3 (the diagnosis story quotes the old 64%/34.8% baselines on purpose).

## Status / TODO

- Qualitative + first-corpus evaluation is in; a broader campaign (more
  projects via `run-project.sh`, wall-time/solver-call ablation of the type
  hints, mutation-based bug seeding) is described as staged work in §4.
- Venue formatting not chosen yet (`nonacm` is set); adjust `\documentclass`
  options when the target is known.
