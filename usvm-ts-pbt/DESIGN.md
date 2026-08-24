# Kotlin–TypeScript fast-check integration

This document describes the internal boundary between Kotlin and the private Node adapter. For the public property
API and CLI examples, see [README.md](README.md).

## Design goals

- Kotlin owns property definitions, validation, registries, orchestration, and public results.
- Node is a thin adapter around fast-check and direct TypeScript loading.
- The JSON exchange is one request and one response from the same packaged distribution; it has no persistence or
  compatibility negotiation.
- Failures are typed without exposing runtime-dependent Node stack traces.
- A blocked or noisy child process cannot hang the JVM or exhaust unbounded memory.

## Components and dependencies

```mermaid
flowchart LR
    subgraph Kotlin
        Caller[Backend caller]
        CLI[FastCheckCli]
        Registry[PropertyRegistry]
        Model[Property model and validation]
        Backend[FastCheckBackend]
        Process[FastCheckProcessClient]
        Projection[FastCheckProjectionClient]
    end

    subgraph Node_adapter[Private Node adapter]
        ExecutionCLI[execution-cli.ts]
        ProjectionCLI[projection-cli.ts]
        Execute[execute-property.ts]
        Domains[project-domain.ts]
        EntryPoints[entry-point.ts]
        Values[js-value.ts]
        Diagnostics[diagnostics.ts]
    end

    FastCheck[fast-check]
    Tsx[tsx]
    UserTS[User TypeScript source]

    CLI --> Registry
    CLI --> Backend
    Caller --> Backend
    Registry --> Model
    Backend --> Model
    Backend --> Process
    Process --> ExecutionCLI
    Projection --> ProjectionCLI
    ExecutionCLI --> Execute
    Execute --> Domains
    Execute --> EntryPoints
    Execute --> Values
    ProjectionCLI --> Domains
    Diagnostics --> ExecutionCLI
    Diagnostics --> Domains
    Diagnostics --> EntryPoints
    Domains --> FastCheck
    Execute --> FastCheck
    EntryPoints --> Tsx
    Tsx --> UserTS
```

| Component | Responsibility |
| --- | --- |
| Kotlin model and validation | Define one backend-neutral property and reject invalid structure before execution. |
| Registry and CLI | Select Kotlin-defined properties and turn user options into a run configuration. |
| `FastCheckBackend` | Validate examples, resolve source roots, and create the adapter request. |
| `FastCheckProcessClient` | Supervise Node, bound I/O, enforce the hard deadline, and validate the response. |
| `execution-cli.ts` | Read one JSON request, protect protocol stdout from user logging, and write one response. |
| `execute-property.ts` | Build the fast-check property, run it, and translate `RunDetails` into the common result. |
| `project-domain.ts` | Translate domain descriptors into real `fc.Arbitrary` instances. |
| `entry-point.ts` | Resolve exactly one module below a source root and invoke its typed export through `tsx`. |
| Value and diagnostic modules | Preserve JavaScript values losslessly and define adapter-emitted diagnostic identifiers. |

`projection-cli.ts` is the smaller sampling path used by `FastCheckProjectionClient`. It shares domain and value
translation with property execution but does not load or call user predicates.

## Boundary contract

```mermaid
flowchart LR
    Definition[PropertyDefinition] --> Validate[Kotlin validation]
    Validate --> Request[Execution request]
    Request --> Node[One Node process]
    Node --> Arbitraries[Domains to arbitraries]
    Arbitraries --> Check[fc.check]
    SourceRoots[Source roots] --> Load[tsx module loading]
    Load --> Check
    Check --> Response[Success or diagnostic response]
    Response --> Verify[Kotlin response validation]
    Verify --> Result[PropertyRunResult or PbtBackendException]
```

The execution request contains `manifest`, `sourceRoots`, optional `seed` and `replayPath`, `numRuns`,
`timeoutMillis`, and tagged `examples`. A response is either:

```text
{ status: "ok", result: PropertyRunResult }
{ status: "error", diagnostics: [{ kind, code, message, path }] }
```

There is intentionally no request ID, operation name, schema version, protocol version, backend ID, or backend
version. The exchange is private, one-shot, and produced and consumed by the same build. Adding compatibility
metadata would create branches that no supported workflow uses.

Kotlin validates trusted model objects and examples early so callers get local errors. Node validates the decoded
JSON again because the process boundary must not trust malformed input. Diagnostic codes have one owner per
language: `PbtDiagnosticCode.kt` for Kotlin and `diagnostics.ts` for Node. Node also sends the diagnostic category,
so Kotlin never infers error meaning from code prefixes.

## One property run

```mermaid
sequenceDiagram
    participant Caller
    participant Backend as FastCheckBackend
    participant Client as ProcessClient
    participant Node as execution-cli.ts
    participant FC as fast-check
    participant TS as User predicate

    Caller->>Backend: run(property, configuration)
    Backend->>Backend: validate property, roots, and examples
    Backend->>Client: check(request)
    Client->>Node: start process and write JSON
    par concurrent process I/O
        Client->>Node: drain stdout
    and
        Client->>Node: drain stderr
    end
    Node->>Node: validate request and resolve entry points
    Node->>FC: check(property, parameters)
    loop generation, replay, examples, shrinking
        FC->>TS: predicate(values)
        TS-->>FC: boolean or Promise<boolean>
    end
    FC-->>Node: RunDetails
    Node-->>Client: one JSON response
    Client->>Client: validate exit, size, shape, category, and property ID
    Client-->>Backend: PropertyRunResult
    Backend-->>Caller: PropertyRunResult
```

Input order is preserved from `PropertyDefinition.inputs` to the positional TypeScript arguments. If either the
predicate or precondition is asynchronous, the adapter uses `fc.asyncProperty`; otherwise it uses `fc.property`.
A false precondition becomes `fc.pre(false)`, leaving skip accounting to fast-check.

## Results, errors, and timeouts

```mermaid
flowchart TD
    Check[Property execution] --> Held{Outcome}
    Held -->|held| Success[SUCCESS result]
    Held -->|falsified| Failure[FAILURE result with counterexample]
    Held -->|fast-check timeout| TimeoutResult[FAILURE result with timeout details]
    Held -->|typed adapter error| Diagnostic[Error response with explicit category]
    Diagnostic --> Exception[PbtBackendException]
    Held -->|unexpected Node failure| Exit[Non-zero exit or invalid response]
    Exit --> Transport[PROCESS_FAILURE or PROTOCOL_ERROR]
    Held -->|hard JVM deadline| Kill[Terminate, then force-kill]
    Kill --> HardTimeout[TIMEOUT exception]
```

Falsification and a timeout cleanly reported by fast-check are completed property results. Invalid input,
entry-point failures, process failures, malformed responses, and the JVM hard timeout are infrastructure
exceptions.

The execution client starts stdout, stderr, and stdin work concurrently. Requests and stdout are limited to 4 MiB;
stderr is limited to 64 KiB. These are transport safety bounds, not property-policy limits. The hard deadline is the
property timeout plus two seconds for transport, followed by a 250 ms graceful shutdown before force-kill. The only
run-control maximum is `2^31 - 1` milliseconds because Node timers use signed 32-bit delays; runs, examples, and
replay paths have no arbitrary count or length caps.

## Runtime packaging

Gradle installs pinned adapter dependencies, compiles only the private adapter, and packages `dist/src` plus its
runtime dependencies in the application distribution. User TypeScript stays as source. During repository tests,
Gradle passes the adapter directory through a JVM system property; an installed distribution resolves it next to
the application libraries. Node.js 18.18 or newer is required, and runtime archives carry an OS/architecture
classifier because `tsx` depends on a native esbuild package.

## Testing boundaries

- TypeScript unit tests cover value encoding, domain projection, source-root containment, entry-point contracts,
  fast-check behavior, and the one-document CLI boundary.
- Kotlin unit tests cover the property model, registry, CLI selection, example membership, and response validation.
- Process tests use tiny temporary Node programs only for transport behavior that is difficult to force through
  fast-check: startup failure, non-zero exit, malformed output, explicit diagnostic categories, and hard timeout.
- Backend integration tests execute real uncompiled TypeScript through the packaged adapter, including replay,
  shrinking, explicit examples, preconditions, async predicates, and timeouts.

## Non-goals

- Persisting requests or results, or supporting old wire formats.
- Discovering properties by scanning TypeScript source roots.
- Compiling user TypeScript as part of the PBT workflow.
- Reimplementing generation, replay, skip accounting, or shrinking in Kotlin.
- Recording coverage or other per-run artifacts in this change.
