"use strict";

const assert = require("node:assert/strict");
const test = require("node:test");

const { loadSpec, runCase, validateSpec } = require("./builtin-spec-runner.cjs");
const fixtureSource = require("./BuiltinSemanticsFixture.ts");

const spec = loadSpec();

test("frozen builtin spec and real residual evidence validate", () => {
  const report = validateSpec(spec);
  assert.deepEqual(report.errors, []);
  assert.equal(report.valid, true);
  assert.equal(report.cases, 33);
  assert.equal(report.residualBlockers, 3);
});

test("real residual source patterns have the frozen Node decisions", () => {
  assert.equal(fixtureSource.flattenReduceArrayDecision([]), true);
  assert.equal(fixtureSource.flattenReduceArrayDecision({}), false);
  assert.equal(fixtureSource.factorizeTailDecision(new Map(), 2), true);
});

for (const testCase of spec.cases) {
  test(`Node differential: ${testCase.id}`, () => {
    assert.deepEqual(runCase(testCase), testCase.expected);
  });
}
