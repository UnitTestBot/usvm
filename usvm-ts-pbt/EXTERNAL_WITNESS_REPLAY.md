# External witness replay and reduction

`ExternalWitnessBackend.confirmWitness` consumes an extracted input, executes the complete registered original
TypeScript precondition and predicate, and returns a serializable `WitnessEvidenceDelta`. It does not replace the
original oracle with an internal helper or a challenge predicate. `FastCheckBackend` implements this API using
its existing tagged codecs, invocation isolation, process supervisor, and source coverage collector.

## Direct symbolic candidates

```kotlin
val deadline = WitnessReplayDeadline(timeoutMillis = 30_000)
val symbolic = searcher.search(
    manifest = property.toManifest(),
    mapping = mapping,
    concreteCapability = concreteCapability,
)
val candidate = symbolic.captureWitness(
    property = property,
    sources = selectedSourceHashes,
)
val evidence = backend.confirmWitness(
    property = property,
    candidate = candidate,
    configuration = WitnessReplayConfiguration(
        timeoutMillis = 30_000,
        validationReserveMillis = 5_000,
    ),
    deadline = deadline,
)
val artifactJson = PropertyManifestJson.json.encodeToUtf8SafeString(evidence)
```

Capture requires a reached symbolic target; failed extraction produces an unrepresentable candidate. The original
candidate, reached target classification in provenance, complete manifest, manifest fingerprint, selected source
hashes, and ordered inputs remain in the artifact. A false symbolic target cannot be confirmed by a native
exception. The artifact also records original/final native outcomes, Node and FastCheck versions, adapter protocol,
accepted shrink steps, attempt counts, and encoded input sizes. Reproduction requires the same registration,
selected source files, source roots, and backend revision; it uses the saved inputs, not an arbitrary FastCheck
seed/path token.

The manifest fingerprint covers input order, domains, declared joint generator, original precondition/predicate,
assertions, and registered source/build identity. Every original callback module must have a selected SHA-256
source hash. Selected files are verified before import in every process. Hashes attest those files' exact bytes;
they do not attest unlisted transitive imports, package dependencies, or the registrant's build hash. Producers
must include all relevant local source/context revisions in the selected files and pin external dependencies in
their reproduction environment.

## Named checks and guarded auxiliary targets

For a registration without named assertions, an unbound property target identifies the original predicate export
as a single boolean/exception check. Registered named assertions cannot fall back to an unnamed predicate target. A named
check must be registered in `PropertyDefinition.assertions` and have an exact executable `WitnessBinding` whose
`pointId` equals that check ID. Auxiliary coverage/hypothesis targets always require a binding. A missing,
different, inactive, or differently numbered event cannot confirm that target.

The original callback emits a passive event after evaluating the original guard and relation. For example:

```typescript
type WitnessHook = (
  pointId: string, contextId: string, revision: string, guard: boolean, holds: boolean,
) => boolean;

function recordRelation(guard: boolean, holds: boolean): boolean {
  const hook = (globalThis as Record<symbol, unknown>)[Symbol.for('org.usvm.ts.pbt.witness')] as
    WitnessHook | undefined;

  return hook?.('result-bounded', 'public-entry/call-1/post', 'binding-v1', guard, holds) ?? holds;
}
```

Only events during the admitted original predicate count. Each invocation captures at most 256 events. Bindings
match point ID, context ID, binding revision, and zero-based occurrence exactly. Guard false is not refutation.
For a named original check, the complete oracle must also fail. A hypothesis-only refutation requires the complete
oracle to hold, and returns `CONFIRMED_HYPOTHESIS_REFUTATION`, not a property defect. If both the original oracle
and a matched hypothesis fail, the result retains the original violation and the contextual refutation separately.

This is a concrete consumer contract for explicit executable bindings. It does not establish those bindings by
source-line coincidence, infer hypotheses, or generate EtsIR challenges. Automatic observation/hypothesis binding
and the accepted producer-to-replay integration remain the responsibility of #398. Manual runtime fixtures here
do not complete that issue or the full coupled hypothesis acceptance of #353/#405.

## Reduction and failures

Admission checks declared domains and joint support independently of shrinking capability, then executes the
original precondition. False preconditions reject; exceptions and nonboolean results are errors. Predicate false
and escaping predicate exceptions are original violations. Tagged special values, positional order, and the
existing isolated input-graph invocation contract apply to every attempt. The wire codec accepts value trees and
does not reconstruct aliases or cycles across separately encoded nodes.

Replay repeats the original input within a process and in fresh supervised processes. Observed admission/outcome,
event, or failure changes report `NONDETERMINISTIC`. These finite checks do not prove universal determinism or
support persistent external/module state.

For an eligible witness, FastCheck's arbitrary must accept `canShrinkWithoutContext`. An adapter replaces only
initial generation with the external input and delegates the complete shrink stream and subsequent contexts to
the original arbitrary. Dependent generators that require unavailable native context report `UNSUPPORTED`; no
general custom shrinker is substituted. #400/#402 own generator-choice/command extensions.

The reduction predicate retains declared support, original precondition admission, the same named check or
guarded auxiliary binding, and the original oracle outcome/exception category. It repeats each attempted input.
After reduction, fresh processes recheck the complete original oracle and target again. Errors, drift, mismatches,
unsupported shrinking and timeouts preserve the original candidate and its confirmed outcome. Unvalidated partial
reductions are never published as final inputs.

`EXHAUSTED` reports backend stream completion, not global minimality. Explicit examples, zero accepted shrinks, or
an unchanged encoded byte size are not minimality proofs. Counts measure attempts and accepted backend shrink
steps; byte sizes measure serialization size, not numeric magnitude.

Replay, runtime probes, coverage conversion, and reduction consume the supplied monotonic deadline. Reduction
reserves final-validation time; synchronous/asynchronous hangs are stopped by the existing process supervisor.
Process teardown can take the supervisor's bounded shutdown grace after the execution budget expires. No fresh
full timeout is granted to the final replay.

## Scheduler evidence

The evidence delta contains the original admission/outcome and explicit terminal status even for unconfirmed
candidates. Only repeatable, admitted, target-matched native replay may contribute `confirmedEdgeAdditions` or
`contextualRefutations`. Rejected, mismatched, failed, unsupported and timed-out replay leaves goals intact.
Passing coverage/hypothesis evidence remains separate from original-property defects.

Coverage replays use only the original candidate; shrink trials are not aggregated into its coverage. Edge
additions come from the existing exact native two-arm TypeScript `if` collector, carry source hashes and condition
ranges, exclude sources with collection diagnostics, and subtract the caller's already confirmed edges. This is
source coverage evidence, not automatic EtsIR edge mapping or a completed #481/#355 scheduler.

Runtime tests in `ExternalWitnessTest`, `external-witness.test.ts`, and `PropertyExecutionConformanceTest` cover
actual external shrinking, named-check preservation, hypothesis-only evidence, context/guard mismatches, source
and registration drift, mutation isolation, special values, errors/timeouts, fresh final validation, source coverage,
and a resolved real USVM candidate passed to the original native oracle.
