# USVM TypeScript property-based testing

This module is the integration baseline for the fast-check and symbolic-execution pipeline tracked by
[issue #346](https://github.com/UnitTestBot/usvm/issues/346). It currently contains only the native TypeScript frontend
smoke test; property execution is introduced by later issues in epic #345.

## Prerequisites

- JDK 11 (the version used by the USVM TypeScript CI job).
- Gradle 8.11 through the repository's `./gradlew` wrapper.
- Node.js 18.18 or newer on `PATH`; JacoDB `neo` uses Node.js 20.20.2.

The module resolves JacoDB `neo` commit `9ea33879c9`. The published `jacodb-ets` artifact bundles the native
`ts-frontend` runtime and matching TypeScript libraries, so the focused check needs neither ArkAnalyzer nor
`npm install`.

## Focused check

```shell
env -u ARKANALYZER_DIR ETS_IR_PROVIDER=ts-frontend \
  ./gradlew --no-daemon :usvm-ts-pbt:clean :usvm-ts-pbt:check
```

The smoke test loads `FrontendBaseline.ts`, finds `absoluteValue` in EtsIR, and verifies its source origins.

## Local JacoDB checkout

For frontend development, substitute a local JacoDB `neo` checkout:

```shell
env -u ARKANALYZER_DIR ETS_IR_PROVIDER=ts-frontend \
  ./gradlew --no-daemon -PuseLocalJacodb=/absolute/path/to/jacodb \
  :usvm-ts-pbt:clean :usvm-ts-pbt:check
```

The composite build installs and builds `jacodb-ets/ts-frontend` through its Gradle tasks. `ETS_FRONTEND_DIR`,
`ETS_FRONTEND_SCRIPT`, and `NODE_EXECUTABLE` remain available as JacoDB runtime overrides when testing an already
built frontend directly. Use a regular JacoDB clone rather than a linked worktree: JacoDB's Git hooks Gradle plugin
currently requires a `.git` directory.
