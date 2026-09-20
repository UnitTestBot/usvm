# TypeScript unknown-call development census

This census measures unknown-call events on a frozen development corpus. The projects and revisions were selected before current measurements and come from the historical TypeScript experiment corpus. They are development evidence and must not be reused as held-out evaluation projects. The manifest admits only `.ts` entry files, excludes declarations and common test suffixes, and excludes synthetic anonymous and initializer entry methods. A class is eligible when it contains at least `minMethodsPerClass` methods with at least `minStatementsPerMethod` IR statements. That threshold qualifies the class but does not discard its shorter ordinary methods. Eligible classes and their methods are ordered by stable SHA-256 ranks derived from the manifest seed and repository-relative identities. Methods are then taken round-robin across the selected classes, so `maxMethods` does not collapse the sample onto the first large class. The execution scene retains every loaded support file. Unknown calls reached inside nested source functions retain their actual containing-function identity.

The primary profile disables every optional unknown-call model and uses `FRESH_SYMBOLIC_RETURN`. Search uses `CLOSEST_TO_UNCOVERED_RANDOM` with the manifest seed and does not stop merely because the entry method reaches 100% statement coverage. Mandatory engine semantics and the legacy approximations that run before unknown-call dispatch remain enabled and identical across profiles. Therefore this census measures observed unknown-call decisions, not every call handled approximately by the engine. The raw artifact keeps every repeated event, while the summary separately deduplicates containing functions and stable source sites. A stable source site can contain multiple lowered IR calls; raw records retain the statement index and callee identity. Method results distinguish analysis that returned normally from partial analysis stopped by an engine or recording failure; a normal return does not claim exhaustive behavior outside the configured budgets and engine semantics. Existing `ts.array.shift` and `ts.array.pop` models validate the mechanism but are not described as census-selected.

Prepare each repository below `CHECKOUT_ROOT` at the exact revision recorded in `development-corpus.json`, then run:

```sh
./gradlew :usvm-ts:runUnknownCallCensus --args='census --manifest usvm-ts/experiments/unknown-call-census/development-corpus.json --checkout-root /absolute/path/to/checkouts --output /absolute/path/to/results'
```

The command refuses a checkout whose Git `HEAD` differs from the manifest or whose working tree contains tracked or untracked changes. It creates `raw.jsonl` without overwriting prior evidence, writes records incrementally, and creates `summary.json`. Use a new output directory for every run. Regenerate the summary without rerunning symbolic execution using:

```sh
./gradlew :usvm-ts:runUnknownCallCensus --args='summarize --input /absolute/path/to/results/raw.jsonl --output /absolute/path/to/results/summary.json'
```

`EMPTY_STOP` can be added as a separate profile when stopping-site counts are needed. Do not combine its counts with the primary `EMPTY_FRESH` profile.
