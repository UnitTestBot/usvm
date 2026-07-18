#!/usr/bin/env node

import { readFile, writeFile } from "node:fs/promises";
import { summarizeCampaign } from "./campaign.mjs";

main().catch((error) => {
  console.error(error instanceof Error ? error.stack : String(error));
  process.exitCode = 1;
});

async function main() {
  const options = parseArgs(process.argv.slice(2));
  const cases = await Promise.all(options.cases.map(async (entry) => ({
    label: entry.label,
    project: entry.project,
    externalReplay: await readJson(entry.externalReplay),
    usvm: await readJson(entry.usvm),
    exposeRaw: await readJson(entry.exposeRaw),
  })));
  const report = summarizeCampaign({ cases, marginPoints: options.marginPoints });
  await writeFile(options.out, `${JSON.stringify(report, null, 2)}\n`, "utf8");
  console.log(JSON.stringify({ out: options.out, ...report.totals, nonInferiority: report.nonInferiority }));
}

async function readJson(path) {
  return JSON.parse(await readFile(path, "utf8"));
}

function parseArgs(args) {
  const result = { cases: [], marginPoints: 2, out: null };
  for (let index = 0; index < args.length; index += 1) {
    switch (args[index]) {
      case "--case": result.cases.push(parseCase(args[++index])); break;
      case "--margin-points": result.marginPoints = Number(args[++index]); break;
      case "--out": result.out = args[++index]; break;
      default: throw new Error(`unknown option '${args[index]}'`);
    }
  }
  if (result.cases.length === 0 || !result.out || !Number.isFinite(result.marginPoints)) {
    throw new Error("required: --case label,project,external.json,usvm.json,raw.json --out summary.json");
  }
  return result;
}

function parseCase(raw) {
  const fields = raw.split(",");
  if (fields.length !== 5 || fields.some((field) => field.length === 0)) {
    throw new Error("case must be label,project,external.json,usvm.json,raw.json");
  }
  const [label, project, externalReplay, usvm, exposeRaw] = fields;
  return { label, project, externalReplay, usvm, exposeRaw };
}
