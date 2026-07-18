# fast-check → External Test Corpus adapter

This package turns a `usvm-ts-pbt` target manifest into reproducible
`fast-check` inputs and writes them as ETC JSON/JSONL for concrete EtsIR
replay.

```bash
npm ci
npm run generate -- \
  --manifest /tmp/targets.json \
  --method 'src/math.ts::%dflt::factorial/1' \
  --seed 42 --runs 1000 --out /tmp/fast-check.jsonl --jsonl
```

Without a harness, the adapter samples the inferred arbitrary and exports all
inputs. With `--harness ./harness.mjs`, it executes a property and also exports
every shrink attempt plus the minimal counterexample:

```js
// harness.mjs
import { target } from "./compiled-project.js";

export async function invoke(args) {
  target(...args); // a throw is a property failure and triggers shrinking
}

// Optional for instance methods: describe the exact EtsIR replay values.
export function toCorpusCase(args) {
  return { receiver: { threshold: 10 }, arguments: args };
}
```

The summary and counterexample metadata include fast-check's `path`. Re-run a
failure with the same `--seed`, `--path`, manifest, and adapter version.

Automatic inference currently handles primitives, literals, unions, arrays,
tuples, optional and rest parameters. Unknown types use a bounded mixture of
primitives, arrays, and plain records. Project classes deliberately degrade to
plain records unless the harness provides a schema-specific mapping.
