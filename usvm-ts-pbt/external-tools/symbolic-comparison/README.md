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

For several one-method project reports, use the campaign aggregator. Each case
is `label,project,external-replay,usvm-symbolic,expose-raw`:

```bash
npm run campaign -- \
  --case sieve,TheAlgorithms-TypeScript,/tmp/sieve-replay.json,/tmp/sieve-usvm.json,/tmp/sieve-raw.json \
  --case swap,typescript-collections,/tmp/swap-replay.json,/tmp/swap-usvm.json,/tmp/swap-raw.json \
  --margin-points 2 --out /tmp/campaign.json
```

The aggregator rejects a case when method identities or EtsIR branch totals
disagree. It also requires `generatedExecutions=0` in the external replay
report, preventing accidental inclusion of internal PBT coverage.
