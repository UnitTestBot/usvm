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
point, and the arm counts add up to the count at the condition. An `if` without `else` uses either a terminating
true arm and a distinct false successor, or a following join whose execution count equals the condition count.
In the latter case the false count is the condition count minus the true count. Missing points, early exits and
throwing conditions that violate this conservation rule remain unknown.
Nested `if` statements are supported under those conditions. Ambiguous counts or ranges and unsupported constructs
produce diagnostics; exact statement mappings remain available.

Loops, `switch`, conditional expressions, logical short-circuit ranges, and function or script ranges are never
reinterpreted as `if` arms. The converter does not instrument the TypeScript program or execute it a second time.
The DSL's `BOTH` mode uses exact admitted coverage to guide the original property search as described below.

## PBT edge feedback in `BOTH`

`DslPropertyRunner.run(program, engine = PropertyEngine.BOTH, configuration = ...)` first runs FastCheck and then
passes its confirmed source edges to the same USVM analysis that searches for a violation of the author checks.
Coverage is enabled automatically in this mode. The predicate, domains and precondition keep their original roles;
coverage affects scheduling only and adds no constraints.

This mode collects V8 counters only during admitted predicate invocations, excluding the precondition, rejected
inputs, import initialization and shrinking trials. A synchronous worker handshake keeps synchronous precondition
and predicate calls uninterrupted. Each report carries a digest of the manifest and local TypeScript source files.
Absent, stale, unsupported and no-admission evidence produce distinct classifications. Only exact mapped `if` arms
provide known covered or uncovered edges; statement hits and missing branch records provide neither.

The deterministic selector alternates a nearest known-uncovered edge priority with a fair original-objective turn.
Covered prefixes stay in the graph. Full edge coverage stops the coverage objective while property exploration
continues; statement saturation does not stop a property search. Distances follow calls and active return sites and
are scheduling overapproximations, not claims of feasibility.
Configurations using state merging or selector loop bounds retain their existing selector with an explicit
unsupported-feedback diagnostic; this first policy does not silently bypass those controls.

When a completed property path crosses a new target, its input is replayed natively as a single explicit example:
no fallback generation and no shrinking. A rejected input, changed oracle result, incompatible source/runtime or
missing claimed edge leaves coverage unchanged. Confirmed additions immediately change subsequent selections in
the running machine. Exact branches first observed during replay also become eligible goals. Each edge has at most
two replay attempts with distinct inputs; failed targets remain listed
as uncovered after that limit. At least half of scheduling turns remain available to the original objective.

Inspect `run.symbolic?.edgeGuidance` for initial coverage/goals, actual selected states and traversed goals,
evidence revisions, replay outcomes/additions, remaining goals and diagnostics. Selection history is bounded to
4096 entries with an explicit omitted count. `run.elapsedMillis` and `run.budgetMillis` report the shared wall-time
budget; replay entries report native replay costs. FastCheck receives at most one third of the total budget.
After frontend preparation, one third of the remaining time is reserved for final witness replay and shrinking;
feedback replays use at most one third of the symbolic allocation. Mapping, startup, search and replay use the
same monotonic deadline. Stop checks occur between interpreter steps and backend calls; an already-running solver
query, frontend conversion already in progress, or process shutdown can exceed the deadline and is included in
elapsed time. A frontend conversion is never started after the deadline; `symbolicSkippedDueToDeadline` reports
that no symbolic analysis ran, and `requireSuccess()` treats it as inconclusive.

The admitted collector covers the main Node isolate only. This slice does not infer relations, change PBT
generation, retain a session between separate runner calls or implement the repeated two-way campaign of #355.

Run the backend checks with:

```shell
env -u ARKANALYZER_DIR ETS_IR_PROVIDER=ts-frontend \
  ./gradlew --no-daemon :usvm-ts-fast-check:check
```
