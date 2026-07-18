# Source coverage → EtsIR branch mapper

This adapter projects external coverage claims onto stable branch IDs from the
`usvm-ts-pbt` target manifest. It is deliberately conservative: only a unique
source arm mapped to a unique EtsIR edge is credited. `one-to-many`, ambiguous,
synthetic, and unmapped cases stay visible in the report and require ETC replay.

Istanbul/c8 coverage (use c8's source-map-remapped `coverage-final.json` for
compiled TypeScript):

```bash
npm run map -- --manifest targets.json \
  --istanbul coverage/coverage-final.json --out mapped-coverage.json
```

Raw V8 precise coverage is supported only when the script URL names the same
source file and its offsets therefore match the manifest. For emitted
JavaScript, first let c8 remap offsets through the TypeScript source map and use
the Istanbul importer:

```bash
npm run map -- --manifest targets.json \
  --v8 /tmp/v8-coverage.json --out mapped-v8.json
```

ExpoSE's structured JSON contains Jalangi source maps and decision flags and is
accepted directly (without parsing its console report):

```bash
npm run map -- --manifest targets.json \
  --expose /tmp/expose-raw.json \
  --source-map /tmp/compiled/magic.js.map \
  --generated-line-offset 1 \
  --out mapped-expose.json
```

`--generated-line-offset` is explicit because Jalangi can remove an emitted
CommonJS `"use strict"` prologue before assigning IIDs. It was `1` for the
validated TypeScript CommonJS fixture and `0` for a hand-written CommonJS file;
the mapper does not guess and silently shift coverage.

Istanbul lines are converted from one-based to the zero-based locations stored
by the EtsIR frontend. Successor-span overlap is preferred; a conservative
condition/ordinal fallback is used only for two-arm `if` and `cond-expr`
records. External claims never replace concrete EtsIR replay as ground truth.
