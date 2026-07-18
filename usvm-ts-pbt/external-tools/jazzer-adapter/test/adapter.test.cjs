"use strict";

const assert = require("node:assert/strict");
const { mkdtemp, readFile, writeFile } = require("node:fs/promises");
const { tmpdir } = require("node:os");
const { join } = require("node:path");
const { test } = require("node:test");
const { exportRawCorpus } = require("../src/corpus.cjs");
const { invokeForFuzz } = require("../src/fuzz-target.cjs");
const { normalizeArguments } = require("../../shared-fixtures/module-export-harness.cjs");
const { decodeMethodInput, encodeMethodInput } = require("../src/type-decoder.cjs");

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

test("ETC seeds encode to the same typed arguments decoded by the fuzz target", () => {
  const external = [
    { kind: "number", value: "49382" },
    { kind: "string", value: "hello" },
    { kind: "array", elements: [{ kind: "boolean", value: "true" }, { kind: "boolean", value: "false" }] },
    { kind: "undefined" },
  ];
  const raw = encodeMethodInput(external, method);
  const decoded = decodeMethodInput(raw, method);
  assert.deepEqual(decoded, [49382, "hello", [true, false], undefined]);
});

test("decoding is deterministic for truncated and arbitrary bytes", () => {
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

test("raw corpus export uses the exact decoder and ETC tags", async () => {
  const root = await mkdtemp(join(tmpdir(), "usvm-jazzer-"));
  const corpusDirectory = join(root, "corpus");
  const { mkdir } = require("node:fs/promises");
  await mkdir(corpusDirectory);
  const raw = encodeMethodInput([
    { kind: "number", value: "-0" },
    { kind: "string", value: "x" },
    { kind: "array", elements: [] },
    { kind: "number", value: "NaN" },
  ], method);
  await writeFile(join(corpusDirectory, "seed"), raw);
  const out = join(root, "etc.json");
  const summary = await exportRawCorpus({
    method,
    methodId: method.methodId,
    harness: {},
    directories: [corpusDirectory],
    out,
  });
  const corpus = JSON.parse(await readFile(out, "utf8"));
  assert.equal(summary.exportedCases, 1);
  assert.equal(corpus.producer, "jazzer.js@4.0.0");
  assert.equal(corpus.cases[0].arguments[0].value, "-0");
  assert.equal(corpus.cases[0].arguments[3].value, "NaN");
});
