#!/usr/bin/env node
"use strict";

const { mkdir, readFile, readdir, stat, writeFile } = require("node:fs/promises");
const { spawnSync } = require("node:child_process");
const { performance } = require("node:perf_hooks");
const { dirname, join, resolve } = require("node:path");
const { encodeEtcV2, PRODUCER, PRODUCER_NAME, PRODUCER_VERSION } = require("./etc-v2.cjs");
const { exportRawCorpus, regularFiles } = require("./corpus.cjs");
const { importEtcSeeds } = require("./seed-corpus.cjs");

const DEFAULT_LOG_CAP_BYTES = 1024 * 1024;
const MAX_LOG_CAP_BYTES = 16 * 1024 * 1024;

if (require.main === module) {
  main().catch((error) => {
    console.error(error instanceof Error ? error.stack : String(error));
    process.exitCode = 1;
  });
}

async function main() {
  const options = parseArgs(process.argv.slice(2));
  const result = await runCampaign(options);
  console.log(JSON.stringify(result.summary));
  process.exitCode = result.exitCode;
}

async function runCampaign(options, dependencies = {}) {
  const now = dependencies.now ?? (() => performance.now());
  const runner = dependencies.runner ?? spawnSync;
  const config = JSON.parse(await readFile(options.runConfig, "utf8"));
  validateRunConfig(config);
  const manifest = JSON.parse(await readFile(options.manifest, "utf8"));
  if (manifest.schemaVersion !== 2) throw new Error(`unsupported target manifest schemaVersion ${manifest.schemaVersion}; expected 2`);
  const method = manifest.methods.find((candidate) => candidate.methodId === options.methodId);
  if (!method) throw new Error(`methodId '${options.methodId}' is absent from ${options.manifest}`);
  const harness = require(resolve(options.harness));

  await prepareRawRunDirectory(options.rawRun);
  await mkdir(options.coverageCorpus, { recursive: true });
  await mkdir(options.crashCorpus, { recursive: true });
  const startedAt = now();
  const seedImport = options.initialEtc.length === 0
    ? emptySeedImport(options.methodId)
    : await importEtcSeeds({
      manifest,
      methodId: options.methodId,
      inputs: options.initialEtc,
      out: options.coverageCorpus,
      harness,
    });
  if ((await regularFiles(options.coverageCorpus)).length === 0) {
    await writeFile(join(options.coverageCorpus, "usvm-initial-seed"), Buffer.from([0]));
  }

  const packageRoot = resolve(__dirname, "..");
  const jazzer = dependencies.jazzerPath ?? join(
    packageRoot,
    "node_modules",
    ".bin",
    process.platform === "win32" ? "jazzer.cmd" : "jazzer",
  );
  const target = resolve(__dirname, "fuzz-target.cjs");
  const includes = options.includes.length > 0 ? options.includes : [`${dirname(resolve(options.harness))}/`];
  const maxLength = Math.max(
    typedFlag(config.flags, "maxLength", 256, nonNegativeInteger),
    await largestFileSize(options.coverageCorpus),
  );
  const logCapBytes = typedFlag(config.flags, "logCapBytes", DEFAULT_LOG_CAP_BYTES, positiveInteger);
  if (logCapBytes > MAX_LOG_CAP_BYTES) throw new Error(`flags.logCapBytes must be <= ${MAX_LOG_CAP_BYTES}`);
  const jazzerArgs = [target];
  if (typedFlag(config.flags, "sync", false, booleanValue)) jazzerArgs.push("--sync");
  includes.forEach((include) => jazzerArgs.push("-i", include));
  const startupFinishedAt = now();
  const remainingExplorationMs = Math.max(0, config.explorationDeadlineMs - elapsed(startedAt, startupFinishedAt));
  jazzerArgs.push(
    options.coverageCorpus,
    "--",
    `-max_total_time=${Math.max(1, Math.ceil(remainingExplorationMs / 1000))}`,
    `-seed=${config.seed}`,
    `-max_len=${maxLength}`,
    `-artifact_prefix=${resolve(options.crashCorpus)}/`,
  );
  let fuzz;
  if (remainingExplorationMs === 0) {
    const error = new Error("exploration deadline expired during adapter startup");
    error.code = "ETIMEDOUT";
    fuzz = { status: null, signal: "SIGTERM", stdout: "", stderr: "", error };
  } else try {
    fuzz = runner(jazzer, jazzerArgs, {
      cwd: options.workdir,
      env: {
        ...process.env,
        USVM_JAZZER_MANIFEST: resolve(options.manifest),
        USVM_JAZZER_METHOD_ID: options.methodId,
        USVM_JAZZER_HARNESS: resolve(options.harness),
        USVM_JAZZER_IGNORE_EXCEPTIONS: typedFlag(config.flags, "ignoreExceptions", false, booleanValue) ? "1" : "0",
      },
      encoding: "utf8",
      stdio: ["ignore", "pipe", "pipe"],
      timeout: remainingExplorationMs,
      killSignal: "SIGTERM",
      maxBuffer: MAX_LOG_CAP_BYTES,
    });
  } catch (error) {
    fuzz = { status: null, signal: null, stdout: "", stderr: "", error };
  }
  const generationFinishedAt = now();
  const timedOut = fuzz.error?.code === "ETIMEDOUT";

  let exported;
  let exportFailure = null;
  try {
    exported = await exportRawCorpus({
      method,
      methodId: options.methodId,
      harness,
      directories: [
        { path: options.coverageCorpus, kind: "coverage" },
        { path: options.crashCorpus, kind: "crash" },
      ],
      out: join(options.rawRun, "corpus.etc.jsonl"),
      seed: String(config.seed),
      generatedAtMs: Math.max(0, Math.trunc(generationFinishedAt - startedAt)),
    });
  } catch (error) {
    exportFailure = error;
    exported = {
      discoveredCorpusEntries: 0,
      generatedCases: 0,
      rejectedEntries: 1,
      replayHandoffCases: 0,
      conservationOk: false,
      countsByKind: {},
      rejections: [{ corpusKind: "adapter", rawRelativePath: "<export>", sha256: null, reason: message(error) }],
      nativeClaims: [],
    };
    await writeFile(join(options.rawRun, "corpus.etc.jsonl"), encodeEtcV2([]), "utf8");
  }
  const exportFinishedAt = now();

  const fullLog = Buffer.from(`[stdout]\n${fuzz.stdout ?? ""}\n[stderr]\n${fuzz.stderr ?? ""}`, "utf8");
  const logTruncated = fullLog.length > logCapBytes;
  await writeFile(join(options.rawRun, "stderr.log"), fullLog.subarray(0, logCapBytes));
  const producer = { name: PRODUCER_NAME, version: PRODUCER_VERSION };
  if (config.adapter.commit) producer.commit = config.adapter.commit;
  const nativeCoverage = {
    schemaVersion: 2,
    producer,
    claims: exported.nativeClaims,
    diagnostics: {
      nativeMetric: "libfuzzer-coverage-corpus-entry",
      coverageAuthority: "diagnostic-only; concrete EtsIR replay is authoritative",
      coverageCorpusEntries: exported.countsByKind.coverage ?? 0,
      crashCorpusEntries: exported.countsByKind.crash ?? 0,
    },
  };
  await writeFile(join(options.rawRun, "native-coverage.json"), `${JSON.stringify(nativeCoverage, null, 2)}\n`, "utf8");

  const startupMs = elapsed(startedAt, startupFinishedAt);
  const generationMs = elapsed(startupFinishedAt, generationFinishedAt);
  const exportMs = elapsed(generationFinishedAt, exportFinishedAt);
  const totalMs = Math.max(startupMs + generationMs + exportMs, elapsed(startedAt, exportFinishedAt));
  const exitStatus = timedOut
    ? "timeout_partial_corpus"
    : exportFailure || fuzz.error || fuzz.signal || fuzz.status !== 0
      ? "tool_failure"
      : "success";
  const accounting = {
    rawCorpusEntriesDiscovered: exported.discoveredCorpusEntries,
    casesGenerated: exported.generatedCases,
    entriesRejected: exported.rejectedEntries,
    casesHandedToReplay: exported.replayHandoffCases,
    seedCasesImported: seedImport.importedCases,
    seedCasesExported: seedImport.exportedCases,
    seedCasesRejected: seedImport.rejectedCases,
    corpusConservationOk: exported.conservationOk,
    seedConservationOk: seedImport.conservationOk,
    actualReplayAttempts: "reported by unified Kotlin replay, not this adapter",
  };
  const runMeta = {
    schemaVersion: 2,
    runId: config.runId,
    producer,
    startupMs,
    generationMs,
    exportMs,
    totalMs,
    commits: config.commits,
    exitStatus,
    timedOut,
    logCapBytes,
    logTruncated,
    overBudgetMs: Math.max(0, totalMs - config.budgetMs),
    termination: {
      jazzerExitCode: fuzz.status,
      signal: fuzz.signal,
      errorCode: fuzz.error?.code ?? null,
      exportError: exportFailure ? message(exportFailure) : null,
    },
    accounting,
    rejections: exported.rejections,
    seedImportRejections: seedImport.rejections,
  };
  await writeFile(join(options.rawRun, "run-meta.json"), `${JSON.stringify(runMeta, null, 2)}\n`, "utf8");

  const exitCode = exitStatus === "success" ? 0 : timedOut ? 124 : fuzz.status && fuzz.status > 0 ? fuzz.status : 1;
  return {
    exitCode,
    summary: {
      producer: PRODUCER,
      runId: config.runId,
      methodId: options.methodId,
      rawRun: options.rawRun,
      exitStatus,
      timedOut,
      accounting,
    },
  };
}

function validateRunConfig(config) {
  if (config?.schemaVersion !== 2) throw new Error(`unsupported run config schemaVersion ${config?.schemaVersion}; expected 2`);
  if (!config.runId || config.adapter?.name !== PRODUCER_NAME || config.adapter?.version !== PRODUCER_VERSION) {
    throw new Error(`run config adapter must be ${PRODUCER}`);
  }
  if (!Number.isSafeInteger(config.seed) || config.seed < 0 || config.seed > 0xffff_ffff) throw new Error("run config seed must be uint32");
  if (!Number.isSafeInteger(config.budgetMs) || config.budgetMs <= 0) throw new Error("run config budgetMs must be positive");
  const expectedGrace = Math.min(5000, Math.max(1000, Math.trunc(config.budgetMs / 10)));
  if (config.exportReplayGraceMs !== expectedGrace || config.explorationDeadlineMs !== config.budgetMs - expectedGrace ||
      config.hardResultDeadlineMs !== config.budgetMs) {
    throw new Error("run config deadlines do not satisfy the artifact-contract v2 formula");
  }
  if (config.cacheMode !== "cold" && config.cacheMode !== "warm") throw new Error("run config cacheMode must be cold or warm");
  if (!config.commits || Object.keys(config.commits).length === 0) throw new Error("run config commits must not be empty");
  if (!config.versions || Object.keys(config.versions).length === 0) throw new Error("run config versions must not be empty");
}

async function prepareRawRunDirectory(path) {
  await mkdir(path, { recursive: true });
  const entries = await readdir(path);
  if (entries.length > 0) throw new Error(`raw-run output directory must be empty: ${path}`);
}

async function largestFileSize(path) {
  let largest = 0;
  for (const file of await regularFiles(path)) {
    largest = Math.max(largest, (await stat(file)).size);
  }
  return largest;
}

function typedFlag(flags, key, fallback, validate) {
  const value = flags?.[key] ?? fallback;
  return validate(value, `flags.${key}`);
}

function nonNegativeInteger(value, name) {
  if (!Number.isSafeInteger(value) || value < 0) throw new Error(`${name} must be a non-negative integer`);
  return value;
}

function positiveInteger(value, name) {
  if (!Number.isSafeInteger(value) || value <= 0) throw new Error(`${name} must be a positive integer`);
  return value;
}

function booleanValue(value, name) {
  if (typeof value !== "boolean") throw new Error(`${name} must be a boolean`);
  return value;
}

function elapsed(start, end) {
  return Math.max(0, Math.trunc(end - start));
}

function emptySeedImport(methodId) {
  return {
    methodId,
    importedCases: 0,
    exportedCases: 0,
    rejectedCases: 0,
    uniqueCorpusFiles: 0,
    conservationOk: true,
    rejections: [],
  };
}

function message(error) {
  return error instanceof Error ? error.message : String(error);
}

function parseArgs(args) {
  const result = {
    runConfig: null,
    manifest: null,
    methodId: null,
    harness: null,
    coverageCorpus: null,
    crashCorpus: null,
    rawRun: null,
    workdir: process.cwd(),
    includes: [],
    initialEtc: [],
  };
  for (let index = 0; index < args.length; index += 1) {
    switch (args[index]) {
      case "--run-config": result.runConfig = args[++index]; break;
      case "--manifest": result.manifest = args[++index]; break;
      case "--method": result.methodId = args[++index]; break;
      case "--harness": result.harness = args[++index]; break;
      case "--coverage-corpus": result.coverageCorpus = args[++index]; break;
      case "--crash-corpus": result.crashCorpus = args[++index]; break;
      case "--raw-run": result.rawRun = args[++index]; break;
      case "--initial-etc": result.initialEtc.push(args[++index]); break;
      case "--workdir": result.workdir = args[++index]; break;
      case "--instrument": result.includes.push(args[++index]); break;
      default: throw new Error(`unknown option '${args[index]}'`);
    }
  }
  if (!result.runConfig || !result.manifest || !result.methodId || !result.harness || !result.coverageCorpus ||
      !result.crashCorpus || !result.rawRun) {
    throw new Error("required: --run-config --manifest --method --harness --coverage-corpus --crash-corpus --raw-run");
  }
  return result;
}

module.exports = { parseArgs, runCampaign, validateRunConfig };
