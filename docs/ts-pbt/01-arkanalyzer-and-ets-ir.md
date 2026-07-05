# ArkAnalyzer, the EtsIR, and how USVM-TS is wired to them

> Research note for the **Property-Based Testing (PBT) for ArkTS/TS** effort.
> Branch: `caelmbleidd/ts_pbt`. Date: 2026-06-19.
>
> Goal of this document: explain *what ArkAnalyzer is*, *how this repository
> depends on it*, *what the IR looks like*, and *whether we should keep relying
> on it or write our own `.ts`/`.js` front-end* that produces the same IR.

---

## 1. TL;DR

* `usvm-ts` (the symbolic execution engine for ArkTS/TS) does **not** parse
  TypeScript itself. It consumes an already-lowered, three-address,
  basic-block IR called **EtsIR**, exposed as Kotlin classes
  (`EtsScene` / `EtsFile` / `EtsMethod` / `EtsStmt` …) from the external
  library **`jacodb-ets`**.
* That IR is produced by **ArkAnalyzer** — a *TypeScript/Node.js* static
  analysis framework for ArkTS/OpenHarmony. ArkAnalyzer parses the source,
  lowers it to its own "ArkIR", and **serializes it to JSON**. `jacodb-ets`
  then **deserializes the JSON** into `EtsFileDto` and **lifts** it to the
  `EtsFile` model.
* The full pipeline is therefore:

  ```
  *.ts / *.ets / *.js
        │   ArkAnalyzer (Node):  src/save/serializeArkIR.js
        ▼
  EtsIR JSON  (one *.ts.json per source file)
        │   jacodb-ets:  EtsFileDto.loadFromJson()   (kotlinx.serialization)
        ▼
  EtsFileDto  (1:1 mirror of the JSON)
        │   jacodb-ets:  EtsFileDto.toEtsFile()  /  EtsMethodBuilder
        ▼
  EtsFile / EtsScene   ← "the IR you see in the project"
        │   usvm-ts:  TsMachine / TsInterpreter / TsExprResolver
        ▼
  symbolic execution  →  TsTest (org.usvm.api.TsTest)
  ```

* **Recommendation (see §6):** for PBT we should build our **own front-end**
  `.ts`/`.js → EtsIR`, because ArkAnalyzer is a heavyweight, version-coupled,
  per-file-subprocess external dependency that is tuned for ArkTS/OHOS (not
  plain JS) and is opaque/hard to control. We can target the exact same IR
  contract so that all of `usvm-ts` keeps working unchanged.

---

## 2. What is ArkAnalyzer?

**ArkAnalyzer** (sometimes written "Ark Analyzer") is an open-source static
analysis framework for **ArkTS** — the application language of Huawei's
**OpenHarmony / HarmonyOS**. ArkTS is a constrained dialect of TypeScript.

* Upstream: `https://gitcode.com/openharmony-sig/arkanalyzer` (OpenHarmony SIG).
* It is written **in TypeScript** and runs on **Node.js**.
* It builds an SSA-ish, Jimple-like **three-address IR** ("ArkIR") out of TS
  sources: `ArkFile`, `ArkClass`, `ArkMethod`, `ArkAssignStmt`,
  `ArkInstanceInvokeExpr`, etc. It also resolves imports/exports, models
  namespaces, decorators, and ships handling for the OHOS SDK. It can run
  **type inference** over the IR.

### The fork we actually use

This project does **not** use upstream ArkAnalyzer directly. It uses a
**fork maintained by `Lipen`** (a jacodb maintainer) that is kept in lock-step
with jacodb's deserializer:

* Fork: `https://gitcode.com/Lipen/arkanalyzer` (mirror: `gitee.com/Lipenx/arkanalyzer`).
* You must checkout a branch named `neo/<DATE>` that matches the current
  jacodb DTO schema. The version coupling is real: the JSON schema emitted by
  ArkAnalyzer must match the `*Dto` classes in `jacodb-ets`.
* CI currently pins branch **`neo/2025-09-03`** (see `.github/workflows/ci.yml`).
  `jacodb-ets/ARKANALYZER.md` mentions `neo/2025-02-24`; `usvm-ts-dataflow/README.md`
  mentions `neo/2024-10-31`. **These drift over time** — always read CI for the
  source of truth.

### The serializer entry point

The relevant script is **`src/save/serializeArkIR.ts`** (built to
`out/src/save/serializeArkIR.js`):

```text
Usage: serializeArkIR [options] <input> <output>
  -p, --project              input is a project directory
  -t, --infer-types [times]  run type inference N times over the IR
  -v, --verbose
  -e, --entrypoints          (load entrypoints; used in jacodb's generateEtsIR)
```

* Single file:   `node out/src/save/serializeArkIR.js sample.ts sample.json`
* Whole project: `node out/src/save/serializeArkIR.js -p project etsir`
  (mirrors the source tree, every file becomes `*.ts.json`).

There is also `src/usvm/inferTypes.ts`, a wrapper that combines serialization
with USVM's *own* type inference (`usvm-dataflow-ts`).

---

## 3. How **this repository** connects to ArkAnalyzer

There are three connection points.

### 3.1 Test-time auto-conversion (the main path for `usvm-ts`)

The test base class `usvm-ts/src/test/kotlin/org/usvm/util/TsMethodTestRunner.kt`
loads `.ts` resources via `loadEtsFileAutoConvert(...)` /
`loadEtsProjectAutoConvert(...)` from **`jacodb-ets`**
(`org.jacodb.ets.utils.LoadEtsFile`). That function:

1. reads env vars
   * `ARKANALYZER_DIR`  (default `arkanalyzer`)
   * `SERIALIZE_SCRIPT_PATH`  (default `out/src/save/serializeArkIR.js`)
   * `NODE_EXECUTABLE`  (default `node`)
2. spawns a subprocess roughly:
   `node $ARKANALYZER_DIR/out/src/save/serializeArkIR.js -t 1 <input.ts> <tmp.json> -v`
   (a temp file for a single file, a temp dir for a project),
3. deserializes the resulting JSON with `EtsFileDto.loadFromJson(stream)`,
4. lifts it with `etsFileDto.toEtsFile()`.

So **every test that loads a `.ts` file shells out to Node and runs
ArkAnalyzer on the fly.** The `.ts` sources live under
`usvm-ts/src/test/resources/samples/**`; the JSON is transient (temp files).

### 3.2 SDK IR generation (Gradle task)

`usvm-ts/build.gradle.kts` defines `generateSdkIR`, which runs
`serializeArkIR.js -p ets etsir` over the OpenHarmony SDK (`*.d.ts`) to produce
committed/cached IR for the SDK. Requires `ARKANALYZER_DIR` to be set.
`usvm-ts-dataflow/build.gradle.kts` has an analogous task.

### 3.3 CI provisioning

`.github/workflows/ci.yml` (the TS job):

* sets up Node 22,
* `git clone --depth=1 --branch neo/2025-09-03 https://gitcode.com/Lipen/arkanalyzer arkanalyzer`
  (with up to 10 retries — the remote is flaky),
* `npm install && npm run build`,
* exports `ARKANALYZER_DIR=$(realpath arkanalyzer)`,
* then `./gradlew :usvm-ts:check :usvm-ts-dataflow:check`.

### 3.4 Dependency coordinates

* `buildSrc/src/main/kotlin/Dependencies.kt`: jacodb pinned at commit
  `b17013382a` (JitPack group `com.github.UnitTestBot.jacodb`), artifact
  `jacodb-ets`.
* The `jacodb-ets` sources used for this analysis live on the jacodb branch
  `lipen/dev` (commit `e02e6fdf…`), module `jacodb-ets/`.

---

## 4. The EtsIR contract (what a producer must emit)

The JSON boundary is the set of `*Dto` classes in
`org.jacodb.ets.dto` (`jacodb-ets/src/main/kotlin/org/jacodb/ets/dto/`).
kotlinx.serialization is configured with a **class discriminator field `"_"`**
(`@JsonClassDiscriminator("_")`), and each variant has a `@SerialName`. So in
the JSON, a statement looks like `{"_":"AssignStmt","left":{…},"right":{…}}`.

### 4.1 Top-level structure (`Model.kt`, `Signatures.kt`, `Cfg.kt`)

```
EtsFileDto
  signature: { projectName, fileName }
  namespaces: [ NamespaceDto ]            // nested classes/namespaces
  classes:    [ ClassDto ]
  importInfos / exportInfos               // module resolution

ClassDto
  signature: { name, declaringFile, declaringNamespace? }
  modifiers: Int (bitmask)                // EtsModifiers
  decorators, category, typeParameters?
  superClassName?, implementedInterfaceNames
  fields:  [ FieldDto ]
  methods: [ MethodDto ]

MethodDto
  signature: { declaringClass, name, parameters:[{name,type,isOptional,isRest}], returnType }
  modifiers, decorators, typeParameters?
  body?: { locals: [LocalDto], cfg: { blocks: [ BasicBlockDto ] } }

BasicBlockDto
  id, successors:[Int], predecessors:[Int]?, stmts:[StmtDto]
```

**Control flow is encoded structurally**: there is no `Goto`/`Switch` statement.
A block lists its successor block ids. An `IfStmt` block has exactly two
successors (true branch first, then false). This is a CFG-of-basic-blocks, not
an AST.

### 4.2 Statements (`Stmts.kt`) — only 7 kinds

| `_` (SerialName) | Payload |
|---|---|
| `NopStmt` | — |
| `AssignStmt` | `left: Value`, `right: Value` |
| `CallStmt` | `expr: CallExpr` |
| `ReturnVoidStmt` | — |
| `ReturnStmt` | `arg: Value` |
| `ThrowStmt` | `arg: Value` |
| `IfStmt` | `condition: ConditionExpr` |
| `RawStmt` | escape hatch: `kind` + `extra` (raw JSON) |

### 4.3 Values / expressions (`Values.kt`)

* **Immediates:** `Local{name,type}`, `Constant{value:String,type}`.
  ⚠️ **All constants are strings** (`"3.14"`, `"true"`, `"hello"`) tagged with a
  `type` (`NumberType`/`BooleanType`/`StringType`/…). There are no typed numeric
  constants in the DTO yet (there is a long commented-out block in `Values.kt`
  describing the intended future split).
* **Exprs:** `NewExpr`, `NewArrayExpr`, `DeleteExpr`, `AwaitExpr`, `YieldExpr`,
  `TypeOfExpr`, `InstanceOfExpr`, `CastExpr`, `UnopExpr{op}`, `BinopExpr{op}`,
  `ConditionExpr{op}`, and calls `InstanceCallExpr` / `StaticCallExpr` /
  `PtrCallExpr` (each carries a `MethodSignature` + `args`).
* **Refs:** `ThisRef`, `ParameterRef{index}`, `CaughtExceptionRef`,
  `GlobalRef{name,ref?}`, `ClosureFieldRef`, `ArrayRef{array,index}`,
  `InstanceFieldRef{instance,field}`, `StaticFieldRef{field}`.
* Operators are **strings** (`Ops.kt`): unary `+ - ! ~ ++ --`; binary
  `+ - * / % ** << >> >>> & | ^ && || ??`; relational `== != === !== < <= > >= in`.
* `RawValue` escape hatch mirrors `RawStmt`.

### 4.4 Types (`Types.kt`)

`AnyType`, `UnknownType`, `GenericType`, `AliasType`, `LexicalEnvType`,
`EnumValueType`, `VoidType`, `NeverType`, `UnionType`, `IntersectionType`,
primitives (`BooleanType`, `NumberType`, `StringType`, `NullType`,
`UndefinedType`, `LiteralType`), `ClassType`, `UnclearReferenceType`,
`ArrayType{elementType,dimensions}`, `TupleType`, `FunctionType`. Plus
`RawType`. The `-t` flag controls how well these get filled in vs left
`UnknownType`/`UnclearReferenceType`.

### 4.5 The DTO → model lift (`Convert.kt`, `EtsMethodBuilder`)

`toEtsFile()` is **not a trivial 1:1 copy**. The `EtsMethodBuilder`:

* re-establishes strict **three-address form**: nested sub-expressions are
  flattened into fresh temporaries (`_tmp0`, `_tmp1`, … via `ensureLocal`),
* maps operator strings to concrete typed classes (`"+" → EtsAddExpr`,
  `"==" → EtsEqExpr`, …),
* attaches `EtsStmtLocation`s and builds the linear/block CFG
  (`EtsBlockCfg`).

**Key takeaway for a custom producer:** the JSON we emit must *already* be
basic-block-structured and *roughly* three-address (one operation per
statement, operands are immediates/refs). ArkAnalyzer does the genuinely hard
lowering (AST → 3AC → CFG); `toEtsFile` only does the final normalization. So a
replacement front-end has to reproduce that lowering, not just a syntax tree.

---

## 5. How the IR is used downstream (relevance to PBT)

* `usvm-ts` walks `EtsMethod.cfg`, resolving each `EtsStmt` /`EtsExpr` in
  `TsInterpreter` / `TsExprResolver`, and emits `org.usvm.api.TsTest`:
  `{ method, before/after parameter states, returnValue, trace }`, where values
  are `TsTestValue` (`TsNumber`, `TsString`, `TsBoolean`, `TsClass`, `TsArray`,
  `TsNull`, `TsUndefined`, exceptions, …).
* For PBT this matters: the engine already gives us concrete input models and
  return values per path. A PBT loop wants to (a) *obtain IR for a generated or
  given program* cheaply and repeatably, and (b) round-trip
  generate→lower→execute→check at high throughput. Spawning Node per program is
  the obvious bottleneck and correctness risk.

---

## 6. Is ArkAnalyzer suitable for us? Should we write our own parser?

### 6.1 What ArkAnalyzer gives us (pros)

* It already works end-to-end; `usvm-ts` consumes its output directly.
* It does the hard parts: TS parsing, AST → 3-address lowering, CFG
  construction, import/export & namespace resolution, decorators, the OHOS SDK,
  and type inference.
* The Lipen fork is kept consistent with jacodb's DTOs.

### 6.2 Pain points (cons)

* **Heavyweight external dependency:** Node + npm + cloning a *specific fork and
  branch*. Setup is fragile (CI retries the clone up to 10×).
* **Version coupling:** the AA branch must match the jacodb DTO schema; the
  "supported branch" drifts (`neo/2024-10-31` → `2025-02-24` → `2025-09-03`).
* **Per-file subprocess at test time:** each `.ts` load shells out to Node.
  Slow, IO-heavy, timeout-prone; bad fit for PBT's many-runs loop.
* **Tuned for ArkTS/OHOS, not plain JS:** `.js` support is unclear/untested
  here; behavior follows ArkTS semantics.
* **Known gaps/bugs:** e.g. unary plus is unsupported
  (`samples/operators/UnaryPlus.kt` is `@Disabled`, AA issue #737).
* **Opaque & uncontrollable:** lowering decisions (temp naming, CFG shape,
  constants-as-strings) are dictated externally. For PBT — where we want to
  *generate* programs and/or IR and tightly control the surface we exercise —
  this is a liability.

### 6.3 Conclusion

**Keep ArkAnalyzer as the reference/oracle, but build our own front-end** that
produces the same EtsIR contract. Reasons: throughput, hermetic builds, control
over the supported language subset, and the ability to generate/round-trip IR
for PBT without a Node round-trip per case. Because we target the *same* IR
(`EtsFileDto` JSON, or the `EtsFile` model directly), **all of `usvm-ts`
continues to work unchanged**, and we can differentially test our front-end
against ArkAnalyzer on overlapping inputs.

---

## 7. Options for our own `.ts`/`.js` → EtsIR front-end

Two output targets:

* **(T-JSON)** emit `EtsFileDto`-shaped JSON → reuse `jacodb-ets`
  `loadFromJson` + `toEtsFile` unchanged. Lowest integration risk; lets
  `toEtsFile` do the final 3AC normalization for us.
* **(T-MODEL)** build the `EtsFile`/`EtsMethod` model directly in JVM and skip
  JSON entirely. Best for throughput / no-Node, but we must reproduce what
  `EtsMethodBuilder` does (3AC, CFG, op→class mapping).

Parser/lowering technology choices:

| Approach | Parsing | Lowering (AST→3AC+CFG) | Node needed? | Notes |
|---|---|---|---|---|
| **A. Our serializer (Node + `typescript` API)** | TS compiler API | we write it in TS | yes, but *our* code | Drop-in replacement for `serializeArkIR`; reuses jacodb deserializer. Still per-file Node. |
| **B. GraalJS + TS compiler on JVM** | run `typescript` in-process via GraalJS | in TS or Kotlin | no external Node | Hermetic, in-JVM; heavier runtime, GraalJS setup. |
| **C. JVM-native TS/JS parser** (swc/oxc/tree-sitter binding, or ANTLR grammar) | native/JVM | Kotlin | no | We own the whole pipeline; most work but best long-term control. |
| **D. Hand-written parser for a subset** | Kotlin | Kotlin | no | Smallest scope; ideal if PBT only needs a language subset initially. |

These are not mutually exclusive — e.g. start with **D** (a controlled subset
that maps cleanly onto the 7 statement kinds and the value/expr set above),
build the IR model directly (**T-MODEL**), and differentially validate against
ArkAnalyzer (the **A**/existing path) on the overlap.

### Open questions to settle before implementation (see §8)

1. Output target: **T-JSON** (reuse jacodb lift) vs **T-MODEL** (direct model)?
2. Parser tech: **A/B/C/D** above?
3. Language scope: which `.ts`/`.js` subset first (the union/intersection of
   what `usvm-ts` already supports and what PBT will generate)?
4. Type information: do we need types up front, or lean on `UnknownType` +
   USVM's own inference (`usvm-dataflow-ts`)?

---

## 8. Appendix: quick reference

* IR producer (external): `arkanalyzer/out/src/save/serializeArkIR.js`
* IR consumer (lib): `jacodb-ets`
  * deserialize: `org.jacodb.ets.dto.EtsFileDto.loadFromJson`
  * lift: `org.jacodb.ets.dto.toEtsFile` / `EtsMethodBuilder` (`Convert.kt`)
  * auto-convert util: `org.jacodb.ets.utils.LoadEtsFile`
    (`loadEtsFileAutoConvert`, `loadEtsProjectAutoConvert`, `generateEtsIR`)
  * DTO package: `org.jacodb.ets.dto.{Model,Stmts,Values,Types,Signatures,Cfg,Ops,Convert}`
  * model package: `org.jacodb.ets.model.{Scene,File,Class,Method,Stmt,Value,Expr,Type,...}`
* In-repo wiring:
  * tests: `usvm-ts/.../util/TsMethodTestRunner.kt`, `util/LoadEts.kt`
  * SDK task: `usvm-ts/build.gradle.kts` → `generateSdkIR`
  * CI: `.github/workflows/ci.yml` (ArkAnalyzer fork `Lipen/arkanalyzer`, branch `neo/2025-09-03`)
  * env vars: `ARKANALYZER_DIR`, `SERIALIZE_SCRIPT_PATH`, `NODE_EXECUTABLE`
* PBT-relevant output: `org.usvm.api.TsTest` + `TsTestValue`
