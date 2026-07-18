#!/usr/bin/env node

import { readFile, writeFile } from "node:fs/promises";
import { mapExpoSeCoverage, mapIstanbulCoverage, mapV8Coverage } from "./mapper.mjs";
import { remapExpoSeCoverage } from "./source-maps.mjs";

main().catch((error) => {
  console.error(error instanceof Error ? error.stack : String(error));
  process.exitCode = 1;
});

async function main() {
  const options = parseArgs(process.argv.slice(2));
  const manifest = JSON.parse(await readFile(options.manifest, "utf8"));
  let coverage = JSON.parse(await readFile(options.coverage, "utf8"));
  if (options.format === "expose") {
    coverage = await remapExpoSeCoverage(coverage, options.sourceMaps, { generatedLineOffset: options.generatedLineOffset });
  }
  const report = options.format === "istanbul" ? mapIstanbulCoverage(manifest, coverage)
    : options.format === "v8" ? mapV8Coverage(manifest, coverage)
      : mapExpoSeCoverage(manifest, coverage);
  await writeFile(options.out, `${JSON.stringify(report, null, 2)}\n`, "utf8");
  console.log(JSON.stringify({ tool: report.tool, out: options.out, ...report.summary }));
}

function parseArgs(args) {
  const result = { manifest: null, coverage: null, format: null, out: null, sourceMaps: [], generatedLineOffset: 0 };
  for (let index = 0; index < args.length; index += 1) {
    switch (args[index]) {
      case "--manifest": result.manifest = args[++index]; break;
      case "--istanbul": result.coverage = args[++index]; result.format = "istanbul"; break;
      case "--v8": result.coverage = args[++index]; result.format = "v8"; break;
      case "--expose": result.coverage = args[++index]; result.format = "expose"; break;
      case "--source-map": result.sourceMaps.push(args[++index]); break;
      case "--generated-line-offset": result.generatedLineOffset = integer(args[++index], "generated-line-offset"); break;
      case "--out": result.out = args[++index]; break;
      default: throw new Error(`unknown option '${args[index]}'`);
    }
  }
  if (!result.manifest || !result.coverage || !result.format || !result.out) {
    throw new Error("required: --manifest <targets.json> (--istanbul <coverage-final.json> | --v8 <v8.json> | --expose <raw.json>) --out <mapping.json>");
  }
  return result;
}

function integer(raw, name) {
  const value = Number(raw);
  if (!Number.isSafeInteger(value)) throw new Error(`${name} must be an integer`);
  return value;
}
