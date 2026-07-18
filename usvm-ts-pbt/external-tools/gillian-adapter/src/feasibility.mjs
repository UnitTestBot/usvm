#!/usr/bin/env node

import { readFile, writeFile } from "node:fs/promises";
import { classifyManifest } from "./classify.mjs";

main().catch((error) => {
  console.error(error instanceof Error ? error.stack : String(error));
  process.exitCode = 1;
});

async function main() {
  const options = parseArgs(process.argv.slice(2));
  const report = classifyManifest(JSON.parse(await readFile(options.manifest, "utf8")));
  await writeFile(options.out, `${JSON.stringify(report, null, 2)}\n`, "utf8");
  console.log(JSON.stringify({ out: options.out, ...report.summary }));
}

function parseArgs(args) {
  const result = { manifest: null, out: null };
  for (let index = 0; index < args.length; index += 1) {
    switch (args[index]) {
      case "--manifest": result.manifest = args[++index]; break;
      case "--out": result.out = args[++index]; break;
      default: throw new Error(`unknown option '${args[index]}'`);
    }
  }
  if (!result.manifest || !result.out) throw new Error("required: --manifest <targets.json> --out <feasibility.json>");
  return result;
}
