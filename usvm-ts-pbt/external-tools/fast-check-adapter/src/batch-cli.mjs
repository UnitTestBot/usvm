#!/usr/bin/env node
import { readFile, writeFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { generateBatchCases, selectBatchMethods, summarizeSelection } from "./batch.mjs";
import { encodeCorpus, SCHEMA_VERSION } from "./corpus.mjs";

const packageJsonPath = resolve(dirname(fileURLToPath(import.meta.url)), "../package.json");
const packageJson = JSON.parse(await readFile(packageJsonPath, "utf8"));
const producer = `fast-check@${packageJson.dependencies["fast-check"]}`;
const options = parseArgs(process.argv.slice(2));
const manifest = JSON.parse(await readFile(options.manifest, "utf8"));
if (manifest.schemaVersion !== 1) throw new Error(`unsupported target manifest schemaVersion ${manifest.schemaVersion}`);

const selection = selectBatchMethods(manifest, options.entryKinds);
const cases = generateBatchCases(selection.selected, options);
const selectionSummary = summarizeSelection(manifest, selection, options);
const corpus = { schemaVersion: SCHEMA_VERSION, producer, cases };

await writeFile(options.out, encodeCorpus(corpus, options.jsonLines), "utf8");
await writeFile(options.selectionOut, `${JSON.stringify(selectionSummary, null, 2)}\n`, "utf8");
if (options.methodIdsOut !== null) {
  await writeFile(options.methodIdsOut, `${selectionSummary.selected.methodIds.join("\n")}\n`, "utf8");
}
console.log(JSON.stringify({
  producer,
  selectedMethods: selectionSummary.selected.methods,
  selectedBranches: selectionSummary.selected.branches,
  excludedMethods: selectionSummary.excluded.methods,
  exportedCases: cases.length,
  runsPerMethod: options.runsPerMethod,
  seed: options.seed,
  out: options.out,
  selectionOut: options.selectionOut,
  methodIdsOut: options.methodIdsOut,
}));

function parseArgs(args) {
  const result = {
    manifest: null,
    out: null,
    selectionOut: null,
    methodIdsOut: null,
    runsPerMethod: 1_000,
    seed: 0,
    entryKinds: ["free", "static"],
    jsonLines: false,
  };
  for (let index = 0; index < args.length; index += 1) {
    switch (args[index]) {
      case "--manifest": result.manifest = args[++index]; break;
      case "--out": result.out = args[++index]; break;
      case "--selection-out": result.selectionOut = args[++index]; break;
      case "--method-ids-out": result.methodIdsOut = args[++index]; break;
      case "--runs-per-method": result.runsPerMethod = parseInteger(args[++index], "runs-per-method"); break;
      case "--seed": result.seed = parseInteger(args[++index], "seed"); break;
      case "--entry-kinds": result.entryKinds = args[++index].split(",").map((value) => value.trim()).filter(Boolean); break;
      case "--jsonl": result.jsonLines = true; break;
      case "--help": usage(0); break;
      default: throw new Error(`unknown option '${args[index]}'`);
    }
  }
  if (!result.manifest || !result.out || !result.selectionOut) usage(1);
  if (result.runsPerMethod < 0) throw new Error("runs-per-method must be non-negative");
  if (result.entryKinds.length === 0) throw new Error("entry-kinds must not be empty");
  return result;
}

function parseInteger(raw, name) {
  const parsed = Number(raw);
  if (!Number.isSafeInteger(parsed)) throw new Error(`${name} must be a safe integer`);
  return parsed;
}

function usage(exitCode) {
  console.error(`Usage: node src/batch-cli.mjs --manifest targets.json --out corpus.json --selection-out selection.json [options]

  --runs-per-method <n>  generated runs for every selected method (default: 1000)
  --seed <n>             campaign seed; each method derives a stable seed (default: 0)
  --entry-kinds <list>   accepted manifest entry kinds (default: free,static)
  --method-ids-out <file> write selected method IDs, one per line
  --jsonl                emit ETC JSONL instead of a single JSON document`);
  process.exit(exitCode);
}
