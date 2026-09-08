# Property execution contract

This document is the normative contract shared by concrete execution, replay, shrinking, domain projection, and
symbolic property search. Backend-specific documents may describe mechanics, but they must not redefine these
semantics.

## Inputs and one invocation

Inputs follow the ordered Kotlin `PropertyDefinition.inputs` domains and the tagged `JsConcreteValue` encoding.
Argument order is preserved. `undefined`, `null`, UTF-16 strings, NaN, both infinities, negative zero, and nested
arrays retain their JavaScript meaning.

Every predicate attempt receives an isolated input graph. Concrete generation, explicit examples, replay, and
each shrinking attempt must not observe mutation left by another attempt. Aliases and cycles already present in
one backend input graph are preserved inside that invocation. The tagged wire representation is a value tree and
does not create reference identity between independently encoded nodes.

When a property has a precondition, the precondition and predicate run sequentially over the same isolated graph.
This matches symbolic execution, where both functions observe one state. A supported precondition is pure, so it
does not change that graph.

## Precondition

A supported precondition is a pure boolean function of its inputs.

| Completion            | Meaning                                                                           |
| --------------------- | --------------------------------------------------------------------------------- |
| `true`                | Admit the input and invoke the predicate.                                         |
| `false`               | Discard the input without invoking the predicate.                                 |
| Escaping exception    | Property-definition or execution error; never a discard or counterexample.        |
| Non-boolean result    | Property-definition or execution error; never a discard or counterexample.        |
| Unsupported execution | Report unsupported explicitly; do not substitute different execution semantics.   |

If a concrete run exhausts fast-check's discard budget, it completes with
`PropertyFailureKind.PRECONDITION_EXHAUSTED`. This is not a property violation and has no counterexample.

Synchronous preconditions are the initial shared concrete and symbolic subset. Existing asynchronous concrete
preconditions retain their JavaScript meaning; symbolic projection and search report them as unsupported.

## Predicate

A predicate returns a boolean.

| Completion         | Meaning                                                                        |
| ------------------ | ------------------------------------------------------------------------------ |
| `true`             | The property holds for this invocation.                                        |
| `false`            | Candidate property violation.                                                  |
| Escaping exception | Candidate property violation, including an escaping assertion exception.       |
| Non-boolean result | Property-definition or execution error; never a candidate property violation.  |

An expected exception belongs inside the property: the predicate catches it, checks it, and returns a boolean.
Asynchronous predicates remain available to the concrete backend and unsupported by symbolic execution until the
symbolic engine can preserve their meaning.

## Mutation and external state

Predicate-local mutation of supported input values is allowed. The invocation boundary isolates it from other
generated samples, explicit examples, replay, and shrinking while retaining aliases inside the current graph.

Precondition purity is an author obligation. The initial contract does not include a purity analyzer, heap
snapshotting, rollback of arbitrary side effects, mutable objects beyond the supported value model, or persistent
external or module state. Properties that depend on those behaviors are outside the supported subset.

## Projection and search classifications

Projection is always relative to the complete declared Kotlin input domain:

| Level         | Required interpretation                                                                                |
| ------------- | ------------------------------------------------------------------------------------------------------ |
| `EXACT`       | The projected values have exactly the declared domain semantics.                                       |
| `APPROXIMATE` | Diagnostics state whether the projection over-approximates, under-approximates, or combines both.      |
| `UNSUPPORTED` | The backend cannot preserve the declared semantics and must not silently execute a different property. |

An over-approximation may produce candidates outside the declared domain; they require concrete validation. An
under-approximation omits declared inputs, so an unsuccessful search cannot establish that the property holds.
Every retained approximation must document its direction and limitation in a capability diagnostic.

Only predicate `false` and escaping predicate exceptions are candidate violations. Timeout, unsupported
execution, solver uncertainty, input-resolution failure, tool failure, and discard-budget exhaustion are neither
violations nor proof that the property holds. A bounded search with no candidate reports only that no violation
was reached within that search.

## Implementation and regression points

- `fast-check-adapter/src/execute-property.ts` applies this contract to generation, explicit examples, replay, and
  shrinking through the existing fast-check invocation.
- `fast-check-adapter/src/project-domain.ts` projects the declared Kotlin domains for concrete execution.
- Downstream USVM projection and search implementations consume the same manifest and mapping artifacts and must
  link to this contract when their dependent changes are integrated.
- `src/test/resources/properties/contract/PropertyExecutionContract.ts` provides concrete regression coverage;
  downstream symbolic integration extends the same fixture with symbolic assertions.

Replay remains ordinary concrete execution with the reported seed and path. It does not introduce a separate
property runner or alternate callback semantics.
