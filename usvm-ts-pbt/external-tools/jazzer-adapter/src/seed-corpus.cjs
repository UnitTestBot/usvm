#!/usr/bin/env node
"use strict";

const { createHash } = require("node:crypto");
const { mkdir, readFile, writeFile } = require("node:fs/promises");
const { join, resolve } = require("node:path");
const { parseEtcV2 } = require("./etc-v2.cjs");
const { encodeMethodInvocation } = require("./type-decoder.cjs");

if (require.main === module) {
  main().catch((error) => {
    console.error(error instanceof Error ? error.stack : String(error));
    process.exitCode = 1;
  });
}

async function main() {
  const options = parseArgs(process.argv.slice(2));
  const manifest = await readManifest(options.manifest);
  const harness = options.harness ? require(resolve(options.harness)) : {};
  const summary = await importEtcSeeds({
    manifest,
    methodId: options.methodId,
    inputs: options.inputs,
    out: options.out,
    harness,
  });
  console.log(JSON.stringify(summary));
}

async function importEtcSeeds({ manifest, methodId, inputs, out, harness = {} }) {
  if (manifest.schemaVersion !== 2) throw new Error(`unsupported target manifest schemaVersion ${manifest.schemaVersion}; expected 2`);
  const method = manifest.methods.find((candidate) => candidate.methodId === methodId);
  if (!method) throw new Error(`methodId '${methodId}' is absent from target manifest`);
  const cases = [];
  for (const path of inputs) {
    const corpus = parseEtcV2(await readFile(path, "utf8"), path);
    cases.push(...corpus.cases.filter((testCase) => testCase.methodId === methodId).map((testCase) => ({ testCase, source: path })));
  }
  await mkdir(out, { recursive: true });
  let exported = 0;
  let uniqueFiles = 0;
  const written = new Set();
  const rejections = [];
  for (const { testCase, source } of cases) {
    try {
      const raw = encodeMethodInvocation(testCase, method, harness);
      const hash = createHash("sha256").update(raw).digest("hex");
      if (!written.has(hash)) {
        await writeFile(join(out, hash), raw);
        written.add(hash);
        uniqueFiles += 1;
      }
      exported += 1;
    } catch (error) {
      rejections.push({
        id: testCase.id,
        source,
        reason: error instanceof Error ? error.message : String(error),
      });
    }
  }
  return {
    methodId,
    importedCases: cases.length,
    exportedCases: exported,
    rejectedCases: rejections.length,
    uniqueCorpusFiles: uniqueFiles,
    conservationOk: exported + rejections.length === cases.length,
    rejections,
  };
}

async function readManifest(path) {
  const manifest = JSON.parse(await readFile(path, "utf8"));
  if (manifest.schemaVersion !== 2) throw new Error(`unsupported target manifest schemaVersion ${manifest.schemaVersion}; expected 2`);
  return manifest;
}

function parseArgs(args) {
  const result = { manifest: null, methodId: null, harness: null, out: null, inputs: [] };
  for (let index = 0; index < args.length; index += 1) {
    switch (args[index]) {
      case "--manifest": result.manifest = args[++index]; break;
      case "--method": result.methodId = args[++index]; break;
      case "--harness": result.harness = args[++index]; break;
      case "--external-inputs": result.inputs.push(args[++index]); break;
      case "--out": result.out = args[++index]; break;
      default: throw new Error(`unknown option '${args[index]}'`);
    }
  }
  if (!result.manifest || !result.methodId || !result.out || result.inputs.length === 0) {
    throw new Error("required: --manifest --method --external-inputs <ETC-v2 JSONL> --out <corpus-dir> [--harness <file>]");
  }
  return result;
}

module.exports = { importEtcSeeds, parseArgs, readManifest };
