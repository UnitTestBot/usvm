import assert from "node:assert/strict";
import { mkdtemp, readFile, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import test from "node:test";
import { exportExpoSeCorpus } from "../src/export.mjs";
import { generateTarget } from "../src/target.mjs";
import { decodeJsonSeed } from "../src/value-codec.mjs";

const method = {
  methodId: "sample.ts::magic/1",
  parameterTypes: ["number"],
  parameters: [{ index: 0, name: "x", type: "number", optional: false, rest: false }],
};

test("generated target has deterministic named symbols and no source interpolation", () => {
  const target = generateTarget({ harnessPath: "/tmp/a'quoted.cjs", method, initialArguments: [7] });
  assert.match(target, /S\$\.symbol\("usvm_arg_0", 7\)/);
  assert.match(target, /require\("\/tmp\/a'quoted\.cjs"\)/);
});

test("ExpoSE path inputs export to ETC without parsing console output", async () => {
  const root = await mkdtemp(join(tmpdir(), "usvm-expose-adapter-"));
  const rawPath = join(root, "raw.json");
  const outPath = join(root, "etc.json");
  const harnessPath = join(root, "harness.cjs");
  await writeFile(harnessPath, "exports.invoke = ([x]) => x;\n", "utf8");
  await writeFile(rawPath, JSON.stringify({ start: 100, end: 250, done: [
    { id: 0, input: { _bound: 0, usvm_arg_0: 0 }, time: 30, errors: [], alternatives: 2 },
    { id: 1, input: { _bound: 1, usvm_arg_0: 49382 }, time: 40, errors: [], alternatives: 0 },
  ] }), "utf8");
  const summary = await exportExpoSeCorpus({
    rawPath, outPath, method, methodId: method.methodId, harnessPath, producer: "expose@test",
  });
  const corpus = JSON.parse(await readFile(outPath, "utf8"));
  assert.equal(summary.exportedCases, 2);
  assert.deepEqual(corpus.cases.map((testCase) => testCase.arguments[0].value), ["0", "49382"]);
});

test("lossy JSON initial seeds are rejected", () => {
  assert.throws(() => decodeJsonSeed({ kind: "number", value: "NaN" }), /not losslessly JSON-encodable/);
  assert.throws(() => decodeJsonSeed({ kind: "undefined" }), /not JSON-encodable/);
});
