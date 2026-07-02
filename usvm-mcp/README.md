# usvm-mcp

An [MCP (Model Context Protocol)](https://modelcontextprotocol.io) server that exposes the USVM
symbolic execution engine for TypeScript (`usvm-ts`) as a set of tools for LLM agents
(Claude Code, Claude Desktop, MCP Inspector, or any other MCP client).

The goal is **hybrid analysis of dynamic languages**: the LLM reads and writes code,
while the symbolic machine provides ground-truth facts about it — concrete inputs per
execution path, reachability witnesses, crashing inputs, and counterexamples to
hypotheses formulated by the LLM.

## Prerequisites

1. **JDK 11+** — the MCP Kotlin SDK requires JVM 11 (the rest of USVM targets 1.8;
   this leaf module overrides the target).
2. **Node.js** — used by ArkAnalyzer to convert TypeScript into ETS IR.
3. **ArkAnalyzer** — a built checkout, pointed to by the `ARKANALYZER_DIR` environment variable:

   ```bash
   git clone https://gitee.com/openharmony-sig/arkanalyzer
   cd arkanalyzer && npm install && npm run build
   export ARKANALYZER_DIR=$(pwd)
   ```

## Build and run

```bash
./gradlew :usvm-mcp:installDist
```

This produces a launcher at `usvm-mcp/build/install/usvm-mcp/bin/usvm-mcp`.
The server speaks MCP over **stdio**: stdout carries JSON-RPC, all logging goes to stderr.

### Connect from Claude Code

```bash
claude mcp add usvm \
  -e ARKANALYZER_DIR=/path/to/arkanalyzer \
  -- /path/to/usvm/usvm-mcp/build/install/usvm-mcp/bin/usvm-mcp
```

### Connect from MCP Inspector (debugging)

```bash
ARKANALYZER_DIR=/path/to/arkanalyzer \
  npx @modelcontextprotocol/inspector usvm-mcp/build/install/usvm-mcp/bin/usvm-mcp
```

### Run from Gradle (debugging)

```bash
./gradlew :usvm-mcp:run
```

## Tools

All tools take a `file` argument — a path to a single `.ts` file. Converted scenes are
cached in memory (keyed by path and mtime), so repeated calls on the same file are fast.
Analysis tools also accept `timeoutMs` (default 30000, max 300000).

| Tool | What it does |
|------|--------------|
| `list_methods` | Lists classes and methods visible to the machine. Call it first to discover exact `class`/`method` argument values. Top-level functions live in a synthetic `%dflt` class. |
| `get_method_ir` | Dumps the CFG of a method: IR statements with indices and successor indices. **Statement indices are the only way to address a statement** (source line numbers are not preserved in the IR); pass them as `stmtIndex` to `check_reachability`. |
| `generate_tests` | Symbolically executes a method and returns one test case per explored path: concrete `parameters`/`thisInstance` plus the expected return value or exception. |
| `check_exceptions` | Same exploration, but reports only the paths that throw, together with the inputs that trigger them. |
| `check_reachability` | Directed (targeted) search towards a given statement. Returns `REACHABLE` with a witness (concrete inputs) or `NOT_REACHED_WITHIN_BUDGET`. |
| `find_unreachable_code` | Reports `if` branches never taken during exploration — dead-code candidates (statement indices match `get_method_ir`). |
| `find_counterexample` | Tries to **falsify a boolean property function** written by the LLM: searches for inputs where it returns `false` (counterexamples) or throws (crashes). |

### Result format

Analysis results are JSON. Concrete values map to JSON naturally; JS-specific values are
tagged objects so nothing is ambiguous:

```json
{
  "kind": "SUCCESS",
  "thisInstance": { "$kind": "object", "class": "BasicConditions", "properties": {} },
  "parameters": [ 12.0, { "$kind": "number", "value": "NaN" }, { "$kind": "undefined" } ],
  "returnValue": 1.0
}
```

Exceptional paths use `"kind": "EXCEPTION"` with an `exception` object instead of `returnValue`.

## Hybrid workflows

**Test generation.** `generate_tests` yields per-path inputs and outcomes; the LLM turns
them into a unit-test file with real assertions and can immediately execute it to validate.

**Reachability querying.** `get_method_ir` → pick the index of an interesting statement
(a `return`, a branch) → `check_reachability`. A witness is a ready-made regression input.

**Hypothesis falsification** (`find_counterexample`). The LLM writes a property function
into a `.ts` file and asks the machine to break it:

```typescript
function abs(x: number): number {
    if (x < 0) return -x;
    return x;
}

// Hypothesis: "abs is always non-negative"
function propAbsNonNegative(x: number): boolean {
    return abs(x) >= 0;
}
```

`find_counterexample(file, method="propAbsNonNegative")` → `COUNTEREXAMPLE_FOUND` with
concrete falsifying inputs, or `NO_COUNTEREXAMPLE_WITHIN_BUDGET`.

**Equivalence checking of a refactoring.** A special case of the above: the LLM puts the
original `f`, its refactored `g`, and `function equiv(x: number): boolean { return f(x) === g(x); }`
into one file and falsifies `equiv`. A counterexample is an input where the refactoring
changed behavior.

## Interpreting verdicts honestly

- Exploration is **bounded by a time budget**. `NOT_REACHED_WITHIN_BUDGET`,
  an empty `check_exceptions` result, or `NO_COUNTEREXAMPLE_WITHIN_BUDGET` are
  *evidence*, not proofs. Retry with a larger `timeoutMs` when it matters.
- The symbolic model **over-approximates JavaScript semantics** in places
  (e.g., number comparisons involving `NaN` and untyped values), so a `REACHABLE`
  witness may occasionally be spurious. The recommended hybrid loop is to
  **validate every witness by actually running the code** with the reported inputs
  (node/ts-node) — the tool responses remind about this. The same over-approximation
  can make `find_unreachable_code` miss dead branches.
- The property/code for `find_counterexample` must live in a **single `.ts` file**
  (project-level scenes are not wired up yet), and must stay within the TS subset
  supported by `usvm-ts` (numbers, booleans, objects, arrays; strings partially).

## Module layout

```
src/main/kotlin/org/usvm/mcp/
├── Main.kt                  # stdio transport, stdout guard
├── UsvmMcpServer.kt         # server construction, tool registration
├── McpErrors.kt             # expected-failure handling (isError results)
├── scene/                   # EtsScene cache (ArkAnalyzer), method lookup
├── exec/                    # UMachineOptions presets, serialized machine runs
├── json/                    # DTOs and TsTestValue -> JSON rendering
└── tools/                   # one file per MCP tool
```

Design notes:

- **stdout discipline**: stdout is the JSON-RPC channel. `Main.kt` re-points `System.out`
  to stderr before anything else, and `logback.xml` sends all logging to stderr
  (`org.usvm`/`org.jacodb`/`io.ksmt` are capped at `WARN`).
- **One analysis at a time**: machine runs are serialized with a mutex; MCP clients may
  issue concurrent calls, but the solver and the machine are heavyweight.
- Concrete values are resolved from symbolic states by `TsTestResolver`
  (`usvm-ts`, `org.usvm.util`), shared with the usvm-ts test infrastructure.

## Tests

```bash
./gradlew :usvm-mcp:test       # unit tests (no ArkAnalyzer required)
```

For an end-to-end check, use the MCP Inspector recipe above on
`usvm-ts/src/test/resources/reachability/BasicConditions.ts`.
