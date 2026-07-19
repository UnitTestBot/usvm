#!/usr/bin/env node

import { createHash } from "node:crypto";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const BASE = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const PROJECTS = [
  { id: "the-algorithms-maths", primitive: true },
  { id: "typescript-algorithms", primitive: true },
  { id: "typescript-collections", primitive: false },
];

const CURRENT_REPORTS = [
  ["internal-pbt", "broad", "source-entry-internal-100-batched-PBT_ONLY.json"],
  ["internal-pbt-usvm", "broad", "source-entry-internal-100-batched-HYBRID.json"],
  ["usvm-only", "broad", "source-entry-internal-100-batched-SYMBOLIC_ONLY.json"],
  ["fast-check", "primitive", "primitive-fast-check-100-batched-PBT_ONLY.json"],
  ["fast-check-usvm", "primitive", "primitive-fast-check-100-batched-HYBRID.json"],
  ["jazzer", "primitive", "primitive-jazzer-1s-batched-PBT_ONLY.json"],
  ["jazzer-usvm", "primitive", "primitive-jazzer-1s-batched-HYBRID.json"],
  ["expose", "primitive", "primitive-expose-2s-batched-PBT_ONLY.json"],
  ["expose-usvm", "primitive", "primitive-expose-2s-batched-HYBRID.json"],
  ["ensemble", "primitive", "primitive-external-ensemble-batched-PBT_ONLY.json"],
  ["ensemble-usvm", "primitive", "primitive-external-ensemble-batched-HYBRID.json"],
];

const LEGACY_REPORTS = [
  ["internal-pbt-usvm", "internal-pbt-100-usvm-HYBRID.json"],
  ["fast-check-usvm", "fast-check-100-usvm-HYBRID.json"],
  ["jazzer-usvm", "jazzer-1s-replay-HYBRID.json"],
  ["expose-usvm", "expose-2s-replay-HYBRID.json"],
  ["ensemble-usvm", "external-ensemble-HYBRID.json"],
];

main().catch((error) => {
  console.error(error instanceof Error ? error.stack : String(error));
  process.exitCode = 1;
});

async function main() {
  const options = parseArgs(process.argv.slice(2));
  const selections = new Map();
  const broadMethods = [];
  const broadEdges = [];
  const primitiveMethods = [];
  const primitiveEdges = [];

  for (const project of PROJECTS) {
    const projectRoot = join(BASE, "projects", project.id);
    const sourceTargets = await readJson(join(projectRoot, "source-targets.json"));
    const targetManifest = await readJson(join(projectRoot, "targets.json"));
    const broadIds = new Set(
      sourceTargets.entries.filter((entry) => entry.sourceCallable).map((entry) => entry.methodId),
    );
    const primitiveIds = new Set(
      sourceTargets.entries.filter((entry) => entry.primitiveEligible).map((entry) => entry.methodId),
    );
    assertSameIds(await readIds(join(projectRoot, "entry-method-ids.txt")), broadIds, `${project.id} broad`);
    assertSameIds(
      await readIds(join(projectRoot, "primitive-method-ids.txt")),
      primitiveIds,
      `${project.id} primitive`,
    );
    selections.set(project.id, { broadIds, primitiveIds });

    for (const method of targetManifest.methods) {
      if (broadIds.has(method.methodId)) {
        broadMethods.push(`${project.id}\t${method.methodId}`);
        for (const branch of method.branches) broadEdges.push(`${project.id}\t${branch.branchId}`);
      }
      if (primitiveIds.has(method.methodId)) {
        primitiveMethods.push(`${project.id}\t${method.methodId}`);
        for (const branch of method.branches) primitiveEdges.push(`${project.id}\t${branch.branchId}`);
      }
    }
  }

  await mkdir(join(BASE, "denominators"), { recursive: true });
  await writeLines(join(BASE, "denominators", "D_broad-v1.methods.tsv"), broadMethods);
  await writeLines(join(BASE, "denominators", "D_broad-v1.edges.tsv"), broadEdges);
  await writeLines(join(BASE, "denominators", "D_primitive-reference-v1.methods.tsv"), primitiveMethods);
  await writeLines(join(BASE, "denominators", "D_primitive-reference-v1.edges.tsv"), primitiveEdges);

  const batched = await projectReports(options.batchedRoot, CURRENT_REPORTS, selections, false);
  const legacy = await projectReports(options.legacyRoot, LEGACY_REPORTS, selections, true);
  await mkdir(join(BASE, "observations"), { recursive: true });
  await writeJson(join(BASE, "observations", "batched-reports.json"), {
    schemaVersion: 1,
    campaign: "batched-entrypoints-2026-07-19",
    rawRootAtFreezeTime: options.batchedRoot,
    lossyProjection: ["concrete inputs", "per-execution timeline", "failure stack traces"],
    reports: batched,
  });
  await writeJson(join(BASE, "observations", "legacy-per-target-reports.json"), {
    schemaVersion: 1,
    campaign: "representative-2026-07-19-per-target",
    rawRootAtFreezeTime: options.legacyRoot,
    status: "historical reference; not a fresh paired timing acceptance baseline",
    lossyProjection: ["concrete inputs", "per-execution timeline", "failure stack traces"],
    reports: legacy,
  });

  console.log(JSON.stringify({
    broad: { methods: broadMethods.length, edges: broadEdges.length },
    primitive: { methods: primitiveMethods.length, edges: primitiveEdges.length },
    reports: { batched: batched.length, legacy: legacy.length },
  }));
}

async function projectReports(rawRoot, reportSpecs, selections, legacy) {
  const result = [];
  for (const project of PROJECTS) {
    const selection = selections.get(project.id);
    for (const spec of reportSpecs) {
      const [scenario, scopeOrFile, currentFile] = spec;
      const scope = legacy ? "primitive" : scopeOrFile;
      const file = legacy ? scopeOrFile : currentFile;
      if (scope === "primitive" && selection.primitiveIds.size === 0) continue;
      const ids = scope === "broad" ? selection.broadIds : selection.primitiveIds;
      const path = join(rawRoot, project.id, file);
      const bytes = await readFile(path);
      const report = JSON.parse(bytes.toString("utf8"));
      const methods = report.methods.filter((method) => ids.has(method.methodId)).map(projectMethod);
      assert(methods.length === ids.size, `${project.id}/${scenario}: expected ${ids.size} methods, got ${methods.length}`);
      result.push({
        projectId: project.id,
        scenario,
        denominatorScope: scope,
        sourceReport: file,
        sourceReportSha256: sha256(bytes),
        config: report.config,
        methods,
      });
    }
  }
  return result;
}

function projectMethod(method) {
  const symbolic = method.symbolic;
  return {
    methodId: method.methodId,
    totalBranches: method.totalBranches,
    coveredBranches: method.coveredBranches,
    totalWallMs: method.totalWallMs,
    pbt: method.pbt ?? null,
    symbolic: symbolic == null ? null : {
      wallMs: symbolic.wallMs,
      steps: symbolic.steps ?? sum(symbolic.targets, (target) => target.steps),
      machineRuns: symbolic.machineRuns ?? symbolic.targets.length,
      targets: symbolic.targets.map((target) => ({
        branchId: target.branchId,
        reached: target.reached,
        replayConfirmed: target.replayConfirmed,
        wallMs: target.wallMs,
        steps: target.steps,
        hintsUsed: target.hintsUsed,
        fallbackUsed: target.fallbackUsed,
      })),
    },
    typeProfile: method.typeProfile ?? null,
  };
}

function parseArgs(args) {
  const options = {
    batchedRoot: "/tmp/representative-ts-pbt-batched-entrypoints-20260719",
    legacyRoot: "/tmp/representative-ts-pbt-20260719",
  };
  for (let i = 0; i < args.length; i += 2) {
    const value = args[i + 1];
    if (!value) throw new Error(`missing value for ${args[i]}`);
    if (args[i] === "--batched-root") options.batchedRoot = resolve(value);
    else if (args[i] === "--legacy-root") options.legacyRoot = resolve(value);
    else throw new Error(`unknown option: ${args[i]}`);
  }
  return options;
}

async function readIds(path) {
  return new Set((await readFile(path, "utf8")).split(/\r?\n/).map((value) => value.trim()).filter(Boolean));
}

async function readJson(path) {
  return JSON.parse(await readFile(path, "utf8"));
}

async function writeLines(path, lines) {
  await writeFile(path, `${[...lines].sort().join("\n")}\n`, "utf8");
}

async function writeJson(path, value) {
  await writeFile(path, `${JSON.stringify(value, null, 2)}\n`, "utf8");
}

function assertSameIds(actual, expected, label) {
  const missing = [...expected].filter((value) => !actual.has(value));
  const extra = [...actual].filter((value) => !expected.has(value));
  assert(missing.length === 0 && extra.length === 0, `${label}: list mismatch; missing=${missing}, extra=${extra}`);
}

function assert(condition, message) {
  if (!condition) throw new Error(message);
}

function sha256(value) {
  return createHash("sha256").update(value).digest("hex");
}

function sum(values, selector) {
  return values.reduce((total, value) => total + selector(value), 0);
}
