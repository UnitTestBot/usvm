import assert from "node:assert/strict";
import test from "node:test";
import { summarizeComparison } from "../src/summarize.mjs";

test("non-inferiority is based on replay-confirmed aggregate coverage", () => {
  const externalReplay = { methods: [
    { method: "%dflt::a(x: number): number", totalBranches: 4, coveredBranches: 3 },
  ] };
  const usvm = { methods: [
    { method: "%dflt::a(x: number): number", totalBranches: 4, coveredBranches: 4, totalWallMs: 10,
      symbolic: { targets: [{ reached: true, replayConfirmed: true }] } },
  ] };
  const report = summarizeComparison({ externalReplay, usvm, marginPoints: 2 });
  assert.equal(report.totals.exposeCoveragePct, 75);
  assert.equal(report.totals.usvmCoveragePct, 100);
  assert.equal(report.nonInferiority.passed, true);
});
