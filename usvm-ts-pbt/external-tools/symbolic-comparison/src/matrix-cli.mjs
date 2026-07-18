#!/usr/bin/env node

import { readFile, writeFile } from "node:fs/promises";
import { summarizeReplayMatrix } from "./matrix.mjs";

main().catch((error) => {
  console.error(error instanceof Error ? error.stack : String(error));
  process.exitCode = 1;
});

async function main() {
  const options = parseArgs(process.argv.slice(2));
  const entries = await Promise.all(options.reports.map(async (entry) => ({
    ...entry,
    report: JSON.parse(await readFile(entry.path, "utf8")),
  })));
  const report = summarizeReplayMatrix({ entries });
  await writeFile(options.out, `${JSON.stringify(report, null, 2)}\n`, "utf8");
  console.log(JSON.stringify({ out: options.out, ...report.totals,
    coverage: Object.fromEntries(Object.entries(report.tools).map(([tool, value]) => [tool, value.coveragePct])) }));
}

function parseArgs(args) {
  const result = { reports: [], out: null };
  for (let index = 0; index < args.length; index += 1) {
    switch (args[index]) {
      case "--report": result.reports.push(parseReport(args[++index])); break;
      case "--out": result.out = args[++index]; break;
      default: throw new Error(`unknown option '${args[index]}'`);
    }
  }
  if (result.reports.length === 0 || !result.out) {
    throw new Error("required: --report tool,kind,label,project,report.json --out matrix.json");
  }
  return result;
}

function parseReport(raw) {
  const fields = raw.split(",");
  if (fields.length !== 5 || fields.some((field) => field.length === 0)) {
    throw new Error("report must be tool,kind,label,project,report.json");
  }
  const [tool, kind, label, project, path] = fields;
  return { tool, kind, label, project, path };
}
