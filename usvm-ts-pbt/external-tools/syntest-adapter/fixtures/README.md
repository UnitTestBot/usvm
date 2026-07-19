# Frozen primitive adapter fixtures

Run `npm run fixtures` to rebuild these files from
`benchmarks/baselines/2026-07-19`. The builder selects exactly the 42 method
IDs in `D_primitive-reference-v1.methods.tsv`, joins the already frozen
`primitiveEligible` callable evidence with each branch's TypeScript condition
origin, and emits canonical v2 input artifacts.

The fixture mapping is exact because every selected method had one
unambiguous exported callable in the frozen mapping and every one of its 236
EtsIR branches has a concrete TypeScript condition origin. The fixture is
classification and harness evidence only. It contains no SynTest execution or
coverage observation.
