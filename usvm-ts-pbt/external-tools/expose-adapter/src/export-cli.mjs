#!/usr/bin/env node

import { readFile } from "node:fs/promises";
import { exportExpoSeCorpus } from "./export.mjs";

main().catch((error) => {
  console.error(error instanceof Error ? error.stack : String(error));
  process.exitCode = 1;
});

async function main() {
  const options = parseArgs(process.argv.slice(2));
  const manifest = JSON.parse(await readFile(options.manifest, "utf8"));
  const method = manifest.methods.find((candidate) => candidate.methodId === options.methodId);
  if (!method) throw new Error(`methodId '${options.methodId}' is absent from ${options.manifest}`);
  const summary = await exportExpoSeCorpus({
    rawPath: options.raw, outPath: options.out, method, methodId: options.methodId,
    harnessPath: options.harness, producer: options.producer,
  });
  console.log(JSON.stringify({ producer: options.producer, methodId: options.methodId, out: options.out, ...summary }));
}

function parseArgs(args) {
  const result = { manifest: null, methodId: null, harness: null, raw: null, out: null, producer: "expose@unknown" };
  for (let index = 0; index < args.length; index += 1) {
    switch (args[index]) {
      case "--manifest": result.manifest = args[++index]; break;
      case "--method": result.methodId = args[++index]; break;
      case "--harness": result.harness = args[++index]; break;
      case "--raw": result.raw = args[++index]; break;
      case "--out": result.out = args[++index]; break;
      case "--producer": result.producer = args[++index]; break;
      default: throw new Error(`unknown option '${args[index]}'`);
    }
  }
  if (!result.manifest || !result.methodId || !result.harness || !result.raw || !result.out) {
    throw new Error("required: --manifest --method --harness --raw <ExpoSE JSON> --out <ETC>");
  }
  return result;
}
