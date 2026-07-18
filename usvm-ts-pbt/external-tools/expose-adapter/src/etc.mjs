import { readFile } from "node:fs/promises";
import { decodeJsonSeed } from "./value-codec.mjs";

export async function readInitialArguments(paths, methodId) {
  for (const path of paths) {
    const corpus = await readEtc(path);
    const testCase = corpus.cases.find((candidate) => candidate.methodId === methodId);
    if (testCase) return testCase.arguments.map((value, index) => decodeJsonSeed(value, `$arguments[${index}]`));
  }
  return null;
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
  const records = text.split(/\r?\n/).map((line) => line.trim()).filter(Boolean).map(JSON.parse);
  const header = records[0]?.schemaVersion ? records.shift() : { schemaVersion: 1, producer: path };
  if (header.schemaVersion !== 1) throw new Error(`unsupported ETC schemaVersion ${header.schemaVersion}`);
  return { ...header, cases: records };
}
