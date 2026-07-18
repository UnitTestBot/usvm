import assert from "node:assert/strict";
import { test } from "node:test";
import { readFile, mkdtemp, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { dirname, join, resolve } from "node:path";
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import fc from "fast-check";
import { arbitraryForType } from "../src/arbitraries.mjs";
import { encodeCorpus, makeCase, SCHEMA_VERSION } from "../src/corpus.mjs";
import { encodeInput, encodeValue } from "../src/value-codec.mjs";

test("ETC encoding preserves special numbers and array holes", () => {
  const sparse = [];
  sparse.length = 2;
  sparse[1] = -0;
  const encoded = encodeInput(undefined, [NaN, Infinity, -Infinity, sparse]);
  assert.deepEqual(encoded.arguments.slice(0, 3).map((value) => value.value), ["NaN", "Infinity", "-Infinity"]);
  assert.equal(encoded.arguments[3].elements[0].kind, "hole");
  assert.equal(encoded.arguments[3].elements[1].value, "-0");
});

test("cycles, aliases, and class instances are explicit unrepresentable values", () => {
  const cyclic = {};
  cyclic.self = cyclic;
  assert.equal(encodeValue(cyclic).properties[0].value.kind, "unrepresentable");

  const shared = {};
  const encodedShared = encodeInput(undefined, [shared, shared]);
  assert.equal(encodedShared.arguments[1].kind, "unrepresentable");

  assert.equal(encodeValue(new Date()).kind, "unrepresentable");
});

test("manifest type arbitraries are seeded and shape-correct", () => {
  const tuple = arbitraryForType("[number, string, boolean[]]");
  const first = fc.sample(tuple, { seed: 42, numRuns: 10 });
  const second = fc.sample(tuple, { seed: 42, numRuns: 10 });
  assert.deepEqual(first, second);
  for (const value of first) {
    assert.equal(value.length, 3);
    assert.equal(typeof value[0], "number");
    assert.equal(typeof value[1], "string");
    assert.ok(value[2].every((item) => typeof item === "boolean"));
  }
});

test("number arbitrary mixes operational values with IEEE edges", () => {
  const values = fc.sample(arbitraryForType("number"), { seed: 42, numRuns: 1_000 });
  assert.ok(values.some((value) => Number.isInteger(value) && value >= 2 && value <= 1_000));
  assert.ok(values.some((value) => !Number.isFinite(value) || Number.isNaN(value)));
});

test("JSON and JSONL writers keep the required ETC header", () => {
  const corpus = {
    schemaVersion: SCHEMA_VERSION,
    producer: "fast-check@test",
    cases: [makeCase({ id: "one", methodId: "f.ts::C::f/1", args: [undefined] })],
  };
  assert.equal(JSON.parse(encodeCorpus(corpus)).schemaVersion, 1);
  const lines = encodeCorpus(corpus, true).trim().split("\n");
  assert.equal(JSON.parse(lines[0]).producer, "fast-check@test");
  assert.equal(JSON.parse(lines[1]).arguments[0].kind, "undefined");
});

test("CLI harness exports shrink attempts and the minimal counterexample", async () => {
  const directory = await mkdtemp(join(tmpdir(), "usvm-fast-check-"));
  const manifestPath = join(directory, "targets.json");
  const harnessPath = join(directory, "harness.mjs");
  const corpusPath = join(directory, "corpus.json");
  const methodId = "f.ts::%dflt::f/1";
  await writeFile(manifestPath, JSON.stringify({
    schemaVersion: 1,
    generator: "test",
    methods: [{
      methodId,
      parameterTypes: ["number"],
      parameters: [{ index: 0, name: "x", type: "number", optional: false, rest: false }],
    }],
  }));
  await writeFile(harnessPath, "export function invoke() { return false; }\n");

  const testDirectory = dirname(fileURLToPath(import.meta.url));
  const cli = resolve(testDirectory, "../src/cli.mjs");
  const result = spawnSync(process.execPath, [
    cli,
    "--manifest", manifestPath,
    "--method", methodId,
    "--harness", harnessPath,
    "--seed", "42",
    "--runs", "5",
    "--out", corpusPath,
  ], { encoding: "utf8" });
  assert.equal(result.status, 0, result.stderr);

  const summary = JSON.parse(result.stdout.trim());
  const corpus = JSON.parse(await readFile(corpusPath, "utf8"));
  assert.ok(summary.counterexample);
  assert.ok(corpus.cases.length >= 2);
  assert.ok(corpus.cases.some((testCase) => testCase.metadata.phase === "counterexample"));
  assert.ok(corpus.cases.some((testCase) => testCase.metadata.phase === "execute-or-shrink"));
});
