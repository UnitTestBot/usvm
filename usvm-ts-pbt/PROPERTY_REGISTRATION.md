# Bounded property registration (#395)

`PropertyDefinition` keeps the original exported TypeScript predicate and optional precondition as the only executable oracle. `PropertyManifest` carries the same fields to concrete and symbolic consumers. Assertions, operands, and tested-call points are author-supplied identities and source locations; they do not evaluate expressions. #396 may observe supported points once, and #397 may use the metadata to focus inference. An opaque predicate continues to run, but its internal assertions have no identities until annotated.

## Inventory and migration

The existing examples are `isCommutative` (two integers), `boundedValueStaysBounded` (one bounded integer), `divisionRoundTrip` with `nonZeroDivisor` (original precondition), and `reverseTwicePreservesValues` (array). Their predicates remain unchanged. `indexedValueIsPresent` adds a bounded array and an index constrained by its actual length.

The selected real suite is [fast-check's `ArrayArbitrary.spec.ts` `biasIts` property](https://github.com/dubzzz/fast-check/blob/85eeab9e87c9d37e66cc7819260e3df1e72305ae/packages/fast-check/test/arbitraries/ArrayArbitrary.spec.ts), MIT licensed. Its callback body and assertion `expect(filtered).toHaveLength(new Set(filtered).size)` are retained in `originalUniqueAssertion` in `ArrayArbitraryProperty.ts`. A local shim implements only the used `toHaveLength` assertion, throwing `AssertionError` on mismatch; the boolean entry point invokes the callback and returns true on normal completion. The shim preserves the failure class and tested success/failure/evaluation order for this assertion, but not Vitest's full diagnostics. The original deliberately faulty identity deduplicator still fails; a Set-based version passes. The registration bounds the input to dense arrays of length 0–8 with integers 0–10. This changes generated support and the size distribution, so it is a development adaptation, not an identical native fast-check run.

The manual adaptation is one exported boolean wrapper around the original callback, a one-method assertion shim, one `PropertyDefinition` with one input domain, one stable assertion ID, two operand IDs, source points, and a source/build-input hash. It does not translate the oracle into a Kotlin expression or another assertion DSL. The test computes SHA-256 from the TypeScript file bytes used as direct execution input; `buildScope` records that transpiled output and transitive imports are **not** pinned by this hash. Registrants of bundled projects must provide a hash and scope for their actual build artifact. The current runner transports the declared hashes but does not independently verify them against loaded files, so consumers must not infer that a matching runtime build was proved.

## Joint support

`ArrayIndexGenerator` has a stable ID and explicitly links input 0, a nonempty dense bounded array, to input 1, an integer index. The index domain must be `0..maxLength-1`; joint admissibility further requires `index < values.length`. Kotlin and the adapter reject incompatible declarations before execution. The concrete arbitrary constructs the pair together. Kotlin and the adapter reject explicit examples outside that joint set. USVM adds the corresponding constraint over the symbolic array length and index. The original predicate still decides success or failure. This direct relation does not infer support from samples and does not implement arbitrary dependent fast-check closures or generator-choice search (#400).

| Capability | Independent supported domains | Array/index relation | Native closure/combinator outside the declared model |
| --- | --- | --- | --- |
| Concrete generation and original oracle | Yes | Yes | Concrete-only in its native suite; no general import |
| Direct symbolic projection | Subject to existing EtsIR/domain capability | Yes, bounded relation | Unsupported |
| Explicit examples | Domain validation | Joint support validation | No declared validation |
| External-example shrinking | Existing `fc.check` examples; exact-input API belongs to #353 | Native chain arbitrary preserves relation during generated shrinking | No general guarantee |
| Empirical observations | #396 | #396 | No automatic observation |
| Generator-choice search | #400 | #400 | Unsupported |

Declared domains and joint support are hard admissibility conditions. The TypeScript precondition separately admits or rejects an input. Fast-check probability, size bias, and observed values are separate evidence; none narrows the declared symbolic set. Assertion false or throw is an original-oracle failure, while precondition exhaustion, unsupported behavior, and timeouts remain distinct outcomes.
