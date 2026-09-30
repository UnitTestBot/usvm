# #404 nearest-work and provisional claim ledger

Status: early input to #405, before pilot data or a comprehensive novelty review. `Yes` below means the cited primary source explicitly supports the stated mechanism; `unverified` means this pass did not establish its absence. Language alone is not a novelty argument.

| Work / primary evidence | Reused test or input structure | Feedback / inference | Symbolic challenge and replay | Boundary for this roadmap |
| --- | --- | --- | --- | --- |
| [JQF/Zest, ISSTA 2019](https://doi.org/10.1145/3293882.3330576) and [JQF project](https://github.com/rohanpadhye/JQF) | Parameterized Java property tests and pluggable generators | Coverage and validity feedback guide semantically valid generation | No symbolic relation challenge established by the cited overview | Reusing PBT generators and feeding coverage back are prior work; compare beyond coverage. |
| [Daikon project](https://plse.cs.washington.edu/daikon/) | Program execution traces | Reports **likely** point-specific invariants from observed values | No testing loop established by the cited overview | Observed relations are hypotheses, not facts or new by themselves. |
| [DSD-Crasher, TOSEM 2008 abstract](https://plse.cs.washington.edu/daikon/pubs/CsallnerSX2008-abstract.html) | Program tests/observations | Dynamic invariants restrict static analysis; a final dynamic step confirms predictions | Yes: dynamic–static–dynamic testing | Dynamic invariant plus static search plus concrete confirmation is direct prior work. Our proposed distinction needs a measured, assertion-specific target/feedback mechanism, not a generic D–S–D claim. |
| [QSYM, USENIX Security 2018](https://www.usenix.org/conference/usenixsecurity18/presentation/yun) | Fuzzer inputs for binaries | Hybrid fuzzing with concolic execution and fuzzer validation | Yes, bidirectional hybrid testing; not a user PBT oracle in the cited abstract | Returning symbolic inputs to a fuzzer and validating them is prior work. The intended TypeScript property/oracle semantics must show incremental value. |
| [HypoFuzz project](https://hypofuzz.com/) | Existing Python Hypothesis tests | Fuzzing backend and coverage dashboard; finer mechanism requires source review | Symbolic relation challenge unverified here | Existing PBT reuse and coverage are established beyond JQF; do not claim those features as novel. |

The supplied private 2026 Go/gopter + usvm-go thesis is **not redistributed** here. Its recorded mechanisms include uncovered-line guidance, input-corpus transfer and `behaviorKey` novelty filtering. Author/title/public citation must be verified with the owner before referencing it in a public paper. The output-novelty control in the pilot is required partly to test this boundary. The full #404 review must also inspect generator-choice search, metamorphic and stateful/model-based PBT, internal-context summaries, and newer property-directed testing against their primary papers; the table above is deliberately incomplete.

## Claim → required evidence

| Proposed statement | Evidence needed before making it | Current status |
| --- | --- | --- |
| The bounded core loop works | A real observation yields a nontrivial assertion-relevant relation; it changes an actual target; a replay-confirmed refutation changes later generated inputs; exact branch signal affects scheduling; all costs and mismatches are logged. | Unmeasured; fixtures and protocol only. |
| It finds more distinct faults or confirms them faster than baselines | Paired equal-budget development results, then independently frozen held-out results, with original oracle, common support, uncertainty and regressions. Compare direct property search and sequential portfolio. | Unmeasured; superiority provisional. |
| Assertion focus adds value beyond generic invariants | Focused versus unfocused inference, equal vocabulary/target budget and controlled target ordering. | Planned contrast only. |
| Observed relations add value beyond coverage/templates/output novelty | Coverage-only, observation-independent templates and output-novelty controls on the same eligible cases and budget. | Planned contrast only. |
| Extensions add value | End-to-end #400–#403 enable/disable comparisons on their supported strata after the core checkpoint. | Out of this early checkpoint. |
| Any bounded symbolic conclusion is sound | Precisely stated support, guards, context and solver assumptions plus original-runtime replay; finite unsuccessful search is no proof. | No proof claim. |

The final ledger must link each empirical statement to a pinned implementation revision, corpus manifest, raw run IDs and table-regeneration command. #405 may report a negative or inconclusive pilot without turning functional completion into an improvement claim. No manuscript or external publication is in this PR.
