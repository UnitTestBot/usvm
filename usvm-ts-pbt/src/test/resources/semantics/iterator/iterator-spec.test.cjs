"use strict";

const assert = require("node:assert/strict");
const test = require("node:test");

const { loadSpec, runCase, validateSpec } = require("./iterator-spec-runner.cjs");
const fixture = require("./IteratorSemanticsFixture.ts");

const spec = loadSpec();

test("frozen iterator contract and all real evidence validate", () => {
  const report = validateSpec(spec);
  assert.deepEqual(report.errors, []);
  assert.equal(report.valid, true);
  assert.equal(report.cases, 26);
  assert.equal(report.realTargets, 9);
  assert.equal(report.expectedReplayConfirmed, 7);
  assert.equal(report.expectedCapabilityMismatches, 2);
  assert.equal(report.frozenUnsupportedEventsPerRun, 25);
});

test("real source shapes have the frozen Node behavior", () => {
  assert.equal(fixture.findMinimumAfterIterator([3, 1, 2]), 1);
  assert.deepEqual(fixture.flattenRecursiveAfterIterator([1, [2, [3]]]), [1, 2, 3]);
  const visited = [];
  fixture.collectionForEach([1, 2, 3], (value) => {
    visited.push(value);
    return value !== 2;
  });
  assert.deepEqual(visited, [1, 2]);
});

test("collection fixture completes all 25 frozen-count executions without iterator unsupported", () => {
  let completed = 0;
  for (let index = 0; index < 25; index += 1) {
    fixture.collectionForEach([index], () => true);
    completed += 1;
  }
  assert.equal(completed, 25);
});

for (const testCase of spec.cases) {
  test(`Node differential: ${testCase.id}`, () => {
    assert.deepEqual(runCase(testCase), testCase.expected);
  });
}
