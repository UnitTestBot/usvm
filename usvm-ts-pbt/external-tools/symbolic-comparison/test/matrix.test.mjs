import assert from "node:assert/strict";
import test from "node:test";
import { summarizeReplayMatrix } from "../src/matrix.mjs";

test("matrix aggregates a complete replay-confirmed tool grid", () => {
  const entries = [
    entry("fast", "external", "a", "p1", externalReport("a", 4, 4)),
    entry("fast", "external", "b", "p2", externalReport("b", 2, 1)),
    entry("usvm", "symbolic", "a", "p1", symbolicReport("a", 4, 4)),
    entry("usvm", "symbolic", "b", "p2", symbolicReport("b", 2, 2)),
  ];
  const matrix = summarizeReplayMatrix({ entries });
  assert.equal(matrix.totals.tools, 2);
  assert.equal(matrix.totals.totalBranches, 6);
  assert.equal(matrix.tools.fast.coveredBranches, 5);
  assert.equal(matrix.tools.usvm.coveragePct, 100);
});

test("matrix rejects an incomplete tool grid", () => {
  const entries = [
    entry("fast", "external", "a", "p", externalReport("a", 2, 2)),
    entry("fast", "external", "b", "p", externalReport("b", 2, 2)),
    entry("usvm", "symbolic", "a", "p", symbolicReport("a", 2, 2)),
  ];
  assert.throws(() => summarizeReplayMatrix({ entries }), /missing matrix report/);
});

test("external rows cannot include internally generated executions", () => {
  const report = externalReport("a", 2, 2);
  report.methods[0].pbt.generatedExecutions = 1;
  assert.throws(
    () => summarizeReplayMatrix({ entries: [entry("fast", "external", "a", "p", report)] }),
    /generatedExecutions=0/,
  );
});

test("matrix restores omitted default counters in internal PBT reports", () => {
  const report = { methods: [{
    method: "%dflt::a(x: number): number", totalBranches: 2, coveredBranches: 2,
    pbt: { executions: 3, returned: 3, threw: 0, diverged: 0, unsupported: 0 },
  }] };
  const matrix = summarizeReplayMatrix({ entries: [entry("internal", "internal", "a", "p", report)] });
  assert.equal(matrix.tools.internal.generatedExecutions, 3);
  assert.equal(matrix.tools.internal.externalExecuted, 0);
});

function entry(tool, kind, label, project, report) {
  return { tool, kind, label, project, report };
}

function externalReport(label, totalBranches, coveredBranches) {
  return { methods: [{
    method: `%dflt::${label}(x: number): number`, totalBranches, coveredBranches, totalWallMs: 10,
    pbt: { executions: 2, returned: 2, threw: 0, diverged: 0, unsupported: 0,
      generatedExecutions: 0, externalImported: 2, externalExecuted: 2 },
  }] };
}

function symbolicReport(label, totalBranches, coveredBranches) {
  return { methods: [{
    method: `%dflt::${label}(x: number): number`, totalBranches, coveredBranches, totalWallMs: 10,
    pbt: null, symbolic: { targets: [{ reached: true, replayConfirmed: true }] },
  }] };
}
