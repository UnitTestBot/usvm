# USVM TypeScript FastCheck backend

`usvm-ts-fast-check` implements the backend-neutral contracts from `usvm-ts-pbt` with FastCheck. It owns
`FastCheckBackend`, the Node adapter, supervised process transport, c8 coverage collection, CLI, and packaged
runtime.

The public property model, validation, registries, coverage contracts and decoders, and property-to-EtsIR mapping
remain in [`usvm-ts-pbt`](../usvm-ts-pbt/README.md).

## Branch coverage boundary

One c8 execution produces the existing source-mapped Istanbul statement report and raw V8 ranges. The bounded
TypeScript branch converter reads those same raw ranges, the executed source snapshot, and the source map. It emits
ordered `if` arms only when the original TypeScript points map back exactly, each arm has a distinct V8 execution
point, and the arm counts add up to the count at the condition. A supported `if` without `else` additionally needs
a single terminating `return` or `throw` in its true arm and a following statement that is reached only on false.
Nested `if` statements are supported under those conditions. Ambiguous counts or ranges and unsupported constructs
produce diagnostics; exact statement mappings remain available.

Loops, `switch`, conditional expressions, logical short-circuit ranges, and function or script ranges are never
reinterpreted as `if` arms. The converter does not instrument the TypeScript program or execute it a second time.
The resulting edges are coverage artifacts; target-selection integration belongs to #399/#355.

Run the backend checks with:

```shell
env -u ARKANALYZER_DIR ETS_IR_PROVIDER=ts-frontend \
  ./gradlew --no-daemon :usvm-ts-fast-check:check
```
