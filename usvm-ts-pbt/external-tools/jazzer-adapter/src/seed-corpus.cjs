#!/usr/bin/env node
"use strict";

const { createHash } = require("node:crypto");
const { mkdir, readFile, writeFile } = require("node:fs/promises");
const { join } = require("node:path");
const { encodeMethodInput } = require("./type-decoder.cjs");

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
  const cases = [];
  for (const path of options.inputs) cases.push(...(await readEtc(path)).cases.filter((testCase) => testCase.methodId === options.methodId));
  await mkdir(options.out, { recursive: true });
  let exported = 0;
  const rejections = [];
  for (const testCase of cases) {
    try {
      const raw = encodeMethodInput(testCase.arguments, method);
      const hash = createHash("sha256").update(raw).digest("hex");
      await writeFile(join(options.out, hash), raw);
      exported += 1;
    } catch (error) {
      rejections.push({ id: testCase.id, reason: error instanceof Error ? error.message : String(error) });
    }
  }
  console.log(JSON.stringify({ methodId: options.methodId, imported: cases.length, exported, rejections }));
}

async function readEtc(path) {
  const text = await readFile(path, "utf8");
  try {
    const parsed = JSON.parse(text);
    if (Array.isArray(parsed)) return { schemaVersion: 1, producer: path, cases: parsed };
    if (Array.isArray(parsed.cases)) return parsed;
  } catch {
    // JSONL below.
  }
  const lines = text.split(/\r?\n/).map((line) => line.trim()).filter(Boolean).map((line, index) => {
    try {
      return JSON.parse(line);
    } catch (error) {
      throw new Error(`${path}:${index + 1}: ${error instanceof Error ? error.message : String(error)}`);
    }
  });
  const header = lines[0]?.schemaVersion ? lines.shift() : { schemaVersion: 1, producer: path };
  if (header.schemaVersion !== 1) throw new Error(`unsupported ETC schemaVersion ${header.schemaVersion} in ${path}`);
  return { ...header, cases: lines };
}

function parseArgs(args) {
  const result = { manifest: null, methodId: null, out: null, inputs: [] };
  for (let index = 0; index < args.length; index += 1) {
    switch (args[index]) {
      case "--manifest": result.manifest = args[++index]; break;
      case "--method": result.methodId = args[++index]; break;
      case "--external-inputs": result.inputs.push(args[++index]); break;
      case "--out": result.out = args[++index]; break;
      default: throw new Error(`unknown option '${args[index]}'`);
    }
  }
  if (!result.manifest || !result.methodId || !result.out || result.inputs.length === 0) {
    throw new Error("required: --manifest --method --external-inputs <ETC> --out <corpus-dir>");
  }
  return result;
}
