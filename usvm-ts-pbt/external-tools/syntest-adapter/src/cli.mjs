#!/usr/bin/env node
import { resolve } from "node:path";
import { pathToFileURL } from "node:url";
import { runAdapter } from "./adapter.mjs";

const EXIT_CODES = Object.freeze({
  success: 0,
  unsupported_configuration: 2,
  tool_failure: 1,
  timeout_partial_corpus: 124,
});

export function parseArgs(args) {
  const names = new Map([
    ["--target-manifest", "targetManifest"],
    ["--source-targets", "sourceTargets"],
    ["--method-ids", "methodIds"],
    ["--run-config", "runConfig"],
    ["--out-dir", "outDir"],
  ]);
  const result = {};
  for (let index = 0; index < args.length; index += 2) {
    const option = args[index];
    const key = names.get(option);
    if (!key) throw new Error(`unknown option '${option}'`);
    if (Object.hasOwn(result, key)) throw new Error(`duplicate option '${option}'`);
    const value = args[index + 1];
    if (typeof value !== "string" || value.length === 0 || names.has(value)) {
      throw new Error(`option '${option}' requires one path`);
    }
    result[key] = value;
  }
  const missing = [...names.entries()].filter(([, key]) => !Object.hasOwn(result, key)).map(([name]) => name);
  if (missing.length > 0) throw new Error(`missing required options: ${missing.join(", ")}`);
  return result;
}

async function main() {
  const paths = parseArgs(process.argv.slice(2));
  const summary = await runAdapter(paths);
  process.stdout.write(`${JSON.stringify(summary)}\n`);
  process.exitCode = EXIT_CODES[summary.exitStatus];
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  main().catch((error) => {
    process.stderr.write(`${error instanceof Error ? error.stack : String(error)}\n`);
    process.exitCode = 1;
  });
}
