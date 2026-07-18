#!/usr/bin/env node
import fc from "fast-check";
import { readFile, writeFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import { arbitraryForMethod } from "./arbitraries.mjs";
import { encodeCorpus, makeCase, SCHEMA_VERSION } from "./corpus.mjs";

const packageJsonPath = resolve(dirname(fileURLToPath(import.meta.url)), "../package.json");
const packageJson = JSON.parse(await readFile(packageJsonPath, "utf8"));
const producer = `fast-check@${packageJson.dependencies["fast-check"]}`;

const options = parseArgs(process.argv.slice(2));
const manifest = JSON.parse(await readFile(options.manifest, "utf8"));
if (manifest.schemaVersion !== 1) throw new Error(`unsupported target manifest schemaVersion ${manifest.schemaVersion}`);
const method = manifest.methods.find((candidate) => candidate.methodId === options.methodId);
if (!method) throw new Error(`methodId '${options.methodId}' is absent from ${options.manifest}`);

const arbitrary = arbitraryForMethod(method);
const cases = [];
let counterexample = null;

if (options.harness === null) {
  const samples = fc.sample(arbitrary, { seed: options.seed, numRuns: options.runs });
  samples.forEach((args, index) => {
    cases.push(makeCase({
      id: `seed-${options.seed}-run-${index}`,
      methodId: method.methodId,
      args,
      metadata: { seed: options.seed, run: index, phase: "sample" },
    }));
  });
} else {
  const harnessModule = await import(pathToFileURL(resolve(options.harness)).href);
  const invoke = harnessModule.invoke ?? harnessModule.default;
  if (typeof invoke !== "function") throw new Error("harness must export invoke(args) or a default function");
  const toCorpusCase = harnessModule.toCorpusCase;
  let invocation = 0;
  let lastFailure = "property returned false";

  const record = async (args, id, metadata) => {
    const mapped = typeof toCorpusCase === "function"
      ? await toCorpusCase(args)
      : { receiver: undefined, arguments: args };
    if (!mapped || !Array.isArray(mapped.arguments)) {
      throw new Error("toCorpusCase(args) must return { receiver?, arguments: [...] }");
    }
    cases.push(makeCase({
      id,
      methodId: method.methodId,
      receiver: mapped.receiver,
      args: mapped.arguments,
      metadata,
    }));
  };

  const property = fc.asyncProperty(arbitrary, async (args) => {
    const run = invocation++;
    await record(args, `seed-${options.seed}-invocation-${run}`, {
      seed: options.seed,
      invocation: run,
      phase: "execute-or-shrink",
    });
    try {
      const verdict = await invoke(args);
      if (verdict === false) {
        lastFailure = "property returned false";
        return false;
      }
      return true;
    } catch (error) {
      lastFailure = error instanceof Error ? `${error.name}: ${error.message}` : String(error);
      return false;
    }
  });
  const checkParameters = { seed: options.seed, numRuns: options.runs };
  if (options.path !== null) checkParameters.path = options.path;
  const details = await fc.check(property, checkParameters);
  if (details.failed) {
    const args = details.counterexample?.[0];
    if (Array.isArray(args)) {
      await record(args, `seed-${options.seed}-counterexample`, {
        seed: options.seed,
        phase: "counterexample",
        path: details.counterexamplePath ?? "",
        failure: lastFailure.slice(0, 500),
      });
    }
    counterexample = {
      path: details.counterexamplePath ?? null,
      failure: lastFailure,
      numRuns: details.numRuns,
      numShrinks: details.numShrinks,
    };
  }
}

const corpus = { schemaVersion: SCHEMA_VERSION, producer, cases };
await writeFile(options.out, encodeCorpus(corpus, options.jsonLines), "utf8");
console.log(JSON.stringify({
  producer,
  methodId: method.methodId,
  seed: options.seed,
  path: options.path,
  requestedRuns: options.runs,
  exportedCases: cases.length,
  counterexample,
  out: options.out,
}));

function parseArgs(args) {
  const result = {
    manifest: null,
    methodId: null,
    out: null,
    runs: 1_000,
    seed: 0,
    harness: null,
    path: null,
    jsonLines: false,
  };
  for (let index = 0; index < args.length; index += 1) {
    switch (args[index]) {
      case "--manifest": result.manifest = args[++index]; break;
      case "--method": result.methodId = args[++index]; break;
      case "--out": result.out = args[++index]; break;
      case "--runs": result.runs = parseInteger(args[++index], "runs"); break;
      case "--seed": result.seed = parseInteger(args[++index], "seed"); break;
      case "--harness": result.harness = args[++index]; break;
      case "--path": result.path = args[++index]; break;
      case "--jsonl": result.jsonLines = true; break;
      case "--help": usage(0); break;
      default: throw new Error(`unknown option '${args[index]}'`);
    }
  }
  if (!result.manifest || !result.methodId || !result.out) usage(1);
  if (result.runs < 0) throw new Error("runs must be non-negative");
  return result;
}

function parseInteger(raw, name) {
  const parsed = Number(raw);
  if (!Number.isSafeInteger(parsed)) throw new Error(`${name} must be a safe integer`);
  return parsed;
}

function usage(exitCode) {
  console.error(`Usage: node src/cli.mjs --manifest targets.json --method <methodId> --out corpus.json [options]

  --runs <n>         generated runs (default: 1000)
  --seed <n>         fast-check seed (default: 0)
  --path <path>      replay a fast-check counterexample path (harness mode)
  --harness <file>   ESM module exporting invoke(args), optionally toCorpusCase(args)
  --jsonl            emit ETC JSONL instead of a single JSON document`);
  process.exit(exitCode);
}
