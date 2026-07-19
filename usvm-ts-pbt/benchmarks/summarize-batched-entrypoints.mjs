#!/usr/bin/env node

import { mkdir, readFile, writeFile } from "node:fs/promises";
import { dirname, join, resolve } from "node:path";

const PROJECTS = [
  {
    id: "the-algorithms-maths",
    name: "TheAlgorithms-TypeScript/maths",
    commit: "19b4ced86c99815f142d4a46a028f55487b8038a",
    files: 42,
    projectFrontend: false,
    oldInternalPrefix: "internal-pbt-100-usvm",
    oldFastCheckPrefix: "fast-check-100-usvm",
  },
  {
    id: "typescript-algorithms",
    name: "javascript-datastructures-algorithms/src",
    commit: "e8ee8f9b8a07589533c4243a210d4cea7b090b10",
    files: 62,
    projectFrontend: true,
    oldInternalPrefix: "internal-pbt-100-usvm",
    oldFastCheckPrefix: "fast-check-100-usvm",
  },
  {
    id: "typescript-collections",
    name: "typescript-collections/src/lib",
    commit: "309bb1b6955b403b212309531607b8d17df152e5",
    files: 17,
    projectFrontend: true,
    oldInternalPrefix: "internal-pbt-100-usvm",
    oldFastCheckPrefix: null,
  },
];

const CURRENT_BROAD = {
  "internal-pbt": "source-entry-internal-100-batched-PBT_ONLY.json",
  "internal-pbt-usvm": "source-entry-internal-100-batched-HYBRID.json",
  "usvm-only": "source-entry-internal-100-batched-SYMBOLIC_ONLY.json",
};

const CURRENT_PRIMITIVE = {
  "internal-pbt": "source-entry-internal-100-batched-PBT_ONLY.json",
  "internal-pbt-usvm": "source-entry-internal-100-batched-HYBRID.json",
  "usvm-only": "source-entry-internal-100-batched-SYMBOLIC_ONLY.json",
  "fast-check": "primitive-fast-check-100-batched-PBT_ONLY.json",
  "fast-check-usvm": "primitive-fast-check-100-batched-HYBRID.json",
  jazzer: "primitive-jazzer-1s-batched-PBT_ONLY.json",
  "jazzer-usvm": "primitive-jazzer-1s-batched-HYBRID.json",
  expose: "primitive-expose-2s-batched-PBT_ONLY.json",
  "expose-usvm": "primitive-expose-2s-batched-HYBRID.json",
  ensemble: "primitive-external-ensemble-batched-PBT_ONLY.json",
  "ensemble-usvm": "primitive-external-ensemble-batched-HYBRID.json",
};

const OLD_PRIMITIVE = {
  "internal-pbt-usvm": (project) => `${project.oldInternalPrefix}-HYBRID.json`,
  "fast-check-usvm": (project) => `${project.oldFastCheckPrefix}-HYBRID.json`,
  "jazzer-usvm": () => "jazzer-1s-replay-HYBRID.json",
  "expose-usvm": () => "expose-2s-replay-HYBRID.json",
  "ensemble-usvm": () => "external-ensemble-HYBRID.json",
};

main().catch((error) => {
  console.error(error instanceof Error ? error.stack : String(error));
  process.exitCode = 1;
});

async function main() {
  const options = parseArgs(process.argv.slice(2));
  const projects = [];
  for (const project of PROJECTS) projects.push(await summarizeProject(options, project));

  const result = {
    schemaVersion: 1,
    campaign: {
      date: "2026-07-19",
      seed: 20260719,
      solver: "YICES",
      pbtRunsPerMethod: 100,
      usvmTargetTimeoutSeconds: 1,
      targetScheduling: "one TsMachine run per method with all residual target roots passed as a list",
      entryPointSelection: "branch-bearing exported top-level source bindings with unambiguous EtsIR origin mapping",
      coverageDefinition: "EtsIR branch edges observed by concrete replay",
      note: "Single fixed-seed rerun; stochastic confidence intervals are out of scope.",
    },
    projects,
    sourceEntryAggregate: aggregate(projects.map((project) => project.sourceEntries)),
    primitiveEntryAggregate: aggregate(projects.map((project) => project.primitiveEntries).filter(Boolean)),
    oldPerTargetPrimitiveAggregate: aggregate(projects.map((project) => project.oldPerTargetPrimitive).filter(Boolean)),
  };
  result.primitiveHybridDeltas = hybridDeltas(result.primitiveEntryAggregate.rows);
  result.batchVsPerTarget = compareBatches(
    result.primitiveEntryAggregate.rows,
    result.oldPerTargetPrimitiveAggregate.rows,
  );

  await mkdir(dirname(options.out), { recursive: true });
  await writeFile(options.out, `${JSON.stringify(result, null, 2)}\n`, "utf8");
  if (options.csv) {
    await mkdir(dirname(options.csv), { recursive: true });
    await writeFile(options.csv, toCsv(result), "utf8");
  }
  console.log(JSON.stringify({ out: options.out, csv: options.csv, projects: projects.length }));
}

async function summarizeProject(options, project) {
  const currentRoot = join(options.root, project.id);
  const oldRoot = join(options.oldRoot, project.id);
  const sourceMapping = JSON.parse(await readFile(join(currentRoot, "source-targets.json"), "utf8"));
  const sourceIds = await readIds(join(currentRoot, "entry-method-ids.txt"));
  const primitiveIds = await readIds(join(currentRoot, "primitive-method-ids.txt"));
  const sourceEntries = {
    methods: sourceIds.size,
    branches: sourceMapping.summary.sourceCallableBranches,
    rows: await summarizeReports(currentRoot, CURRENT_BROAD, sourceIds),
  };
  const primitiveEntries = primitiveIds.size === 0 ? null : {
    methods: primitiveIds.size,
    branches: sourceMapping.summary.primitiveEligibleBranches,
    rows: await summarizeReports(currentRoot, CURRENT_PRIMITIVE, primitiveIds),
  };

  const oldFiles = Object.fromEntries(
    Object.entries(OLD_PRIMITIVE)
      .filter(([method]) => method !== "fast-check-usvm" || project.oldFastCheckPrefix)
      .map(([method, path]) => [method, path(project)]),
  );
  const oldPerTargetPrimitive = primitiveIds.size === 0 ? null : {
    methods: primitiveIds.size,
    rows: await summarizeReports(oldRoot, oldFiles, primitiveIds),
  };

  return {
    id: project.id,
    name: project.name,
    commit: project.commit,
    files: project.files,
    projectFrontend: project.projectFrontend,
    sourceMapping: sourceMapping.summary,
    sourceEntries,
    primitiveEntries,
    oldPerTargetPrimitive,
  };
}

async function readIds(path) {
  return new Set((await readFile(path, "utf8")).split(/\r?\n/).map((value) => value.trim()).filter(Boolean));
}

async function summarizeReports(root, files, methodIds) {
  const result = {};
  for (const [name, relativePath] of Object.entries(files)) {
    const report = JSON.parse(await readFile(join(root, relativePath), "utf8"));
    result[name] = summarizeMethods(report.methods.filter((method) => methodIds.has(method.methodId)));
  }
  return result;
}

function summarizeMethods(methods) {
  const symbolicReports = methods.map((method) => method.symbolic).filter(Boolean);
  const targets = symbolicReports.flatMap((symbolic) => symbolic.targets);
  const totalBranches = sum(methods, (method) => method.totalBranches);
  const coveredBranches = sum(methods, (method) => method.coveredBranches);
  return {
    methods: methods.length,
    totalBranches,
    coveredBranches,
    coveragePct: percentage(coveredBranches, totalBranches),
    totalWallMs: sum(methods, (method) => method.totalWallMs),
    pbtWallMs: sum(methods, (method) => method.pbt?.wallMs ?? 0),
    symbolicWallMs: sum(symbolicReports, (symbolic) => symbolic.wallMs),
    symbolicSteps: sum(symbolicReports, (symbolic) =>
      symbolic.steps ?? sum(symbolic.targets, (target) => target.steps)),
    machineRuns: sum(symbolicReports, (symbolic) => symbolic.machineRuns ?? symbolic.targets.length),
    pbtExecutions: sum(methods, (method) => method.pbt?.executions ?? 0),
    externalExecuted: sum(methods, (method) => method.pbt?.externalExecuted ?? 0),
    symbolicTargets: targets.length,
    symbolicReached: targets.filter((target) => target.reached).length,
    symbolicReplayConfirmed: targets.filter((target) => target.replayConfirmed).length,
  };
}

function aggregate(sections) {
  const names = new Set(sections.flatMap((section) => Object.keys(section.rows)));
  const rows = {};
  for (const name of names) {
    const values = sections.map((section) => section.rows[name]).filter(Boolean);
    const totalBranches = sum(values, (value) => value.totalBranches);
    const coveredBranches = sum(values, (value) => value.coveredBranches);
    rows[name] = {
      projects: values.length,
      methods: sum(values, (value) => value.methods),
      totalBranches,
      coveredBranches,
      coveragePct: percentage(coveredBranches, totalBranches),
      totalWallMs: sum(values, (value) => value.totalWallMs),
      pbtWallMs: sum(values, (value) => value.pbtWallMs),
      symbolicWallMs: sum(values, (value) => value.symbolicWallMs),
      symbolicSteps: sum(values, (value) => value.symbolicSteps),
      machineRuns: sum(values, (value) => value.machineRuns),
      pbtExecutions: sum(values, (value) => value.pbtExecutions),
      externalExecuted: sum(values, (value) => value.externalExecuted),
      symbolicTargets: sum(values, (value) => value.symbolicTargets),
      symbolicReached: sum(values, (value) => value.symbolicReached),
      symbolicReplayConfirmed: sum(values, (value) => value.symbolicReplayConfirmed),
    };
  }
  return {
    projects: sections.length,
    methods: sum(sections, (section) => section.methods),
    rows,
  };
}

function hybridDeltas(rows) {
  const result = {};
  for (const [base, hybrid] of [
    ["internal-pbt", "internal-pbt-usvm"],
    ["fast-check", "fast-check-usvm"],
    ["jazzer", "jazzer-usvm"],
    ["expose", "expose-usvm"],
    ["ensemble", "ensemble-usvm"],
  ]) {
    if (!rows[base] || !rows[hybrid]) continue;
    result[hybrid] = {
      before: rows[base].coveredBranches,
      after: rows[hybrid].coveredBranches,
      addedBranches: rows[hybrid].coveredBranches - rows[base].coveredBranches,
      addedPercentagePoints: Math.round(100 * (rows[hybrid].coveragePct - rows[base].coveragePct)) / 100,
      symbolicWallMs: rows[hybrid].symbolicWallMs,
      machineRuns: rows[hybrid].machineRuns,
      targets: rows[hybrid].symbolicTargets,
      reached: rows[hybrid].symbolicReached,
      replayConfirmed: rows[hybrid].symbolicReplayConfirmed,
    };
  }
  return result;
}

function compareBatches(currentRows, oldRows) {
  const result = {};
  for (const name of Object.keys(oldRows)) {
    if (!currentRows[name]) continue;
    const current = currentRows[name];
    const old = oldRows[name];
    result[name] = {
      oldPerTarget: pickSymbolic(old),
      currentBatched: pickSymbolic(current),
      symbolicWallRatio: old.symbolicWallMs === 0
        ? null
        : Math.round(1000 * current.symbolicWallMs / old.symbolicWallMs) / 1000,
      machineRunReduction: old.machineRuns - current.machineRuns,
      coverageDifference: current.coveredBranches - old.coveredBranches,
    };
  }
  return result;
}

function pickSymbolic(row) {
  return {
    coveredBranches: row.coveredBranches,
    symbolicWallMs: row.symbolicWallMs,
    machineRuns: row.machineRuns,
    targets: row.symbolicTargets,
    reached: row.symbolicReached,
    replayConfirmed: row.symbolicReplayConfirmed,
  };
}

function toCsv(result) {
  const header = [
    "scope", "project", "method", "methods", "coveredBranches", "totalBranches", "coveragePct",
    "totalWallMs", "pbtWallMs", "symbolicWallMs", "machineRuns", "symbolicTargets",
    "symbolicReached", "symbolicReplayConfirmed",
  ];
  const rows = [];
  const append = (scope, project, method, value) => rows.push([
    scope, project, method, value.methods, value.coveredBranches, value.totalBranches, value.coveragePct,
    value.totalWallMs, value.pbtWallMs, value.symbolicWallMs, value.machineRuns, value.symbolicTargets,
    value.symbolicReached, value.symbolicReplayConfirmed,
  ]);
  for (const project of result.projects) {
    for (const [method, value] of Object.entries(project.sourceEntries.rows)) append("source-entry", project.id, method, value);
    for (const [method, value] of Object.entries(project.primitiveEntries?.rows ?? {})) append("primitive-entry", project.id, method, value);
  }
  for (const [method, value] of Object.entries(result.sourceEntryAggregate.rows)) append("source-entry", "ALL", method, value);
  for (const [method, value] of Object.entries(result.primitiveEntryAggregate.rows)) append("primitive-entry", "ALL", method, value);
  return `${[header, ...rows].map((row) => row.map(csvCell).join(",")).join("\n")}\n`;
}

function sum(values, selector) { return values.reduce((total, value) => total + selector(value), 0); }
function percentage(value, total) { return total === 0 ? null : Math.round(10_000 * value / total) / 100; }
function csvCell(value) {
  const string = value === null || value === undefined ? "" : String(value);
  return /[",\n]/.test(string) ? `"${string.replaceAll('"', '""')}"` : string;
}

function parseArgs(args) {
  const result = { root: null, oldRoot: null, out: null, csv: null };
  for (let index = 0; index < args.length; index += 1) {
    switch (args[index]) {
      case "--root": result.root = resolve(args[++index]); break;
      case "--old-root": result.oldRoot = resolve(args[++index]); break;
      case "--out": result.out = resolve(args[++index]); break;
      case "--csv": result.csv = resolve(args[++index]); break;
      default: throw new Error(`unknown option '${args[index]}'`);
    }
  }
  if (!result.root || !result.oldRoot || !result.out) {
    throw new Error("required: --root <batched-dir> --old-root <per-target-dir> --out <summary.json> [--csv <table.csv>]");
  }
  return result;
}
