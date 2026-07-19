#!/usr/bin/env node

import { mkdir, readFile, writeFile } from "node:fs/promises";
import { dirname, join, resolve } from "node:path";

const PROJECTS = [
  {
    id: "the-algorithms-maths",
    name: "TheAlgorithms-TypeScript/maths",
    files: 42,
    commit: "19b4ced86c99815f142d4a46a028f55487b8038a",
    broad: {
      "internal-pbt": "internal-pbt-100-PBT_ONLY.json",
      "fast-check": "fast-check-100-PBT_ONLY.json",
      "usvm-only": "usvm-only-1s-SYMBOLIC_ONLY.json",
      "internal-pbt-usvm": "internal-pbt-100-usvm-HYBRID.json",
      "internal-pbt-usvm-hints": "internal-pbt-100-usvm-HYBRID_WITH_HINTS.json",
      "fast-check-usvm": "fast-check-100-usvm-HYBRID.json",
      "fast-check-usvm-hints": "fast-check-100-usvm-HYBRID_WITH_HINTS.json",
    },
    source: {
      "internal-pbt": "source-internal-pbt-100-PBT_ONLY.json",
      "fast-check": "source-fast-check-100-PBT_ONLY.json",
      jazzer: "jazzer-1s-replay-PBT_ONLY.json",
      expose: "expose-2s-replay-PBT_ONLY.json",
      "usvm-only": "usvm-only-1s-SYMBOLIC_ONLY.json",
      "internal-pbt-usvm": "internal-pbt-100-usvm-HYBRID.json",
      "internal-pbt-usvm-hints": "internal-pbt-100-usvm-HYBRID_WITH_HINTS.json",
      "fast-check-usvm": "fast-check-100-usvm-HYBRID.json",
      "fast-check-usvm-hints": "fast-check-100-usvm-HYBRID_WITH_HINTS.json",
      "jazzer-usvm": "jazzer-1s-replay-HYBRID.json",
      "expose-usvm": "expose-2s-replay-HYBRID.json",
      "external-ensemble": "external-ensemble-PBT_ONLY.json",
      "external-ensemble-usvm": "external-ensemble-HYBRID.json",
    },
  },
  {
    id: "typescript-algorithms",
    name: "javascript-datastructures-algorithms/src",
    files: 62,
    commit: "e8ee8f9b8a07589533c4243a210d4cea7b090b10",
    broad: {
      "internal-pbt": "internal-pbt-100-usvm-PBT_ONLY.json",
      "fast-check": "fast-check-100-usvm-PBT_ONLY.json",
      "usvm-only": "usvm-only-1s-SYMBOLIC_ONLY.json",
      "internal-pbt-usvm": "internal-pbt-100-usvm-HYBRID.json",
      "internal-pbt-usvm-hints": "internal-pbt-100-usvm-HYBRID_WITH_HINTS.json",
      "fast-check-usvm": "fast-check-100-usvm-HYBRID.json",
      "fast-check-usvm-hints": "fast-check-100-usvm-HYBRID_WITH_HINTS.json",
    },
    source: {
      "internal-pbt": "internal-pbt-100-usvm-PBT_ONLY.json",
      "fast-check": "fast-check-100-usvm-PBT_ONLY.json",
      jazzer: "jazzer-1s-replay-PBT_ONLY.json",
      expose: "expose-2s-replay-PBT_ONLY.json",
      "usvm-only": "usvm-only-1s-SYMBOLIC_ONLY.json",
      "internal-pbt-usvm": "internal-pbt-100-usvm-HYBRID.json",
      "internal-pbt-usvm-hints": "internal-pbt-100-usvm-HYBRID_WITH_HINTS.json",
      "fast-check-usvm": "fast-check-100-usvm-HYBRID.json",
      "fast-check-usvm-hints": "fast-check-100-usvm-HYBRID_WITH_HINTS.json",
      "jazzer-usvm": "jazzer-1s-replay-HYBRID.json",
      "expose-usvm": "expose-2s-replay-HYBRID.json",
      "external-ensemble": "external-ensemble-PBT_ONLY.json",
      "external-ensemble-usvm": "external-ensemble-HYBRID.json",
    },
  },
  {
    id: "typescript-collections",
    name: "typescript-collections/src/lib",
    files: 17,
    commit: "309bb1b6955b403b212309531607b8d17df152e5",
    broad: {
      "internal-pbt": "internal-pbt-100-usvm-PBT_ONLY.json",
      "fast-check": "fast-check-100-usvm-PBT_ONLY.json",
      "usvm-only": "usvm-only-1s-SYMBOLIC_ONLY.json",
      "internal-pbt-usvm": "internal-pbt-100-usvm-HYBRID.json",
      "internal-pbt-usvm-hints": "internal-pbt-100-usvm-HYBRID_WITH_HINTS.json",
      "fast-check-usvm": "fast-check-100-usvm-HYBRID.json",
      "fast-check-usvm-hints": "fast-check-100-usvm-HYBRID_WITH_HINTS.json",
    },
    source: {},
  },
];

main().catch((error) => {
  console.error(error instanceof Error ? error.stack : String(error));
  process.exitCode = 1;
});

async function main() {
  const options = parseArgs(process.argv.slice(2));
  const projects = [];
  for (const project of PROJECTS) projects.push(await summarizeProject(options.root, project));

  const result = {
    schemaVersion: 1,
    campaign: {
      date: "2026-07-19",
      seed: 20260719,
      pbtRunsPerMethod: 100,
      usvmTargetTimeoutSeconds: 1,
      jazzerBudgetSecondsPerMethod: 1,
      exposeBudgetSecondsPerMethod: 2,
      coverageDefinition: "EtsIR branch edges observed by concrete replay",
      note: "Single fixed-seed representative campaign; not a multi-seed confidence interval.",
    },
    projects,
    broadIrAggregate: aggregate(projects.map((project) => project.broadIr)),
    sourceCallableAggregate: aggregate(projects.map((project) => project.sourceCallable).filter(Boolean)),
    sourceGeneratorAggregate: aggregateGenerators(projects),
    gillianFeasibilityAggregate: aggregateGillian(projects),
  };
  await mkdir(dirname(options.out), { recursive: true });
  await writeFile(options.out, `${JSON.stringify(result, null, 2)}\n`, "utf8");
  if (options.csv) {
    await mkdir(dirname(options.csv), { recursive: true });
    await writeFile(options.csv, toCsv(result), "utf8");
  }
  console.log(JSON.stringify({ out: options.out, csv: options.csv, projects: projects.length }));
}

async function summarizeProject(root, project) {
  const projectRoot = join(root, project.id);
  const sourceMapping = JSON.parse(await readFile(join(projectRoot, "source-targets.json"), "utf8"));
  const sourceIds = new Set((await readFile(join(projectRoot, "source-method-ids.txt"), "utf8")).split(/\r?\n/).filter(Boolean));
  const broadRows = await summarizeReports(projectRoot, project.broad, null);
  const sourceRows = sourceIds.size > 0 ? await summarizeReports(projectRoot, project.source, sourceIds) : {};
  return {
    id: project.id,
    name: project.name,
    files: project.files,
    commit: project.commit,
    manifest: {
      methods: sourceMapping.summary.manifestMethods,
      branches: sourceMapping.summary.manifestBranches,
    },
    broadIr: { rows: broadRows },
    sourceMapping: sourceMapping.summary,
    sourceCallable: sourceIds.size > 0 ? { methods: sourceIds.size, rows: sourceRows } : null,
    generators: sourceIds.size > 0 ? await generatorStats(projectRoot, sourceIds) : null,
    gillianFeasibility: JSON.parse(await readFile(join(projectRoot, "gillian-feasibility.json"), "utf8")).summary,
  };
}

async function summarizeReports(root, files, methodIds) {
  const result = {};
  for (const [method, relativePath] of Object.entries(files)) {
    const report = JSON.parse(await readFile(join(root, relativePath), "utf8"));
    const methods = methodIds === null
      ? report.methods
      : report.methods.filter((entry) => methodIds.has(entry.methodId));
    result[method] = summarizeMethods(methods);
  }
  return result;
}

function summarizeMethods(methods) {
  const targets = methods.flatMap((method) => method.symbolic?.targets ?? []);
  const totalBranches = sum(methods, "totalBranches");
  const coveredBranches = sum(methods, "coveredBranches");
  return {
    methods: methods.length,
    branchMethods: methods.filter((method) => method.totalBranches > 0).length,
    totalBranches,
    coveredBranches,
    coveragePct: percentage(coveredBranches, totalBranches),
    totalWallMs: sum(methods, "totalWallMs"),
    pbtExecutions: methods.reduce((total, method) => total + (method.pbt?.executions ?? 0), 0),
    // Kotlin serialization omits generatedExecutions when it equals the
    // backwards-compatible default `executions`.
    pbtGenerated: methods.reduce(
      (total, method) => total + (method.pbt ? (method.pbt.generatedExecutions ?? method.pbt.executions) : 0),
      0,
    ),
    pbtUnsupported: methods.reduce((total, method) => total + (method.pbt?.unsupported ?? 0), 0),
    pbtFailures: methods.reduce((total, method) => total + (method.pbt?.failures?.length ?? 0), 0),
    externalImported: methods.reduce((total, method) => total + (method.pbt?.externalImported ?? 0), 0),
    externalExecuted: methods.reduce((total, method) => total + (method.pbt?.externalExecuted ?? 0), 0),
    externalRejected: methods.reduce((total, method) => total + (method.pbt?.externalRejected ?? 0), 0),
    externalDeduplicated: methods.reduce((total, method) => total + (method.pbt?.externalDeduplicated ?? 0), 0),
    symbolicTargets: targets.length,
    symbolicReached: targets.filter((target) => target.reached).length,
    symbolicReplayConfirmed: targets.filter((target) => target.replayConfirmed).length,
    symbolicFallbacks: targets.filter((target) => target.fallbackUsed).length,
  };
}

async function generatorStats(root, methodIds) {
  const fastCheck = JSON.parse(await readFile(join(root, "fast-check-seed-20260719.json"), "utf8"));
  const stats = {
    "fast-check": {
      cases: fastCheck.cases.filter((entry) => methodIds.has(entry.methodId)).length,
      producer: fastCheck.producer,
    },
  };
  for (const [key, relativePath] of [["jazzer", "jazzer-1s/summary.json"], ["expose", "expose-2s/summary.json"]]) {
    const report = JSON.parse(await readFile(join(root, relativePath), "utf8"));
    const results = report.results.filter((entry) => methodIds.has(entry.methodId));
    stats[key] = {
      producer: report.producer,
      methods: results.length,
      branches: results.reduce((total, entry) => total + entry.branches, 0),
      cases: results.reduce((total, entry) => total + entry.exportedCases, 0),
      elapsedMs: results.reduce((total, entry) => total + entry.elapsedMs, 0),
      exitCodes: countBy(results, (entry) => String(entry.exitCode)),
      pathErrors: results.reduce((total, entry) => total + (entry.toolSummary?.pathErrors ?? 0), 0),
    };
  }
  return stats;
}

function aggregate(sections) {
  const methods = new Set(sections.flatMap((section) => Object.keys(section.rows)));
  const rows = {};
  for (const method of methods) {
    const values = sections.map((section) => section.rows[method]).filter(Boolean);
    const totalBranches = values.reduce((total, value) => total + value.totalBranches, 0);
    const coveredBranches = values.reduce((total, value) => total + value.coveredBranches, 0);
    rows[method] = {
      applicableProjects: values.length,
      methods: values.reduce((total, value) => total + value.methods, 0),
      branchMethods: values.reduce((total, value) => total + value.branchMethods, 0),
      totalBranches,
      coveredBranches,
      coveragePct: percentage(coveredBranches, totalBranches),
      totalWallMs: values.reduce((total, value) => total + value.totalWallMs, 0),
      pbtExecutions: values.reduce((total, value) => total + value.pbtExecutions, 0),
      pbtGenerated: values.reduce((total, value) => total + value.pbtGenerated, 0),
      pbtUnsupported: values.reduce((total, value) => total + value.pbtUnsupported, 0),
      externalImported: values.reduce((total, value) => total + value.externalImported, 0),
      externalExecuted: values.reduce((total, value) => total + value.externalExecuted, 0),
      symbolicTargets: values.reduce((total, value) => total + value.symbolicTargets, 0),
      symbolicReached: values.reduce((total, value) => total + value.symbolicReached, 0),
      symbolicReplayConfirmed: values.reduce((total, value) => total + value.symbolicReplayConfirmed, 0),
      symbolicFallbacks: values.reduce((total, value) => total + value.symbolicFallbacks, 0),
    };
  }
  return { projects: sections.length, rows };
}

function aggregateGenerators(projects) {
  const result = {};
  for (const tool of ["fast-check", "jazzer", "expose"]) {
    const values = projects.map((project) => project.generators?.[tool]).filter(Boolean);
    result[tool] = {
      applicableProjects: values.length,
      methods: values.reduce((total, value) => total + (value.methods ?? 0), 0),
      branches: values.reduce((total, value) => total + (value.branches ?? 0), 0),
      cases: values.reduce((total, value) => total + value.cases, 0),
      elapsedMs: values.reduce((total, value) => total + (value.elapsedMs ?? 0), 0),
      pathErrors: values.reduce((total, value) => total + (value.pathErrors ?? 0), 0),
    };
  }
  return result;
}

function aggregateGillian(projects) {
  return {
    methods: projects.reduce((total, project) => total + project.gillianFeasibility.methods, 0),
    automaticSymbolDeclarations: projects.reduce((total, project) => total + project.gillianFeasibility.automaticSymbolDeclarations, 0),
    customHarness: projects.reduce((total, project) => total + project.gillianFeasibility.customHarness, 0),
  };
}

function sum(values, property) { return values.reduce((total, value) => total + value[property], 0); }
function percentage(value, total) { return total === 0 ? null : Math.round(10_000 * value / total) / 100; }
function countBy(values, key) {
  const result = {};
  for (const value of values) result[key(value)] = (result[key(value)] ?? 0) + 1;
  return result;
}

function toCsv(result) {
  const header = [
    "scope", "project", "method", "methods", "branchMethods", "coveredBranches",
    "totalBranches", "coveragePct", "totalWallMs", "pbtExecutions", "pbtUnsupported",
    "symbolicTargets", "symbolicReached", "symbolicReplayConfirmed", "symbolicFallbacks",
  ];
  const rows = [];
  const add = (scope, project, method, value) => rows.push([
    scope, project, method, value.methods, value.branchMethods, value.coveredBranches,
    value.totalBranches, value.coveragePct, value.totalWallMs, value.pbtExecutions,
    value.pbtUnsupported, value.symbolicTargets, value.symbolicReached,
    value.symbolicReplayConfirmed, value.symbolicFallbacks,
  ]);
  for (const project of result.projects) {
    for (const [method, value] of Object.entries(project.broadIr.rows)) add("broad-ir", project.id, method, value);
    for (const [method, value] of Object.entries(project.sourceCallable?.rows ?? {})) add("source-callable", project.id, method, value);
  }
  for (const [method, value] of Object.entries(result.broadIrAggregate.rows)) add("broad-ir", "ALL", method, value);
  for (const [method, value] of Object.entries(result.sourceCallableAggregate.rows)) add("source-callable", "ALL", method, value);
  return `${[header, ...rows].map((row) => row.map(csvCell).join(",")).join("\n")}\n`;
}

function csvCell(value) {
  const string = value === null || value === undefined ? "" : String(value);
  return /[",\n]/.test(string) ? `"${string.replaceAll('"', '""')}"` : string;
}

function parseArgs(args) {
  const result = { root: null, out: null, csv: null };
  for (let index = 0; index < args.length; index += 1) {
    switch (args[index]) {
      case "--root": result.root = resolve(args[++index]); break;
      case "--out": result.out = resolve(args[++index]); break;
      case "--csv": result.csv = resolve(args[++index]); break;
      default: throw new Error(`unknown option '${args[index]}'`);
    }
  }
  if (!result.root || !result.out) throw new Error("required: --root <campaign-dir> --out <summary.json> [--csv <table.csv>]");
  return result;
}
