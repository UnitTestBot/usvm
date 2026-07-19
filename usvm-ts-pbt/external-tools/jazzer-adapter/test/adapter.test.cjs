"use strict";

const assert = require("node:assert/strict");
const { mkdtemp, mkdir, readFile, readdir, stat, writeFile } = require("node:fs/promises");
const { tmpdir } = require("node:os");
const { join, resolve } = require("node:path");
const { test } = require("node:test");
const { exportRawCorpus } = require("../src/corpus.cjs");
const { parseEtcV2 } = require("../src/etc-v2.cjs");
const { invokeForFuzz } = require("../src/fuzz-target.cjs");
const { parseArgs: parseRunArgs, runCampaign } = require("../src/run.cjs");
const { importEtcSeeds } = require("../src/seed-corpus.cjs");
const { normalizeArguments } = require("../../shared-fixtures/module-export-harness.cjs");
const {
  decodeMethodInput,
  decodeMethodInvocation,
  encodeMethodInput,
  encodeMethodInvocation,
} = require("../src/type-decoder.cjs");

const fixtureRoot = resolve(__dirname, "../fixtures/v2");
const fixtureManifestPath = join(fixtureRoot, "target-manifest.json");
const fixtureConfigPath = join(fixtureRoot, "run-config.json");
const fixtureEtcPath = join(fixtureRoot, "initial-corpus.etc.jsonl");
const fixtureHarnessPath = join(fixtureRoot, "harness.cjs");

const method = {
  methodId: "f.ts::%dflt::f/4",
  parameterTypes: ["number", "string", "boolean[]", "number | undefined"],
  parameters: [
    { index: 0, name: "n", type: "number", optional: false, rest: false },
    { index: 1, name: "s", type: "string", optional: false, rest: false },
    { index: 2, name: "bs", type: "boolean[]", optional: false, rest: false },
    { index: 3, name: "maybe", type: "number | undefined", optional: false, rest: false },
  ],
};

test("Jazzer.js dependency stays exactly pinned with audited license and integrity", async () => {
  const lock = JSON.parse(await readFile(resolve(__dirname, "../package-lock.json"), "utf8"));
  const jazzer = lock.packages["node_modules/@jazzer.js/core"];
  assert.equal(lock.packages[""].dependencies["@jazzer.js/core"], "4.0.0");
  assert.equal(jazzer.version, "4.0.0");
  assert.equal(jazzer.license, "Apache-2.0");
  assert.match(jazzer.integrity, /^sha512-/);
});

test("legacy ETC seeds still encode to the same typed arguments", () => {
  const external = [
    { kind: "number", value: "49382" },
    { kind: "string", value: "hello" },
    { kind: "array", elements: [{ kind: "boolean", value: "true" }, { kind: "boolean", value: "false" }] },
    { kind: "undefined" },
  ];
  const raw = encodeMethodInput(external, method);
  assert.deepEqual(decodeMethodInput(raw, method), [49382, "hello", [true, false], undefined]);
});

test("legacy decoding is deterministic for truncated and arbitrary bytes", () => {
  for (const raw of [Buffer.alloc(0), Buffer.from([0]), Buffer.from([255, 1, 2, 3, 4, 5])]) {
    assert.deepEqual(decodeMethodInput(raw, method), decodeMethodInput(raw, method));
  }
});

test("coverage mode can continue after target exceptions", () => {
  const oneNumber = { parameterTypes: ["number"] };
  const invoke = () => { throw new Error("expected target failure"); };
  assert.doesNotThrow(() => invokeForFuzz({ method: oneNumber, invoke, ignoreExceptions: true }, Buffer.alloc(0)));
  assert.throws(
    () => invokeForFuzz({ method: oneNumber, invoke, ignoreExceptions: false }, Buffer.alloc(0)),
    /expected target failure/,
  );
});

test("bounded harness maps execution and exported ETC arguments identically", () => {
  const beforeMin = process.env.USVM_NUMBER_MIN;
  const beforeMax = process.env.USVM_NUMBER_MAX;
  process.env.USVM_NUMBER_MIN = "-100";
  process.env.USVM_NUMBER_MAX = "100";
  try {
    assert.deepEqual(normalizeArguments([Infinity, -123.9, 12.8, "x"]), [0, -100, 12, "x"]);
  } finally {
    if (beforeMin === undefined) delete process.env.USVM_NUMBER_MIN;
    else process.env.USVM_NUMBER_MIN = beforeMin;
    if (beforeMax === undefined) delete process.env.USVM_NUMBER_MAX;
    else process.env.USVM_NUMBER_MAX = beforeMax;
  }
});

test("rest parameters do not consume a spurious element before their length", () => {
  const restMethod = {
    parameterTypes: ["number"],
    parameters: [{ index: 0, name: "xs", type: "number", optional: false, rest: true }],
  };
  const external = [{ kind: "array", elements: [
    { kind: "number", value: "7" },
    { kind: "number", value: "8" },
  ] }];
  const raw = encodeMethodInput(external, restMethod);
  assert.deepEqual(decodeMethodInput(raw, restMethod), [[7, 8]]);
});

test("non-matching literal and primitive seeds are rejected", () => {
  const literalMethod = {
    parameterTypes: ["\"yes\"", "true", "42", "null"],
    parameters: [
      { type: "\"yes\"", optional: false, rest: false },
      { type: "true", optional: false, rest: false },
      { type: "42", optional: false, rest: false },
      { type: "null", optional: false, rest: false },
    ],
  };
  assert.throws(() => encodeMethodInput([
    { kind: "string", value: "no" },
    { kind: "boolean", value: "true" },
    { kind: "number", value: "42" },
    { kind: "null" },
  ], literalMethod), /expected literal/);
});

test("unified CLI accepts only the v2 input/output surface", () => {
  const parsed = parseRunArgs([
    "--run-config", "config.json",
    "--manifest", "manifest.json",
    "--method", "method-id",
    "--harness", "harness.cjs",
    "--coverage-corpus", "coverage",
    "--crash-corpus", "crashes",
    "--initial-etc", "pbt.etc.jsonl",
    "--raw-run", "raw-run",
  ]);
  assert.deepEqual(parsed.initialEtc, ["pbt.etc.jsonl"]);
  assert.equal(parsed.rawRun, "raw-run");
  assert.throws(() => parseRunArgs(["--seconds", "1"]), /unknown option '--seconds'/);
});

test("fixed legacy byte corpus has a frozen ETC-v2 meaning", async () => {
  const golden = JSON.parse(await readFile(join(fixtureRoot, "fixed-byte-golden.json"), "utf8"));
  const root = await temporaryDirectory();
  const coverage = join(root, "coverage");
  await mkdir(coverage);
  await writeFile(join(coverage, "fixed"), Buffer.from(golden.rawHex, "hex"));
  const out = join(root, "corpus.etc.jsonl");
  const summary = await exportRawCorpus({
    method: golden.method,
    methodId: golden.method.methodId,
    directories: [{ kind: "coverage", path: coverage }],
    out,
    seed: "17",
  });
  const corpus = parseEtcV2(await readFile(out, "utf8"), out);
  assert.equal(summary.generatedCases, 1);
  assert.deepEqual({ receiver: corpus.cases[0].receiver, arguments: corpus.cases[0].arguments }, golden.expected);
});

test("ETC-v2 envelope round-trips receiver plans, aliases, sparse arrays, collections, and special values", async () => {
  const root = await temporaryDirectory();
  const corpusDirectory = join(root, "coverage");
  const out = join(root, "roundtrip.etc.jsonl");
  const manifest = JSON.parse(await readFile(fixtureManifestPath, "utf8"));
  const source = parseEtcV2(await readFile(fixtureEtcPath, "utf8"), fixtureEtcPath);
  const harness = require(fixtureHarnessPath);
  const imported = await importEtcSeeds({
    manifest,
    methodId: source.cases[0].methodId,
    inputs: [fixtureEtcPath],
    out: corpusDirectory,
    harness,
  });
  assert.deepEqual(
    { imported: imported.importedCases, exported: imported.exportedCases, rejected: imported.rejectedCases },
    { imported: 1, exported: 1, rejected: 0 },
  );
  const [rawFile] = await readdir(corpusDirectory);
  const raw = await readFile(join(corpusDirectory, rawFile));
  const invocation = decodeMethodInvocation(raw, manifest.methods[0], harness);
  assert.strictEqual(invocation.arguments[0], invocation.receiver);
  assert.equal(0 in invocation.arguments[1], false);
  assert.ok(Number.isNaN(invocation.arguments[1][1]));
  assert.ok(Object.is(invocation.arguments[1][2], -0));
  assert.ok(invocation.arguments[2] instanceof Map);
  assert.ok(invocation.arguments[2].get("key") instanceof Set);
  assert.equal(invocation.arguments[3](1), true);

  await exportRawCorpus({
    method: manifest.methods[0],
    methodId: source.cases[0].methodId,
    harness,
    directories: [{ kind: "coverage", path: corpusDirectory }],
    out,
    seed: "17",
  });
  const roundTrip = parseEtcV2(await readFile(out, "utf8"), out);
  assert.deepEqual(roundTrip.cases[0].receiver, source.cases[0].receiver);
  assert.deepEqual(roundTrip.cases[0].arguments, source.cases[0].arguments);
});

test("callable and constructor plans get stable explicit rejection without harness materializers", async () => {
  const root = await temporaryDirectory();
  const manifest = JSON.parse(await readFile(fixtureManifestPath, "utf8"));
  const result = await importEtcSeeds({
    manifest,
    methodId: manifest.methods[0].methodId,
    inputs: [fixtureEtcPath],
    out: join(root, "coverage"),
    harness: {},
  });
  assert.equal(result.exportedCases, 0);
  assert.equal(result.rejectedCases, 1);
  assert.match(result.rejections[0].reason, /^unsupported_constructor_plan:/);
  assert.equal(result.conservationOk, true);
});

test("constructor plans with self aliases reject exactly instead of recursing", async () => {
  const manifest = JSON.parse(await readFile(fixtureManifestPath, "utf8"));
  const harness = require(fixtureHarnessPath);
  const receiver = {
    kind: "object",
    aliasId: "self",
    properties: [],
    constructorPlan: {
      callable: { modulePath: "fixtures/model.cjs", exportName: "Model", callableKind: "class" },
      arguments: [{ kind: "alias", aliasReference: "self" }],
    },
  };
  assert.throws(() => encodeMethodInvocation({
    id: "constructor-cycle",
    methodId: manifest.methods[0].methodId,
    generatedAtMs: 0,
    seed: "17",
    receiver,
    arguments: [
      { kind: "alias", aliasReference: "self" },
      { kind: "undefined" },
      { kind: "undefined" },
      { kind: "undefined" },
    ],
  }, manifest.methods[0], harness), /unsupported_constructor_alias_cycle:self/);
});

test("unified CLI core writes exactly the four raw-run v2 artifacts and bounded logs", async () => {
  const fixture = await campaignFixture();
  await writeFile(join(fixture.crashes, "crash-input"), Buffer.from([2, 3, 4]));
  const result = await runCampaign(fixture.options, {
    now: sequenceClock([0, 5, 1005, 1010]),
    runner: () => ({ status: 0, signal: null, stdout: "x".repeat(400), stderr: "diagnostic" }),
    jazzerPath: "/not-executed/jazzer",
  });
  assert.equal(result.exitCode, 0);
  assert.deepEqual((await readdir(fixture.rawRun)).sort(), [
    "corpus.etc.jsonl", "native-coverage.json", "run-meta.json", "stderr.log",
  ]);
  const corpus = parseEtcV2(await readFile(join(fixture.rawRun, "corpus.etc.jsonl"), "utf8"));
  const meta = JSON.parse(await readFile(join(fixture.rawRun, "run-meta.json"), "utf8"));
  const native = JSON.parse(await readFile(join(fixture.rawRun, "native-coverage.json"), "utf8"));
  assert.equal(meta.schemaVersion, 2);
  assert.equal(meta.exitStatus, "success");
  assert.equal(meta.logTruncated, true);
  assert.equal((await stat(join(fixture.rawRun, "stderr.log"))).size, 256);
  assert.equal(meta.accounting.rawCorpusEntriesDiscovered, 2);
  assert.equal(meta.accounting.casesGenerated, corpus.cases.length);
  assert.equal(meta.accounting.casesHandedToReplay, corpus.cases.length);
  assert.equal(meta.accounting.entriesRejected + meta.accounting.casesGenerated, meta.accounting.rawCorpusEntriesDiscovered);
  assert.equal(meta.accounting.actualReplayAttempts, "reported by unified Kotlin replay, not this adapter");
  assert.equal(native.diagnostics.coverageAuthority, "diagnostic-only; concrete EtsIR replay is authoritative");
  assert.equal(native.claims.length, 1);
});

test("timeout preserves a partial validator-shaped corpus and timeout metadata", async () => {
  const fixture = await campaignFixture();
  const timeout = new Error("fixture timeout");
  timeout.code = "ETIMEDOUT";
  const result = await runCampaign(fixture.options, {
    now: sequenceClock([0, 2, 1002, 1008]),
    runner: () => ({ status: null, signal: "SIGTERM", stdout: "partial", stderr: "timeout", error: timeout }),
    jazzerPath: "/not-executed/jazzer",
  });
  assert.equal(result.exitCode, 124);
  const meta = JSON.parse(await readFile(join(fixture.rawRun, "run-meta.json"), "utf8"));
  assert.equal(meta.exitStatus, "timeout_partial_corpus");
  assert.equal(meta.timedOut, true);
  assert.ok(parseEtcV2(await readFile(join(fixture.rawRun, "corpus.etc.jsonl"), "utf8")).cases.length >= 1);
  assert.deepEqual((await readdir(fixture.rawRun)).sort(), [
    "corpus.etc.jsonl", "native-coverage.json", "run-meta.json", "stderr.log",
  ]);
});

test("non-zero tool completion preserves crash corpus and tool-failure metadata", async () => {
  const fixture = await campaignFixture();
  await writeFile(join(fixture.crashes, "crash-input"), Buffer.from([7, 8, 9]));
  const result = await runCampaign(fixture.options, {
    now: sequenceClock([0, 3, 13, 20]),
    runner: () => ({ status: 77, signal: null, stdout: "", stderr: "fixture crash" }),
    jazzerPath: "/not-executed/jazzer",
  });
  assert.equal(result.exitCode, 77);
  const meta = JSON.parse(await readFile(join(fixture.rawRun, "run-meta.json"), "utf8"));
  const corpus = parseEtcV2(await readFile(join(fixture.rawRun, "corpus.etc.jsonl"), "utf8"));
  assert.equal(meta.exitStatus, "tool_failure");
  assert.equal(meta.timedOut, false);
  assert.ok(corpus.cases.some((testCase) => testCase.metadata.corpusKind === "crash"));
});

test("signal completion without an exit status is still a tool failure", async () => {
  const fixture = await campaignFixture();
  const result = await runCampaign(fixture.options, {
    now: sequenceClock([0, 3, 13, 20]),
    runner: () => ({ status: null, signal: "SIGABRT", stdout: "", stderr: "fixture signal" }),
    jazzerPath: "/not-executed/jazzer",
  });
  assert.equal(result.exitCode, 1);
  const meta = JSON.parse(await readFile(join(fixture.rawRun, "run-meta.json"), "utf8"));
  assert.equal(meta.exitStatus, "tool_failure");
  assert.equal(meta.termination.signal, "SIGABRT");
});

async function campaignFixture() {
  const root = await temporaryDirectory();
  const coverage = join(root, "coverage");
  const crashes = join(root, "crashes");
  const rawRun = join(root, "raw-run");
  await mkdir(coverage);
  await mkdir(crashes);
  return {
    coverage,
    crashes,
    rawRun,
    options: {
      runConfig: fixtureConfigPath,
      manifest: fixtureManifestPath,
      methodId: "fixtures/receiver.ts::Model::exercise/4",
      harness: fixtureHarnessPath,
      coverageCorpus: coverage,
      crashCorpus: crashes,
      rawRun,
      workdir: root,
      includes: [],
      initialEtc: [fixtureEtcPath],
    },
  };
}

function sequenceClock(values) {
  return () => {
    assert.ok(values.length > 0, "fake clock exhausted");
    return values.shift();
  };
}

async function temporaryDirectory() {
  return mkdtemp(join(tmpdir(), "usvm-jazzer-v2-"));
}
