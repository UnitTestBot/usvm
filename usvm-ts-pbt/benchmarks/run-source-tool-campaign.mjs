#!/usr/bin/env node
import { createHash } from "node:crypto";
import { spawnSync } from "node:child_process";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { dirname, join, resolve } from "node:path";

const options = parseArgs(process.argv.slice(2));
const mapping = JSON.parse(await readFile(options.mapping, "utf8"));
let targets = mapping.entries.filter((entry) => entry.primitiveEligible)
  .sort((left, right) => left.methodId.localeCompare(right.methodId));
if (options.limit !== null) targets = targets.slice(0, options.limit);
await mkdir(options.outDir, { recursive: true });

const campaignCases = [];
const results = [];
for (let index = 0; index < targets.length; index += 1) {
  const target = targets[index];
  const slug = `${String(index).padStart(3, "0")}-${createHash("sha256").update(target.methodId).digest("hex").slice(0, 12)}`;
  const targetDir = resolve(options.outDir, slug);
  await mkdir(targetDir, { recursive: true });
  const corpusPath = resolve(targetDir, "etc.json");
  const started = Date.now();
  const run = options.tool === "jazzer"
    ? runJazzer(target, targetDir, corpusPath)
    : runExpoSe(target, targetDir, corpusPath);
  let corpus = null;
  try {
    corpus = JSON.parse(await readFile(corpusPath, "utf8"));
    campaignCases.push(...(corpus.cases ?? []));
  } catch {
    // Failure is explicit in the result row; never synthesize inputs.
  }
  const stdoutLines = String(run.stdout ?? "").trim().split(/\r?\n/).filter(Boolean);
  let toolSummary = null;
  try { toolSummary = JSON.parse(stdoutLines.at(-1)); } catch { /* diagnostics below */ }
  const row = {
    methodId: target.methodId,
    branches: target.branches,
    sourceFile: target.sourceFile,
    exportName: target.exportName,
    exitCode: run.status,
    signal: run.signal,
    elapsedMs: Date.now() - started,
    exportedCases: corpus?.cases?.length ?? 0,
    toolSummary,
    stderrTail: String(run.stderr ?? "").slice(-1_000),
  };
  results.push(row);
  console.log(JSON.stringify({ progress: `${index + 1}/${targets.length}`, tool: options.tool, ...row }));
}

const producer = options.tool === "jazzer" ? "jazzer.js@4.0.0" : `expose@${options.exposeCommit}`;
const mergedCorpus = { schemaVersion: 1, producer, cases: campaignCases };
const summary = {
  schemaVersion: 1,
  tool: options.tool,
  producer,
  budgetSecondsPerMethod: options.seconds,
  seed: options.seed,
  selectedMethods: targets.length,
  selectedBranches: targets.reduce((sum, target) => sum + target.branches, 0),
  completedMethods: results.filter((result) => result.exportedCases > 0).length,
  exportedCases: campaignCases.length,
  exitCodes: Object.fromEntries([...new Set(results.map((result) => String(result.exitCode)))].sort().map((code) => [
    code, results.filter((result) => String(result.exitCode) === code).length,
  ])),
  results,
};
await writeFile(resolve(options.outDir, "corpus.json"), `${JSON.stringify(mergedCorpus, null, 2)}\n`, "utf8");
await writeFile(resolve(options.outDir, "summary.json"), `${JSON.stringify(summary, null, 2)}\n`, "utf8");
await writeFile(resolve(options.outDir, "method-ids.txt"), `${targets.map((target) => target.methodId).join("\n")}\n`, "utf8");
console.log(JSON.stringify({ done: true, outDir: options.outDir, ...summary, results: undefined }));

function runJazzer(target, targetDir, corpusPath) {
  const runScript = resolve(options.adapterDir, "src/run.cjs");
  return spawnSync(process.execPath, [
    runScript,
    "--manifest", resolve(options.manifest),
    "--method", target.methodId,
    "--harness", resolve(options.harness),
    "--instrument", `${dirname(target.compiledModule)}/`,
    "--corpus", resolve(targetDir, "raw-corpus"),
    "--crashes", resolve(targetDir, "crashes"),
    "--workdir", dirname(target.compiledModule),
    "--seconds", String(options.seconds),
    "--seed", String(options.seed),
    "--sync", "--ignore-exceptions",
    "--out", corpusPath,
    "--log", resolve(targetDir, "tool.log"),
  ], {
    encoding: "utf8",
    env: harnessEnvironment(target, true),
    stdio: ["ignore", "pipe", "pipe"],
  });
}

function runExpoSe(target, targetDir, corpusPath) {
  const runScript = resolve(options.adapterDir, "src/run.mjs");
  const args = [
    runScript,
    "--expose-dir", resolve(options.exposeDir),
    "--node", resolve(options.exposeNode),
    "--commit", options.exposeCommit,
    "--manifest", resolve(options.manifest),
    "--method", target.methodId,
    "--harness", resolve(options.harness),
    "--harness-env", `USVM_COMPILED_MODULE=${target.compiledModule}`,
    "--harness-env", `USVM_MODULE_EXPORT=${target.exportName}`,
    "--workdir", targetDir,
    "--seconds", String(options.seconds),
    "--test-timeout", String(options.seconds),
    "--raw", resolve(targetDir, "raw.json"),
    "--out", corpusPath,
    "--log", resolve(targetDir, "tool.log"),
  ];
  if (options.z3Library) args.push("--z3-library", resolve(options.z3Library));
  return spawnSync(process.execPath, args, {
    encoding: "utf8",
    env: harnessEnvironment(target, false),
    stdio: ["ignore", "pipe", "pipe"],
  });
}

function harnessEnvironment(target, includeBounds) {
  const environment = {
    ...process.env,
    USVM_COMPILED_MODULE: target.compiledModule,
    USVM_MODULE_EXPORT: target.exportName,
    USVM_SUPPRESS_CONSOLE: "1",
  };
  if (includeBounds) {
    environment.USVM_NUMBER_MIN = String(options.numberMin);
    environment.USVM_NUMBER_MAX = String(options.numberMax);
  } else {
    delete environment.USVM_NUMBER_MIN;
    delete environment.USVM_NUMBER_MAX;
  }
  return environment;
}

function parseArgs(args) {
  const result = {
    tool: null, mapping: null, manifest: null, harness: null, adapterDir: null, outDir: null,
    seconds: 1, seed: 20260719, limit: null, numberMin: -100, numberMax: 100,
    exposeDir: null, exposeNode: process.execPath, z3Library: null,
    exposeCommit: "ec03edf85f883248612b1d498c6a7d9189d16d6f",
  };
  for (let index = 0; index < args.length; index += 1) {
    switch (args[index]) {
      case "--tool": result.tool = args[++index]; break;
      case "--mapping": result.mapping = args[++index]; break;
      case "--manifest": result.manifest = args[++index]; break;
      case "--harness": result.harness = args[++index]; break;
      case "--adapter-dir": result.adapterDir = args[++index]; break;
      case "--out": result.outDir = resolve(args[++index]); break;
      case "--seconds": result.seconds = positiveInteger(args[++index], "seconds"); break;
      case "--seed": result.seed = integer(args[++index], "seed"); break;
      case "--limit": result.limit = positiveInteger(args[++index], "limit"); break;
      case "--number-min": result.numberMin = Number(args[++index]); break;
      case "--number-max": result.numberMax = Number(args[++index]); break;
      case "--expose-dir": result.exposeDir = args[++index]; break;
      case "--expose-node": result.exposeNode = args[++index]; break;
      case "--z3-library": result.z3Library = args[++index]; break;
      case "--expose-commit": result.exposeCommit = args[++index]; break;
      default: throw new Error(`unknown option '${args[index]}'`);
    }
  }
  if (!["jazzer", "expose"].includes(result.tool) || !result.mapping || !result.manifest ||
      !result.harness || !result.adapterDir || !result.outDir) {
    throw new Error("required: --tool jazzer|expose --mapping --manifest --harness --adapter-dir --out");
  }
  if (result.tool === "expose" && !result.exposeDir) throw new Error("ExpoSE requires --expose-dir");
  if (!Number.isFinite(result.numberMin) || !Number.isFinite(result.numberMax) || result.numberMin > result.numberMax) {
    throw new Error("number bounds must be finite and ordered");
  }
  return result;
}

function integer(raw, name) {
  const value = Number(raw);
  if (!Number.isSafeInteger(value)) throw new Error(`${name} must be an integer`);
  return value;
}

function positiveInteger(raw, name) {
  const value = integer(raw, name);
  if (value <= 0) throw new Error(`${name} must be positive`);
  return value;
}
