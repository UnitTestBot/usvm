# USVM TypeScript property-based testing

`usvm-ts-pbt` is the integration baseline for the fast-check and symbolic-execution pipeline tracked by
[issue #346](https://github.com/UnitTestBot/usvm/issues/346). Property execution is introduced by later issues.

## Design

- TypeScript is parsed by the repository's default JacoDB native `ts-frontend`; ArkAnalyzer is not required.
- The module follows the repository-wide JacoDB dependency without a separate version pin.
- The smoke test loads a TypeScript method into EtsIR and verifies its CFG and `EtsSourceSpan` origins.

## Run

Requires JDK 11, Node.js 18.18 or newer, and the repository's Gradle wrapper.

```shell
env -u ARKANALYZER_DIR ETS_IR_PROVIDER=ts-frontend \
  ./gradlew --no-daemon :usvm-ts-pbt:clean :usvm-ts-pbt:check
```

To substitute a local JacoDB checkout:

```shell
env -u ARKANALYZER_DIR ETS_IR_PROVIDER=ts-frontend \
  ./gradlew --no-daemon -PuseLocalJacodb=/absolute/path/to/jacodb \
  :usvm-ts-pbt:clean :usvm-ts-pbt:check
```
