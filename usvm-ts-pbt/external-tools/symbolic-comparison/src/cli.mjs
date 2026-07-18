#!/usr/bin/env node

import { readFile, writeFile } from "node:fs/promises";
import { summarizeComparison } from "./summarize.mjs";

main().catch((error) => {
  console.error(error instanceof Error ? error.stack : String(error));
  process.exitCode = 1;
});

async function main() {
  const options = parseArgs(process.argv.slice(2));
  const externalReplay = await readJson(options.externalReplay);
  const usvm = await readJson(options.usvm);
  const exposeRaw = await readNamedJson(options.exposeRaw);
  const mappings = await readNamedJson(options.mappings);
  const report = summarizeComparison({ externalReplay, usvm, exposeRaw, mappings, marginPoints: options.marginPoints });
  await writeFile(options.out, `${JSON.stringify(report, null, 2)}\n`, "utf8");
  console.log(JSON.stringify({ out: options.out, ...report.totals, nonInferiority: report.nonInferiority }));
}

async function readNamedJson(entries) {
  return Object.fromEntries(await Promise.all(entries.map(async ({ name, path }) => [name, await readJson(path)])));
}

async function readJson(path) {
  return JSON.parse(await readFile(path, "utf8"));
}

function parseArgs(args) {
  const result = { externalReplay: null, usvm: null, exposeRaw: [], mappings: [], marginPoints: 2, out: null };
  for (let index = 0; index < args.length; index += 1) {
    switch (args[index]) {
      case "--external-replay": result.externalReplay = args[++index]; break;
      case "--usvm": result.usvm = args[++index]; break;
      case "--expose-raw": result.exposeRaw.push(namedPath(args[++index])); break;
      case "--mapping": result.mappings.push(namedPath(args[++index])); break;
      case "--margin-points": result.marginPoints = Number(args[++index]); break;
      case "--out": result.out = args[++index]; break;
      default: throw new Error(`unknown option '${args[index]}'`);
    }
  }
  if (!result.externalReplay || !result.usvm || !result.out || !Number.isFinite(result.marginPoints)) {
    throw new Error("required: --external-replay <PBT_ONLY.json> --usvm <SYMBOLIC_ONLY.json> --out <comparison.json>");
  }
  return result;
}

function namedPath(raw) {
  const separator = raw.indexOf("=");
  if (separator <= 0 || separator === raw.length - 1) throw new Error("expected method=path");
  return { name: raw.slice(0, separator), path: raw.slice(separator + 1) };
}
