# Manual unsupported-prefix sample v1

This frozen, stratified 50-edge sample is drawn verbatim from
`benchmarks/baselines/2026-07-19/denominators/D_broad-v1.edges.tsv`.
Annotations were reviewed against the source revisions frozen in the baseline
manifest and the historical unsupported observations:

- 22 positives have a mandatory `yield` or raw `SpreadElement` before the
  target condition on every CFG prefix;
- 12 primitive arithmetic controls, 4 iterator-model controls, 4 comparison
  controls, 6 exact-builtin controls and 2 pre-spread array controls are not
  unconditionally unsupported. Flagged and probe-required are deliberately
  negative for the narrow `unsupported` precision/recall measurement.

`CapabilityManualSampleTest` verifies that every ID still belongs to the
frozen broad denominator and computes the confusion matrix from the production
classifier. This fixture measures the frozen static audit only; it does not
claim that `supported_with_flag` is enabled in a campaign.
