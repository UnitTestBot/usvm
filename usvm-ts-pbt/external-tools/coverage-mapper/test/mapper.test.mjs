import assert from "node:assert/strict";
import test from "node:test";
import { mapExpoSeCoverage, mapIstanbulCoverage, mapV8Coverage } from "../src/mapper.mjs";

const span = (startLine, startColumn, endLine, endColumn, startOffset, endOffset) => ({
  fileName: "sample.ts", startLine, startColumn, endLine, endColumn, startOffset, endOffset, nodeKind: "fixture",
});

function manifest(branches = [
  { branchId: "m#true", ifStmtIndex: 1, successorOrdinal: 0, successorStmtIndex: 2,
    conditionOrigin: span(0, 4, 0, 9, 4, 9), successorOrigin: span(1, 2, 1, 11, 12, 21) },
  { branchId: "m#false", ifStmtIndex: 1, successorOrdinal: 1, successorStmtIndex: 3,
    conditionOrigin: span(0, 4, 0, 9, 4, 9), successorOrigin: span(3, 2, 3, 11, 32, 41) },
]) {
  return { schemaVersion: 1, methods: [{ methodId: "sample.ts::m/1", fileName: "sample.ts", branches }] };
}

test("Istanbul arm locations map one-to-one to zero-based EtsIR origins", () => {
  const coverage = {
    "/project/sample.ts": {
      path: "/project/sample.ts",
      branchMap: { "0": {
        type: "if",
        loc: { start: { line: 1, column: 0 }, end: { line: 4, column: 12 } },
        locations: [
          { start: { line: 2, column: 0 }, end: { line: 2, column: 12 } },
          { start: { line: 4, column: 0 }, end: { line: 4, column: 12 } },
        ],
      } },
      b: { "0": [7, 0] },
    },
  };
  const report = mapIstanbulCoverage(manifest(), coverage);
  assert.equal(report.summary.statuses["one-to-one"], 2);
  assert.equal(report.summary.creditedCovered, 1);
  assert.deepEqual(report.mappings.map((mapping) => mapping.claimedCovered), [true, false]);
});

test("a source arm shared by normalized EtsIR edges is explicit one-to-many", () => {
  const branches = manifest().methods[0].branches;
  const extra = { ...branches[0], branchId: "m#normalized", ifStmtIndex: 4 };
  const coverage = {
    "sample.ts": {
      branchMap: { "0": { type: "if", locations: [
        { start: { line: 2, column: 0 }, end: { line: 2, column: 12 } },
        { start: { line: 4, column: 0 }, end: { line: 4, column: 12 } },
      ] } },
      b: { "0": [1, 1] },
    },
  };
  const report = mapIstanbulCoverage(manifest([...branches, extra]), coverage);
  assert.equal(report.mappings.find((item) => item.branchId === "m#true").status, "one-to-many");
  assert.equal(report.mappings.find((item) => item.branchId === "m#normalized").creditedCovered, false);
});

test("V8 same-source offsets use the narrowest precise block range", () => {
  const coverage = { result: [{
    url: "file:///project/sample.ts",
    functions: [{ functionName: "m", isBlockCoverage: true, ranges: [
      { startOffset: 0, endOffset: 60, count: 5 },
      { startOffset: 32, endOffset: 41, count: 0 },
    ] }],
  }] };
  const report = mapV8Coverage(manifest(), coverage);
  assert.equal(report.summary.statuses["one-to-one"], 2);
  assert.deepEqual(report.mappings.map((mapping) => mapping.claimedCovered), [true, false]);
});

test("ExpoSE Jalangi decision bits map true and false arms by condition", () => {
  const coverage = { finalCoverage: [{
    file: "/project/sample.ts",
    smap: { "4": ["1", "5", "1", "10"] },
    branches: { "4": 0x1 | 0x2 },
  }] };
  const report = mapExpoSeCoverage(manifest(), coverage);
  assert.equal(report.summary.statuses["one-to-one"], 2);
  assert.deepEqual(report.mappings.map((mapping) => mapping.claimedCovered), [true, false]);
});
