import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { mkdtemp, readFile, readdir, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { test } from "node:test";
import fc from "fast-check";
import { runAdapter } from "../src/adapter.mjs";
import { arbitraryForType } from "../src/arbitraries.mjs";
import { loadFrozenContract } from "../src/contract.mjs";
import { parseInitialCorpus } from "../src/corpus.mjs";
import {
  encodeInput,
  encodeValue,
  materializeInput,
  referencedCallable,
} from "../src/value-codec.mjs";

const testDirectory = dirname(fileURLToPath(import.meta.url));
const adapterDirectory = resolve(testDirectory, "..");
const repositoryRoot = resolve(testDirectory, "../../../..");
const fixtures = resolve(testDirectory, "fixtures");
const methodId = "src/fixture.ts::%dflt::classify/2";

test("ETC encoding is lossless for special values, holes, structured aliases, and exact rejections", () => {
  const sparse = [];
  sparse.length = 3;
  sparse[1] = -0;
  const shared = { label: "shared" };
  const encoded = encodeInput(undefined, [
    NaN,
    Infinity,
    -Infinity,
    sparse,
    shared,
    shared,
    new Map([["sparse", sparse]]),
    new Set([shared]),
  ]);
  assert.deepEqual(encoded.arguments.slice(0, 3).map((value) => value.value), ["NaN", "Infinity", "-Infinity"]);
  assert.deepEqual(encoded.arguments[3].elements.map((value) => value.kind), ["hole", "number", "hole"]);
  assert.equal(encoded.arguments[3].elements[1].value, "-0");
  assert.equal(encoded.arguments[5].kind, "alias");
  assert.equal(encoded.arguments[6].entries[0].value.kind, "alias");
  assert.equal(encoded.arguments[7].elements[0].kind, "alias");

  const cyclic = {};
  cyclic.self = cyclic;
  assert.deepEqual(
    [encodeValue(cyclic).properties[0].value.unrepresentableKind, encodeValue(() => 1).unrepresentableKind],
    ["cycle", "function"],
  );
  assert.equal(encodeValue(new Date()).unrepresentableKind, "classInstance");
  assert.equal(encodeValue(Symbol("x")).unrepresentableKind, "symbol");

  const unsupported = new Date();
  const repeatedUnsupported = encodeInput(undefined, [unsupported, unsupported]);
  assert.equal(repeatedUnsupported.arguments[0].kind, "unrepresentable");
  assert.equal(repeatedUnsupported.arguments[1].aliasReference, repeatedUnsupported.arguments[0].aliasId);

  const invalidReceiverPlan = encodeInput([{}], [], {
    receiverPlan: {
      callable: { modulePath: "fixture.ts", exportName: "Box", callableKind: "class" },
      arguments: [],
    },
  });
  assert.equal(invalidReceiverPlan.receiver.kind, "unrepresentable");
  assert.equal(invalidReceiverPlan.receiver.constructorPlan, undefined);
});

test("constructor plans and callable references materialize structured receivers without losing identity", async () => {
  class Box {
    constructor(value) {
      this.value = value;
    }
  }
  const shared = [1, 2];
  const input = encodeInput(
    { label: "box" },
    [shared, shared, referencedCallable({
      modulePath: "fixture.ts",
      exportName: "identity",
      callableKind: "function",
    })],
    {
      receiverPlan: {
        className: "Box",
        callable: { modulePath: "fixture.ts", exportName: "Box", callableKind: "class" },
        arguments: [7],
      },
    },
  );
  const materialized = await materializeInput(input, {
    resolveCallable(reference) {
      return reference.exportName === "Box" ? Box : (value) => value;
    },
  });
  assert.ok(materialized.receiver instanceof Box);
  assert.equal(materialized.receiver.value, 7);
  assert.equal(materialized.receiver.label, "box");
  assert.strictEqual(materialized.arguments[0], materialized.arguments[1]);
  assert.equal(materialized.arguments[2](9), 9);
});

test("manifest arbitraries are deterministic and produce nested object, Map, Set, and callable shapes", () => {
  const type = "{ payload: { value: number }; tags?: string[]; table: Map<string, number>; seen: Set<boolean> }";
  const first = fc.sample(arbitraryForType(type), { seed: 42, numRuns: 20 });
  const second = fc.sample(arbitraryForType(type), { seed: 42, numRuns: 20 });
  assert.deepEqual(first, second);
  for (const value of first) {
    assert.equal(typeof value.payload.value, "number");
    assert.ok(value.table instanceof Map);
    assert.ok(value.seen instanceof Set);
    if (value.tags !== undefined) assert.ok(Array.isArray(value.tags));
  }
  const callable = fc.sample(arbitraryForType("(x: number) => string"), { seed: 7, numRuns: 1 })[0];
  assert.equal(typeof callable, "function");
  assert.equal(encodeValue(callable).unrepresentableKind, "function");
});

test("adapter reads the frozen ETC v2 schema and rejects v1 with the shared converter instruction", async () => {
  const contract = await loadFrozenContract();
  assert.equal(contract.schemaVersion, 2);
  assert.ok(contract.valueKinds.has("alias"));
  assert.throws(
    () => parseInitialCorpus('{"schemaVersion":1,"producer":"legacy@1"}\n', contract),
    /artifact-contract convert-v1-etc/u,
  );
});

test("raw run keeps initial ETC as a lossless mandatory prefix and passes the common Kotlin validator", {
  timeout: 120_000,
}, async () => {
  const outDir = await temporaryDirectory("fast-check-golden-");
  const result = await runAdapter(fixtureOptions(outDir, {
    initialEtc: fixture("initial.etc.jsonl"),
    harness: fixture("golden-harness.mjs"),
  }));
  assert.equal(result.exitCode, 0);
  assert.deepEqual(await readdir(outDir).then((names) => names.sort()), [
    "corpus.etc.jsonl",
    "native-coverage.json",
    "run-meta.json",
    "stderr.log",
  ]);

  const records = await readJsonLines(join(outDir, "corpus.etc.jsonl"));
  assert.deepEqual(records[0], { schemaVersion: 2, producer: "fast-check@4.9.0" });
  const prefix = records.slice(1, 4);
  assert.ok(prefix.every((testCase) => testCase.generatedAtMs === 0));
  assert.ok(prefix.every((testCase) => testCase.metadata.replayPrefix === "true"));
  assert.ok(prefix.every((testCase) => testCase.metadata.mutationSeed === "false"));
  assert.deepEqual(
    prefix[0].arguments[1].properties.slice(0, 3).map((property) => property.value.value),
    ["Infinity", "-Infinity", "-0"],
  );
  assert.equal(prefix[0].arguments[1].properties[3].value.elements[0].kind, "hole");
  assert.equal(prefix[1].receiver.constructorPlan.callable.exportName, "Box");
  assert.equal(prefix[1].arguments[1].kind, "callable");
  assert.equal(prefix[2].arguments[1].unrepresentableKind, "cycle");
  assert.equal(records.length, 1 + 3 + 16);
  assert.ok(records.slice(4).every((testCase) => testCase.generatedAtMs >= 1));

  const nativeCoverage = JSON.parse(await readFile(join(outDir, "native-coverage.json"), "utf8"));
  assert.deepEqual(nativeCoverage.claims, []);
  assert.equal(nativeCoverage.diagnostics.coverageAuthority, "diagnostic-only; EtsIR replay is authoritative");
  assert.equal(nativeCoverage.diagnostics.funnel.initialPrefix, 3);
  assert.equal(nativeCoverage.diagnostics.funnel.initialMaterialized, 2);
  assert.equal(nativeCoverage.diagnostics.funnel.initialRejected, 1);
  assert.equal(nativeCoverage.diagnostics.funnel.generated, 16);
  assert.equal(nativeCoverage.diagnostics.funnel.exported, 19);

  const validation = validateRawRun(outDir);
  assert.equal(validation.status, 0, validation.stderr);
  assert.deepEqual(JSON.parse(validation.stdout), {
    artifact: `raw-run-directory:${outDir.split("/").at(-1)}`,
    valid: true,
    issues: [],
  });
});

test("fixed seed and fast-check path reproduce the same shrunk counterexample", async () => {
  const firstDir = await temporaryDirectory("fast-check-shrink-a-");
  const first = await runAdapter(fixtureOptions(firstDir, { harness: fixture("failure-harness.mjs") }));
  assert.equal(first.exitCode, 0);
  const firstCases = (await readJsonLines(join(firstDir, "corpus.etc.jsonl"))).slice(1);
  const firstCounterexample = firstCases.find((testCase) => testCase.metadata.phase === "counterexample");
  assert.ok(firstCounterexample);
  assert.ok(first.nativeCoverage.diagnostics.funnel.shrinkAttempts > 0);
  assert.equal(
    first.nativeCoverage.diagnostics.funnel.exported,
    first.nativeCoverage.diagnostics.funnel.generated
      + first.nativeCoverage.diagnostics.funnel.shrinkAttempts
      + first.nativeCoverage.diagnostics.funnel.counterexamples,
  );

  const path = firstCounterexample.metadata.fastCheckPath;
  const secondDir = await temporaryDirectory("fast-check-shrink-b-");
  const second = await runAdapter(fixtureOptions(secondDir, {
    harness: fixture("failure-harness.mjs"),
    path,
  }));
  assert.equal(second.exitCode, 0);
  const secondCases = (await readJsonLines(join(secondDir, "corpus.etc.jsonl"))).slice(1);
  const secondCounterexample = secondCases.find((testCase) => testCase.metadata.phase === "counterexample");
  assert.ok(secondCounterexample);
  assert.deepEqual(
    pickCounterexample(secondCounterexample),
    pickCounterexample(firstCounterexample),
  );
});

test("deadline interruption preserves a contract-valid partial prefix and reports over-budget fields", {
  timeout: 120_000,
}, async () => {
  const directory = await temporaryDirectory("fast-check-timeout-input-");
  const configPath = join(directory, "run-config.json");
  const config = JSON.parse(await readFile(fixture("run-config.json"), "utf8"));
  config.runId = "fast-check-timeout";
  config.budgetMs = 1001;
  config.exportReplayGraceMs = 1000;
  config.explorationDeadlineMs = 1;
  config.hardResultDeadlineMs = 1001;
  config.flags.fastCheckRunsPerMethod = 10_000;
  await writeFile(configPath, `${JSON.stringify(config)}\n`, "utf8");
  const outDir = await temporaryDirectory("fast-check-timeout-output-");
  const result = await runAdapter(fixtureOptions(outDir, {
    runConfig: configPath,
    initialEtc: fixture("initial.etc.jsonl"),
    harness: fixture("slow-harness.mjs"),
  }));
  assert.equal(result.exitCode, 124);
  assert.equal(result.meta.exitStatus, "timeout_partial_corpus");
  assert.equal(result.meta.timedOut, true);
  assert.ok(result.meta.overBudgetMs >= 0);
  const records = await readJsonLines(join(outDir, "corpus.etc.jsonl"));
  assert.ok(records.length >= 4);
  assert.ok(records.slice(1, 4).every((testCase) => testCase.metadata.replayPrefix === "true"));
  const validation = validateRawRun(outDir);
  assert.equal(validation.status, 0, validation.stderr);
});

test("CLI stdout is one protocol event and diagnostics stay in bounded raw stderr.log", () => {
  const outDir = spawnSync("mktemp", ["-d", join(tmpdir(), "fast-check-cli-XXXXXX")], { encoding: "utf8" }).stdout.trim();
  const result = spawnSync(process.execPath, [
    resolve(adapterDirectory, "src/cli.mjs"),
    "run",
    "--target-manifest", fixture("target-manifest.json"),
    "--source-targets", fixture("source-targets.jsonl"),
    "--method-ids", fixture("method-ids.txt"),
    "--run-config", fixture("run-config.json"),
    "--out-dir", outDir,
  ], { encoding: "utf8" });
  assert.equal(result.status, 0, result.stderr);
  assert.equal(result.stderr, "");
  const lines = result.stdout.trim().split("\n");
  assert.equal(lines.length, 1);
  assert.equal(JSON.parse(lines[0]).event, "run-complete");
});

function fixture(name) {
  return resolve(fixtures, name);
}

function fixtureOptions(outDir, overrides = {}) {
  return {
    targetManifest: fixture("target-manifest.json"),
    sourceTargets: fixture("source-targets.jsonl"),
    methodIds: fixture("method-ids.txt"),
    initialEtc: null,
    runConfig: fixture("run-config.json"),
    seed: null,
    budgetMs: null,
    exportReplayGraceMs: null,
    outDir,
    harness: null,
    path: null,
    runId: null,
    cacheMode: "cold",
    ...overrides,
  };
}

async function temporaryDirectory(prefix) {
  return mkdtemp(join(tmpdir(), prefix));
}

async function readJsonLines(path) {
  return (await readFile(path, "utf8")).trim().split(/\r?\n/u).map((line) => JSON.parse(line));
}

function validateRawRun(outDir) {
  return spawnSync(
    resolve(repositoryRoot, "gradlew"),
    [
      "-q",
      "-I", resolve(testDirectory, "validate-raw-run.init.gradle"),
      ":usvm-ts-pbt:validateFastCheckRawRun",
      `-PfastCheckRawRun=${outDir}`,
    ],
    { cwd: repositoryRoot, encoding: "utf8", timeout: 110_000 },
  );
}

function pickCounterexample(testCase) {
  return {
    methodId: testCase.methodId,
    seed: testCase.seed,
    path: testCase.path,
    receiver: testCase.receiver,
    arguments: testCase.arguments,
    fastCheckPath: testCase.metadata.fastCheckPath,
  };
}
