# Replay-confirmed symbolic-engine comparison

This aggregator compares external DSE inputs only after EtsIR concrete replay
with the `SYMBOLIC_ONLY` USVM report. Raw ExpoSE timing/path counts and source
mapping diagnostics are optional annotations.

```bash
npm run summarize -- \
  --external-replay /tmp/suite-PBT_ONLY.json \
  --usvm /tmp/suite-SYMBOLIC_ONLY.json \
  --expose-raw magic=/tmp/expose-magic/raw.json \
  --mapping magic=/tmp/expose-magic/mapped.json \
  --margin-points 2 --out /tmp/comparison.json
```

The pilot pass/fail metric is aggregate replay-confirmed EtsIR branch-edge
coverage. Timing is emitted but explicitly marked non-comparable until both
engines are run by the same cold/warm process protocol with identical frontend
accounting.
