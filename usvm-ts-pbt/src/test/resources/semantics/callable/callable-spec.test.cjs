"use strict";

const assert = require("node:assert/strict");
const test = require("node:test");

const fixtureSource = require("./CallableSemanticsFixture.ts");
const librarySource = require("./CallableSemanticsLibrary.ts");
const { loadSpec, runCase, validateSpec } = require("./callable-spec-runner.cjs");

const spec = loadSpec();

test("frozen callable spec and exact 11-way real residual partition validate", () => {
  const report = validateSpec(spec);
  assert.deepEqual(report.errors, []);
  assert.equal(report.valid, true);
  assert.equal(report.cases, 20);
  assert.equal(report.materialized, 13);
  assert.equal(report.rejected, 7);
  assert.equal(report.residualBlockers, 11);
  assert.equal(report.dispatchKinds, 4);
});

test("source fixtures expose direct, imported, field, receiver, call, recursion and arity behavior", () => {
  assert.equal(fixtureSource.directAdd(2, 3), 5);
  assert.equal(fixtureSource.topLevelArrow(21), 42);
  assert.equal(librarySource.importedArrow(6, 7), 42);
  assert.equal(librarySource.importedOffset(2), 42);
  assert.equal(fixtureSource.invokeField({ base: 40, operation: fixtureSource.readBase }, "operation", [2]), 42);
  assert.equal(fixtureSource.invokeWithCall(fixtureSource.readBase, { base: 38 }, [4]), 42);
  assert.equal(fixtureSource.recursiveFactorial(6), 720);
  assert.equal(fixtureSource.arityPair(4), "1:4:undefined");
  assert.equal(fixtureSource.arityPair(4, 5, 99), "3:4:5");
});

test("unknown callable references fail closed instead of becoming undefined", () => {
  const unknown = structuredClone(spec.cases.find((candidate) => candidate.id === "direct-function"));
  unknown.etcCase.arguments[0].callableReference.exportName = "notExported";
  assert.throws(
    () => runCase(unknown),
    /outside the exact registry/,
  );
});

for (const testCase of spec.cases) {
  test(`Node callable differential: ${testCase.id}`, () => {
    assert.deepEqual(runCase(testCase), testCase.expected);
  });
}
