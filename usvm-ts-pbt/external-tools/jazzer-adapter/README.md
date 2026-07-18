# Jazzer.js → External Test Corpus adapter

The adapter uses one deterministic decoder for both the live fuzz target and
offline corpus export. Therefore every typed input exported to ETC is exactly
the input that Jazzer.js executed from the corresponding raw bytes.

The harness must be CommonJS on Node 18 (compile TypeScript first):

```js
// harness.cjs
const { magic } = require("./compiled-project.cjs");
exports.invoke = (args) => magic(...args);
exports.toCorpusCase = (args) => ({ receiver: undefined, arguments: args });
```

Run a bounded fuzzing campaign and export its raw seed/coverage corpus and
crashes directly to ETC:

```bash
npm ci
npm run fuzz -- \
  --manifest /tmp/targets.json \
  --method 'src/math.ts::%dflt::magic/1' \
  --harness /tmp/harness.cjs \
  --instrument /absolute/path/to/compiled/project/ \
  --corpus /tmp/jazzer-corpus --crashes /tmp/jazzer-crashes \
  --seconds 60 --seed 42 --sync \
  --out /tmp/jazzer.json --log /tmp/jazzer.log
```

Seed Jazzer.js with ETC produced by internal PBT, fast-check, or USVM:

```bash
node src/seed-corpus.cjs \
  --manifest /tmp/targets.json --method '<methodId>' \
  --external-inputs /tmp/fast-check.jsonl --out /tmp/jazzer-corpus
```

The automatic decoder supports primitives, literals, unions, arrays, tuples,
optional/rest parameters, and bounded plain records. Inputs outside that
invertible subset are explicitly rejected by the seed importer. Jazzer's raw
corpus remains the source of truth; EtsIR coverage is credited only after ETC
replay by `usvm-ts-pbt`. An empty corpus receives one explicit zero seed because
libFuzzer otherwise executes an implicit empty input without saving it, which
would make offline replay incomplete.
