#!/usr/bin/env node
"use strict";

const { readFile } = require("node:fs/promises");
const { resolve } = require("node:path");
const { exportRawCorpus } = require("./corpus.cjs");

main().catch((error) => {
  console.error(error instanceof Error ? error.stack : String(error));
  process.exitCode = 1;
});

async function main() {
  const options = parseArgs(process.argv.slice(2));
  const manifest = JSON.parse(await readFile(options.manifest, "utf8"));
  if (manifest.schemaVersion !== 1) throw new Error(`unsupported target manifest schemaVersion ${manifest.schemaVersion}`);
  const method = manifest.methods.find((candidate) => candidate.methodId === options.methodId);
  if (!method) throw new Error(`methodId '${options.methodId}' is absent from ${options.manifest}`);
  const harness = require(resolve(options.harness));
  const summary = await exportRawCorpus({
    method,
    methodId: options.methodId,
    harness,
    directories: options.directories,
    out: options.out,
  });
  console.log(JSON.stringify({ producer: "jazzer.js@4.0.0", methodId: options.methodId, out: options.out, ...summary }));
}

function parseArgs(args) {
  const result = { manifest: null, methodId: null, harness: null, out: null, directories: [] };
  for (let index = 0; index < args.length; index += 1) {
    switch (args[index]) {
      case "--manifest": result.manifest = args[++index]; break;
      case "--method": result.methodId = args[++index]; break;
      case "--harness": result.harness = args[++index]; break;
      case "--corpus": result.directories.push(args[++index]); break;
      case "--crashes": result.directories.push(args[++index]); break;
      case "--out": result.out = args[++index]; break;
      default: throw new Error(`unknown option '${args[index]}'`);
    }
  }
  if (!result.manifest || !result.methodId || !result.harness || !result.out || result.directories.length === 0) {
    throw new Error("required: --manifest --method --harness --corpus <dir> --out <file>");
  }
  return result;
}
