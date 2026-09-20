# USVM TypeScript FastCheck backend

`usvm-ts-fast-check` implements the backend-neutral contracts from `usvm-ts-pbt` with FastCheck. It owns
`FastCheckBackend`, the Node adapter, supervised process transport, c8 coverage collection, CLI, and packaged
runtime.

The public property model, validation, registries, coverage contracts and decoders, and property-to-EtsIR mapping
remain in [`usvm-ts-pbt`](../usvm-ts-pbt/README.md).

Run the backend checks with:

```shell
env -u ARKANALYZER_DIR ETS_IR_PROVIDER=ts-frontend \
  ./gradlew --no-daemon :usvm-ts-fast-check:check
```
