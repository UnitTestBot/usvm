#!/usr/bin/env node

import { createHash } from "node:crypto";
import { readFile } from "node:fs/promises";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const BASE = resolve(dirname(fileURLToPath(import.meta.url)), "..");

main().catch((error) => {
  console.error(error instanceof Error ? error.stack : String(error));
  process.exitCode = 1;
});

async function main() {
  const manifest = await readJson(join(BASE, "baseline-manifest.json"));
  const audit = await readJson(join(BASE, "upstream-audit.json"));
  const batched = await readJson(join(BASE, "observations", "batched-reports.json"));
  const legacy = await readJson(join(BASE, "observations", "legacy-per-target-reports.json"));
  const summary = await readJson(join(BASE, "results", "batched-summary.json"));

  assert(manifest.schemaVersion === 1, "unsupported manifest schema");
  await validateArtifacts(manifest.expectedArtifacts);
  validateAudit(audit);
  const selections = await validateDenominators(manifest);
  validateObservations(batched, manifest.acceptance.batched, selections);
  validateLegacyObservations(legacy, manifest.acceptance.historicalPerTargetPrimitive, selections);
  validateSavedSummary(summary, manifest.acceptance);

  console.log(JSON.stringify({
    status: "ok",
    baselineId: manifest.baselineId,
    artifactsChecked: manifest.expectedArtifacts.length,
    auditedTools: audit.tools.length,
    denominators: Object.fromEntries(
      Object.entries(manifest.denominators).map(([id, value]) => [id, { methods: value.methods, edges: value.edges }]),
    ),
    broadCoverage: Object.fromEntries(
      Object.entries(manifest.acceptance.batched.broad).map(([name, row]) => [name, `${row.coveredBranches}/${row.totalBranches}`]),
    ),
    primitiveCoverage: Object.fromEntries(
      Object.entries(manifest.acceptance.batched.primitive).map(([name, row]) => [name, `${row.coveredBranches}/${row.totalBranches}`]),
    ),
    freshPairedLegacyTiming: manifest.gateStatus.freshPairedLegacyTiming,
  }, null, 2));
}

async function validateArtifacts(artifacts) {
  for (const artifact of artifacts) {
    const actual = sha256(await readFile(join(BASE, artifact.path)));
    assert(actual === artifact.sha256, `${artifact.path}: sha256 ${actual}, expected ${artifact.sha256}`);
  }
}

function validateAudit(audit) {
  assert(audit.schemaVersion === 1, "unsupported upstream audit schema");
  assert(audit.completeness.status === "complete", "upstream audit is incomplete");
  for (const field of ["canonicalUrlUnknown", "pinnedRevisionUnknown", "spdxUnknown"]) {
    assert(audit.completeness[field] === 0, `upstream audit ${field} must be zero`);
  }
  assert(audit.tools.length === 6, `expected six audited tools, got ${audit.tools.length}`);
  for (const tool of audit.tools) {
    assert(/^https:\/\/github\.com\//.test(tool.canonicalRepository), `${tool.id}: non-canonical repository URL`);
    assert(/^[0-9a-f]{40}$/.test(tool.pinned.commit), `${tool.id}: commit is not a full SHA`);
    assert(typeof tool.spdx === "string" && tool.spdx.length > 0, `${tool.id}: missing SPDX`);
    assert(tool.licenseFiles.length > 0, `${tool.id}: missing license file audit`);
    assert(tool.status.startsWith("audited"), `${tool.id}: audit status is not terminal`);
    for (const file of tool.licenseFiles) {
      assert(/^[0-9a-f]{40}$/.test(file.gitBlob), `${tool.id}/${file.path}: invalid git blob`);
      assert(file.evidence.includes(tool.pinned.commit), `${tool.id}/${file.path}: evidence is not commit-pinned`);
    }
    if (tool.notice.required) {
      assert(tool.licenseFiles.some((file) => file.path === "NOTICE"), `${tool.id}: required NOTICE not audited`);
    }
  }
}

async function validateDenominators(manifest) {
  const selections = new Map();
  const derived = {
    "D_broad-v1": { methods: [], edges: [] },
    "D_primitive-reference-v1": { methods: [], edges: [] },
  };

  for (const project of manifest.projects) {
    const root = join(BASE, "projects", project.id);
    const sourceTargets = await readJson(join(root, "source-targets.json"));
    const targetManifest = await readJson(join(root, "targets.json"));
    const broadIds = new Set(sourceTargets.entries.filter((entry) => entry.sourceCallable).map((entry) => entry.methodId));
    const primitiveIds = new Set(sourceTargets.entries.filter((entry) => entry.primitiveEligible).map((entry) => entry.methodId));
    assertSameSet(await readIds(join(root, "entry-method-ids.txt")), broadIds, `${project.id} broad ids`);
    assertSameSet(await readIds(join(root, "primitive-method-ids.txt")), primitiveIds, `${project.id} primitive ids`);
    selections.set(project.id, { broadIds, primitiveIds });

    const manifestIds = new Set();
    const branchIds = new Set();
    for (const method of targetManifest.methods) {
      assert(!manifestIds.has(method.methodId), `${project.id}: duplicate method ${method.methodId}`);
      manifestIds.add(method.methodId);
      for (const branch of method.branches) {
        assert(!branchIds.has(branch.branchId), `${project.id}: duplicate branch ${branch.branchId}`);
        branchIds.add(branch.branchId);
        assert(Number.isInteger(branch.ifStmtIndex), `${branch.branchId}: missing ifStmtIndex`);
        assert(Number.isInteger(branch.successorStmtIndex), `${branch.branchId}: missing successorStmtIndex`);
        assert(Number.isInteger(branch.successorOrdinal), `${branch.branchId}: missing successorOrdinal`);
      }
      addMethod(derived["D_broad-v1"], project.id, method, broadIds);
      addMethod(derived["D_primitive-reference-v1"], project.id, method, primitiveIds);
    }
    for (const id of broadIds) assert(manifestIds.has(id), `${project.id}: broad method absent from target manifest: ${id}`);
    for (const id of primitiveIds) assert(manifestIds.has(id), `${project.id}: primitive method absent from target manifest: ${id}`);
  }

  for (const [id, expected] of Object.entries(manifest.denominators)) {
    const methods = lines(derived[id].methods);
    const edges = lines(derived[id].edges);
    assert(derived[id].methods.length === expected.methods, `${id}: method count mismatch`);
    assert(derived[id].edges.length === expected.edges, `${id}: edge count mismatch`);
    assert(sha256(methods) === expected.methodsSha256, `${id}: derived method hash mismatch`);
    assert(sha256(edges) === expected.edgesSha256, `${id}: derived edge hash mismatch`);
    assert((await readFile(join(BASE, expected.methodsFile), "utf8")) === methods, `${id}: methods file differs from derivation`);
    assert((await readFile(join(BASE, expected.edgesFile), "utf8")) === edges, `${id}: edges file differs from derivation`);
  }
  return selections;
}

function addMethod(denominator, projectId, method, selectedIds) {
  if (!selectedIds.has(method.methodId)) return;
  denominator.methods.push(`${projectId}\t${method.methodId}`);
  for (const branch of method.branches) denominator.edges.push(`${projectId}\t${branch.branchId}`);
}

function validateObservations(document, expected, selections) {
  assert(document.schemaVersion === 1, "unsupported batched observation schema");
  for (const [scenario, row] of Object.entries(expected.broad)) {
    assertExpected(aggregate(document.reports, scenario, "broad", selections), row, `batched broad/${scenario}`);
  }
  for (const [scenario, row] of Object.entries(expected.primitive)) {
    assertExpected(aggregate(document.reports, scenario, "primitive", selections), row, `batched primitive/${scenario}`);
  }
}

function validateLegacyObservations(document, expected, selections) {
  assert(document.schemaVersion === 1, "unsupported legacy observation schema");
  for (const [scenario, row] of Object.entries(expected)) {
    assertExpected(aggregate(document.reports, scenario, "primitive", selections), row, `legacy primitive/${scenario}`);
  }
}

function aggregate(reports, scenario, denominator, selections) {
  const matching = reports.filter((report) => report.scenario === scenario);
  assert(matching.length > 0, `${denominator}/${scenario}: no reports`);
  const result = {
    methods: 0,
    totalBranches: 0,
    coveredBranches: 0,
    symbolicWallMs: 0,
    machineRuns: 0,
    symbolicTargets: 0,
    symbolicReached: 0,
    symbolicReplayConfirmed: 0,
  };
  for (const report of matching) {
    assert(/^[0-9a-f]{64}$/.test(report.sourceReportSha256), `${report.projectId}/${scenario}: invalid raw SHA`);
    const ids = denominator === "broad"
      ? selections.get(report.projectId).broadIds
      : selections.get(report.projectId).primitiveIds;
    for (const method of report.methods.filter((candidate) => ids.has(candidate.methodId))) {
      result.methods += 1;
      result.totalBranches += method.totalBranches;
      result.coveredBranches += method.coveredBranches;
      if (method.symbolic == null) continue;
      result.symbolicWallMs += method.symbolic.wallMs;
      result.machineRuns += method.symbolic.machineRuns;
      result.symbolicTargets += method.symbolic.targets.length;
      result.symbolicReached += method.symbolic.targets.filter((target) => target.reached).length;
      result.symbolicReplayConfirmed += method.symbolic.targets.filter((target) => target.replayConfirmed).length;
    }
  }
  return result;
}

function validateSavedSummary(summary, acceptance) {
  for (const [scenario, expected] of Object.entries(acceptance.batched.broad)) {
    assertExpected(summary.sourceEntryAggregate.rows[scenario], expected, `saved summary broad/${scenario}`);
  }
  for (const [scenario, expected] of Object.entries(acceptance.batched.primitive)) {
    assertExpected(summary.primitiveEntryAggregate.rows[scenario], expected, `saved summary primitive/${scenario}`);
  }
  for (const [scenario, expected] of Object.entries(acceptance.historicalPerTargetPrimitive)) {
    assertExpected(summary.oldPerTargetPrimitiveAggregate.rows[scenario], expected, `saved summary legacy/${scenario}`);
  }
}

function assertExpected(actual, expected, label) {
  for (const [field, value] of Object.entries(expected)) {
    assert(actual[field] === value, `${label}.${field}: ${actual[field]}, expected ${value}`);
  }
}

async function readIds(path) {
  return new Set((await readFile(path, "utf8")).split(/\r?\n/).map((value) => value.trim()).filter(Boolean));
}

async function readJson(path) {
  return JSON.parse(await readFile(path, "utf8"));
}

function assertSameSet(actual, expected, label) {
  const missing = [...expected].filter((value) => !actual.has(value));
  const extra = [...actual].filter((value) => !expected.has(value));
  assert(missing.length === 0 && extra.length === 0, `${label}: missing=${missing}, extra=${extra}`);
}

function lines(values) {
  return `${[...values].sort().join("\n")}\n`;
}

function sha256(value) {
  return createHash("sha256").update(value).digest("hex");
}

function assert(condition, message) {
  if (!condition) throw new Error(message);
}
