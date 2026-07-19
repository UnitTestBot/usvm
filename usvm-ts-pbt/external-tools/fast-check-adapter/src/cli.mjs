#!/usr/bin/env node
import { runAdapter } from "./adapter.mjs";

try {
  const options = parseArgs(process.argv.slice(2));
  const result = await runAdapter(options);
  process.stdout.write(`${JSON.stringify(result.event)}\n`);
  process.exitCode = result.exitCode;
} catch (error) {
  process.stderr.write(`${error instanceof Error ? error.message : String(error)}\n`);
  process.exitCode = 64;
}

export function parseArgs(args) {
  const result = {
    targetManifest: null,
    sourceTargets: null,
    methodIds: null,
    initialEtc: null,
    runConfig: null,
    seed: null,
    budgetMs: null,
    exportReplayGraceMs: null,
    outDir: null,
    harness: null,
    path: null,
    runId: null,
    cacheMode: "cold",
  };
  if (args[0] !== "run") usage(args[0] === "--help" || args[0] === "-h" ? 0 : 64);
  for (let index = 1; index < args.length; index += 1) {
    const option = args[index];
    switch (option) {
      case "--target-manifest": result.targetManifest = value(args, ++index, option); break;
      case "--source-targets": result.sourceTargets = value(args, ++index, option); break;
      case "--method-ids": result.methodIds = value(args, ++index, option); break;
      case "--initial-etc": result.initialEtc = value(args, ++index, option); break;
      case "--run-config": result.runConfig = value(args, ++index, option); break;
      case "--seed": result.seed = integer(value(args, ++index, option), option, 0, 0xffff_ffff); break;
      case "--budget-ms": result.budgetMs = integer(value(args, ++index, option), option, 1); break;
      case "--export-replay-grace-ms": result.exportReplayGraceMs = integer(value(args, ++index, option), option, 1); break;
      case "--out-dir": result.outDir = value(args, ++index, option); break;
      case "--harness": result.harness = value(args, ++index, option); break;
      case "--path": result.path = value(args, ++index, option, true); break;
      case "--run-id": result.runId = value(args, ++index, option); break;
      case "--cache-mode": result.cacheMode = value(args, ++index, option); break;
      case "--help": usage(0); break;
      default: throw new Error(`unknown option '${option}'`);
    }
  }
  for (const name of ["targetManifest", "sourceTargets", "methodIds", "outDir"]) {
    if (result[name] === null) usage(64);
  }
  if (result.runConfig === null) {
    if (result.seed === null || result.budgetMs === null || result.exportReplayGraceMs === null) usage(64);
  }
  return result;
}

function value(args, index, option, allowEmpty = false) {
  const entry = args[index];
  if (entry === undefined || (!allowEmpty && entry.length === 0)) throw new Error(`${option} requires a value`);
  return entry;
}

function integer(raw, option, min, max = Number.MAX_SAFE_INTEGER) {
  const parsed = Number(raw);
  if (!Number.isSafeInteger(parsed) || parsed < min || parsed > max) {
    throw new Error(`${option} must be an integer in ${min}..${max}`);
  }
  return parsed;
}

function usage(exitCode) {
  const stream = exitCode === 0 ? process.stdout : process.stderr;
  stream.write(`Usage: fast-check-adapter run
  --target-manifest target-manifest.json
  --source-targets source-targets.jsonl
  --method-ids method-ids.txt
  [--initial-etc initial.etc.jsonl]
  [--run-config run-config.json]
  --seed <unsigned-int>
  --budget-ms <end-to-end-budget>
  --export-replay-grace-ms <reserved-grace>
  --out-dir <empty-run-directory>
  [--harness harness.mjs]
  [--path fast-check-path]

When --run-config is present, seed/budget/grace flags are optional but must
match it when supplied. The adapter emits only the four raw-v2 artifacts.\n`);
  process.exit(exitCode);
}
