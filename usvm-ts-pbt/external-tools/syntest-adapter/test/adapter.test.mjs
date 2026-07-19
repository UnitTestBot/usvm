import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { mkdtemp, readFile, readdir, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { test } from "node:test";
import { runAdapter } from "../src/adapter.mjs";
import { classifyMethods, isSupportedPrimitiveType } from "../src/classify.mjs";
import {
  commonValidatorBridge,
  loadAdapterInputs,
  readEtcCorpus,
  validateRawRunLocal,
} from "../src/contract-bridge.mjs";
import { buildHarnessPlans } from "../src/harness.mjs";
import { parseArgs } from "../src/cli.mjs";
import { ProcessProtocolRunner } from "../src/runner.mjs";
import { assertEncodedValues, encodeConcreteValues, ValueEncodingError } from "../src/value-codec.mjs";

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = resolve(HERE, "..");
const FIXTURES = join(ROOT, "fixtures");
const INPUTS = Object.freeze({
  targetManifest: join(FIXTURES, "primitive-target-manifest.v2.json"),
  sourceTargets: join(FIXTURES, "primitive-source-targets.v2.jsonl"),
  methodIds: join(FIXTURES, "primitive-method-ids.txt"),
  runConfig: join(FIXTURES, "run-config.v2.json"),
});

test("all 42 frozen primitive methods are machine-classified with exact fixture mapping", async () => {
  const inputs = await loadAdapterInputs(INPUTS);
  const actual = classifyMethods(inputs);
  const golden = JSON.parse(await readFile(join(FIXTURES, "primitive-classification.expected.json"), "utf8"));
  assert.equal(actual.selectedMethods, 42);
  assert.equal(actual.selectedEdges, 236);
  assert.equal(actual.eligibleMethods, 42);
  assert.equal(actual.eligibleEdges, 236);
  assert.deepEqual(actual.classifications, golden.methods);
  assert.ok(actual.classifications.every((entry) => entry.status === "eligible" && entry.reasons.length === 0));
});

test("classifier emits only closed, exact ineligibility reasons", () => {
  const method = {
    methodId: "bad.ts::C::m/1",
    signature: "C.m(xs: number[]): void",
    projectName: "fixture",
    fileName: "bad.ts",
    className: "C",
    methodName: "m",
    arity: 1,
    parameterTypes: ["number[]"],
    parameters: [{ index: 0, name: "xs", type: "number[]", optional: false, rest: true }],
    entryKind: "instance",
    branches: [{ branchId: "bad.ts::C::m/1#s1:0->2", ifStmtIndex: 1, successorOrdinal: 0, successorStmtIndex: 2 }],
  };
  const mapped = {
    ...method,
    methodId: "mapped.ts::C::m/1",
    branches: [
      { branchId: "mapped.ts::C::m/1#s1:0->2", ifStmtIndex: 1, successorOrdinal: 0, successorStmtIndex: 2 },
      { branchId: "mapped.ts::C::m/1#s1:1->3", ifStmtIndex: 1, successorOrdinal: 1, successorStmtIndex: 3 },
    ],
  };
  const range = { fileName: "mapped.ts", startOffset: 0, endOffset: 1, startLine: 0, startColumn: 0, endLine: 0, endColumn: 1 };
  const sourceTargets = mapped.branches.map((branch, index) => ({
    schemaVersion: 2,
    methodId: mapped.methodId,
    branchId: branch.branchId,
    stmtIndex: 1,
    successorStmtIndex: branch.successorStmtIndex,
    successorOrdinal: branch.successorOrdinal,
    tsSourceRange: range,
    sourceOrigin: { modulePath: "mapped.ts", callableName: index === 0 ? "left" : "right", callableKind: "free" },
    mappingStatus: index === 0 ? "exact" : "ambiguous",
  }));
  const result = classifyMethods({
    manifest: { methods: [method, mapped] },
    sourceTargets,
    methodIds: ["absent", method.methodId, mapped.methodId],
  });
  assert.deepEqual(result.classifications[0].reasons, ["method-not-in-manifest"]);
  assert.deepEqual(result.classifications[1].reasons, [
    "source-mapping-missing",
    "unsupported-entry-kind",
    "unsupported-parameter-type",
    "unsupported-rest-parameter",
  ]);
  assert.deepEqual(result.classifications[2].reasons, [
    "callable-origin-mismatch",
    "source-mapping-not-exact",
    "source-origin-ambiguous",
    "unsupported-entry-kind",
    "unsupported-parameter-type",
    "unsupported-rest-parameter",
  ]);
  const closed = new Set(result.reasonVocabulary);
  assert.ok(result.classifications.flatMap((entry) => entry.reasons).every((reason) => closed.has(reason)));
  assert.equal(isSupportedPrimitiveType("number | undefined"), true);
  assert.equal(isSupportedPrimitiveType("number[]"), false);
  assert.equal(isSupportedPrimitiveType("(x: number) => boolean"), false);
});

test("manifest-driven harnesses export every exact branch objective", async () => {
  const inputs = await loadAdapterInputs(INPUTS);
  const classification = classifyMethods(inputs);
  const harnesses = buildHarnessPlans({ ...inputs, classification });
  assert.equal(harnesses.length, 42);
  assert.equal(harnesses.reduce((sum, harness) => sum + harness.objectiveRequests.length, 0), 236);
  const first = harnesses[0];
  assert.equal(first.methodId, "absolute_value.ts::%dflt::%AM0$%dflt/1");
  assert.equal(first.callableName, "absoluteValue");
  assert.equal(first.objectiveRequests.length, 2);
  assert.ok(first.objectiveRequests.every((objective) => objective.etsIrBranchId.startsWith(`${first.methodId}#`)));
  assert.match(first.source, /import \* as __subject from "absolute_value\.ts"/);
  assert.match(first.source, /Reflect\.apply/);
});

test("ETC v2 concrete extraction preserves special values, holes, maps, sets, aliases, and cycles", () => {
  const shared = { answer: 42 };
  shared.self = shared;
  const sparse = [];
  sparse.length = 2;
  sparse[1] = -0;
  const encoded = encodeConcreteValues({
    arguments: [NaN, Infinity, -Infinity, -0, sparse, new Map([["k", shared]]), new Set([shared]), shared],
  });
  assert.deepEqual(encoded.arguments.slice(0, 4).map((entry) => entry.value), ["NaN", "Infinity", "-Infinity", "-0"]);
  assert.equal(encoded.arguments[4].elements[0].kind, "hole");
  assert.equal(encoded.arguments[4].elements[1].value, "-0");
  assert.equal(encoded.arguments[5].kind, "map");
  assert.equal(encoded.arguments[6].kind, "set");
  assert.equal(encoded.arguments[7].kind, "alias");
  assert.doesNotThrow(() => assertEncodedValues(encoded));
  assert.throws(() => encodeConcreteValues({ arguments: [() => true] }), ValueEncodingError);
  assert.throws(() => assertEncodedValues({
    arguments: [{ kind: "unrepresentable", reason: "closure", unrepresentableKind: "function" }],
  }), /unrepresentable value rejected/);
});

test("successful runner abstraction attempts all eligible methods and hands every exported case to replay", async () => {
  const root = await mkdtemp(join(tmpdir(), "usvm-syntest-success-"));
  const runner = new FakeRunner({ statusFor: () => "success" });
  let commonValidatorCalls = 0;
  const commonValidator = {
    validateRawRun: async (outDir) => {
      commonValidatorCalls += 1;
      return { status: "pass", outDir };
    },
  };
  const summary = await runAdapter({ ...INPUTS, outDir: join(root, "raw") }, { runner, commonValidator });
  assert.equal(summary.exitStatus, "success");
  assert.equal(runner.calls.length, 42);
  assert.equal(summary.funnel.selectedMethods, 42);
  assert.equal(summary.funnel.selectedEdges, 236);
  assert.equal(summary.funnel.eligibleMethods, 42);
  assert.equal(summary.funnel.harnessedMethods, 42);
  assert.equal(summary.funnel.attemptedMethods, 42);
  assert.equal(summary.funnel.rawCases, 42);
  assert.equal(summary.funnel.exportedCases, 42);
  assert.equal(summary.funnel.handedToUnifiedReplayCases, 42);
  assert.equal(summary.funnel.replayedCases, null);
  assert.equal(summary.funnel.confirmedEdges, null);
  assert.equal(commonValidatorCalls, 1);
  assert.deepEqual((await readdir(join(root, "raw"))).sort(), [
    "corpus.etc.jsonl",
    "native-coverage.json",
    "run-meta.json",
    "stderr.log",
  ]);
  assert.deepEqual(await validateRawRunLocal(join(root, "raw")), { valid: true, cases: 42, claims: 42 });
  const corpus = await readEtcCorpus(join(root, "raw", "corpus.etc.jsonl"));
  assert.equal(corpus.cases.length, 42);
  const native = JSON.parse(await readFile(join(root, "raw", "native-coverage.json"), "utf8"));
  assert.equal(native.diagnostics.coverageTruth, false);
  assert.equal(native.diagnostics.mayIncreasePaperNumerator, false);
  assert.equal(native.diagnostics.denominator.broadPrimitiveEdges, 236);
  assert.equal(native.diagnostics.eligibleCoverageMustNotBeReportedAsBroadCoverage, true);
  const meta = JSON.parse(await readFile(join(root, "raw", "run-meta.json"), "utf8"));
  assert.equal(meta.replayHandoff.status, "pending-unified-kotlin-replay");
  assert.equal(meta.classification.methods.length, 42);
});

test("timeout and nonzero failure both preserve valid partial corpora", async (t) => {
  for (const status of ["timeout", "failure"]) {
    await t.test(status, async () => {
      const root = await mkdtemp(join(tmpdir(), `usvm-syntest-${status}-`));
      const runner = new FakeRunner({ statusFor: (ordinal) => ordinal === 0 ? status : "success" });
      const summary = await runAdapter({ ...INPUTS, outDir: join(root, "raw") }, { runner });
      assert.equal(summary.exitStatus, status === "timeout" ? "timeout_partial_corpus" : "tool_failure");
      assert.equal(summary.funnel.attemptedMethods, 42);
      assert.equal(summary.funnel.rawCases, 42);
      assert.equal(summary.funnel.handedToUnifiedReplayCases, 42);
      const corpus = await readEtcCorpus(join(root, "raw", "corpus.etc.jsonl"));
      assert.equal(corpus.cases.length, 42);
      const meta = JSON.parse(await readFile(join(root, "raw", "run-meta.json"), "utf8"));
      assert.equal(meta.timedOut, status === "timeout");
    });
  }
});

test("initial corpus is passed only after the runner declares upstream support", async () => {
  const root = await mkdtemp(join(tmpdir(), "usvm-syntest-initial-"));
  const baseConfig = JSON.parse(await readFile(INPUTS.runConfig, "utf8"));
  baseConfig.flags.syntest.initialCorpusPath = "initial.etc.jsonl";
  const initial = [
    JSON.stringify({ schemaVersion: 2, producer: "fixture@1.0.0" }),
    JSON.stringify({
      id: "initial-1",
      methodId: "absolute_value.ts::%dflt::%AM0$%dflt/1",
      generatedAtMs: 0,
      path: "initial:0",
      arguments: [{ kind: "number", value: "-0" }],
    }),
    "",
  ].join("\n");
  await writeFile(join(root, "initial.etc.jsonl"), initial, "utf8");
  await writeFile(join(root, "run-config.json"), `${JSON.stringify(baseConfig)}\n`, "utf8");

  const supported = new FakeRunner({ statusFor: () => "success", initialCorpus: true });
  await runAdapter({ ...INPUTS, runConfig: join(root, "run-config.json"), outDir: join(root, "supported") }, { runner: supported });
  assert.equal(supported.calls[0].initialCorpus.length, 1);
  const supportedMeta = JSON.parse(await readFile(join(root, "supported", "run-meta.json"), "utf8"));
  assert.equal(supportedMeta.initialCorpus.used, true);

  baseConfig.flags.syntest.initialCorpusPath = "missing-file-is-not-read.etc.jsonl";
  await writeFile(join(root, "run-config-no-support.json"), `${JSON.stringify(baseConfig)}\n`, "utf8");
  const unsupported = new FakeRunner({ statusFor: () => "success", initialCorpus: false });
  await runAdapter({ ...INPUTS, runConfig: join(root, "run-config-no-support.json"), outDir: join(root, "unsupported") }, { runner: unsupported });
  assert.equal(unsupported.calls[0].initialCorpus, undefined);
  const unsupportedMeta = JSON.parse(await readFile(join(root, "unsupported", "run-meta.json"), "utf8"));
  assert.equal(unsupportedMeta.initialCorpus.used, false);
  assert.equal(unsupportedMeta.initialCorpus.reason, "upstream-initial-corpus-capability-not-declared");
});

test("process protocol reads checkpointed tests after timeout or nonzero exit", async (t) => {
  const runConfig = JSON.parse(await readFile(INPUTS.runConfig, "utf8"));
  const harness = {
    methodId: "fixture.ts::%dflt::f/0",
    objectiveRequests: [{ expectedNativeObjectiveKey: "fixture-objective" }],
  };
  for (const mode of ["failure", "timeout"]) {
    await t.test(mode, async () => {
      const runner = new ProcessProtocolRunner({
        command: [process.execPath, join(ROOT, "test-support/fake-syntest-wrapper.mjs"), "--mode", mode],
        perMethodBudgetMs: mode === "timeout" ? 75 : 1000,
        initialCorpus: false,
      });
      const result = await runner.runMethod({ harness, runConfig, methodCount: 1 });
      assert.equal(result.status, mode);
      assert.equal(result.cases.length, 1);
      assert.equal(result.objectives.length, 1);
      assert.equal(result.diagnostics.partialCheckpointRead, true);
    });
  }
});

test("adapter-local bridge invokes the shared validator CLI shape", async () => {
  const bridge = commonValidatorBridge([process.execPath, join(ROOT, "test-support/fake-validator.mjs")]);
  const result = await bridge.validateRawRun("/tmp/raw-run-fixture");
  assert.equal(result.status, "pass");
  assert.equal(result.invoked, true);
});

test("CLI accepts exactly the five unified artifact paths", () => {
  const args = [
    "--target-manifest", "manifest.json",
    "--source-targets", "source.jsonl",
    "--method-ids", "ids.txt",
    "--run-config", "config.json",
    "--out-dir", "out",
  ];
  assert.deepEqual(parseArgs(args), {
    targetManifest: "manifest.json",
    sourceTargets: "source.jsonl",
    methodIds: "ids.txt",
    runConfig: "config.json",
    outDir: "out",
  });
  assert.throws(() => parseArgs([...args, "--corpus", "seed.jsonl"]), /unknown option/);
  assert.throws(() => parseArgs(args.slice(0, -2)), /missing required options/);
});

test("frozen upstream Apache-2.0 LICENSE and NOTICE retain audited git blobs", async () => {
  const license = await readFile(join(ROOT, "upstream/LICENSE"));
  const notice = await readFile(join(ROOT, "upstream/NOTICE"));
  const revision = JSON.parse(await readFile(join(ROOT, "upstream/REVISION.json"), "utf8"));
  assert.equal(gitBlob(license), "25d5b94be87665d6b25e88314c7e909f161b6537");
  assert.equal(gitBlob(notice), "a9d61efb8ba51da916e1acf75d48ab4eea2b1fc6");
  assert.equal(revision.licenseGitBlob, gitBlob(license));
  assert.equal(revision.noticeGitBlob, gitBlob(notice));
  assert.equal(revision.spdx, "Apache-2.0");
  assert.equal(revision.vendoredUpstreamCode, false);
});

test("fixture provenance hashes every generated input without claiming a run", async () => {
  const provenance = JSON.parse(await readFile(join(FIXTURES, "provenance.json"), "utf8"));
  for (const [name, expected] of Object.entries(provenance.generatedSha256)) {
    const content = await readFile(join(FIXTURES, name));
    assert.equal(createHash("sha256").update(content).digest("hex"), expected, name);
  }
  assert.match(provenance.derivation, /no coverage observation/i);
});

class FakeRunner {
  constructor({ statusFor, initialCorpus = false }) {
    this.statusFor = statusFor;
    this.capabilities = Object.freeze({ available: true, initialCorpus, protocol: "fake-test-runner" });
    this.calls = [];
  }

  async runMethod(request) {
    this.calls.push(request);
    const argumentsForMethod = request.harness.parameterTypes.map((type, index) =>
      type.includes("string") ? `${request.methodOrdinal}-${index}` : request.methodOrdinal + index);
    return {
      status: this.statusFor(request.methodOrdinal),
      started: true,
      cases: [{
        id: `raw-${request.methodOrdinal}`,
        generatedAtMs: request.methodOrdinal + 1,
        arguments: argumentsForMethod,
      }],
      objectives: [{
        nativeTargetId: request.harness.objectiveRequests[0].expectedNativeObjectiveKey,
        covered: request.methodOrdinal % 2 === 0,
        discoveredAtMs: request.methodOrdinal + 1,
      }],
      stderr: "",
    };
  }
}

function gitBlob(content) {
  return createHash("sha1").update(`blob ${content.length}\0`).update(content).digest("hex");
}
