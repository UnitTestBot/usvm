# TypeScript unknown-call development census

This census measures unknown-call events on a frozen development corpus. The projects and revisions were selected before current measurements and come from the historical TypeScript experiment corpus. They are development evidence and must not be reused as held-out evaluation projects. The manifest admits only `.ts` sources, excludes declarations and common test suffixes, excludes synthetic anonymous and initializer entry methods, sorts stable repository-relative file/function identities, and takes the bounded prefix recorded in `limits`. Unknown calls reached inside nested source functions retain their actual containing-function identity.

The primary profile disables every optional unknown-call model and uses `FRESH_SYMBOLIC_RETURN`. Mandatory engine semantics and the legacy approximations that run before unknown-call dispatch remain enabled and identical across profiles. Therefore this census measures observed unknown-call decisions, not every call handled approximately by the engine. The raw artifact keeps every repeated event, while the summary separately deduplicates containing functions and stable source sites. Existing `ts.array.shift` and `ts.array.pop` models validate the mechanism but are not described as census-selected.

Prepare each repository below `CHECKOUT_ROOT` at the exact revision recorded in `development-corpus.json`, then run:

```sh
./gradlew :usvm-ts:runUnknownCallCensus --args='census --manifest usvm-ts/experiments/unknown-call-census/development-corpus.json --checkout-root /absolute/path/to/checkouts --output /absolute/path/to/results'
```

The command refuses a checkout whose Git `HEAD` differs from the manifest. It writes `raw.jsonl` incrementally and creates `summary.json`. Regenerate the summary without rerunning symbolic execution using:

```sh
./gradlew :usvm-ts:runUnknownCallCensus --args='summarize --input /absolute/path/to/results/raw.jsonl --output /absolute/path/to/results/summary.json'
```

`EMPTY_STOP` can be added as a separate profile when stopping-site counts are needed. Do not combine its counts with the primary `EMPTY_FRESH` profile.
