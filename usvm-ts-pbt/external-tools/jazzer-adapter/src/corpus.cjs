"use strict";

const { createHash } = require("node:crypto");
const { readdir, readFile, writeFile } = require("node:fs/promises");
const { basename, join } = require("node:path");
const { decodeMethodInput } = require("./type-decoder.cjs");

async function exportRawCorpus({ method, methodId, harness, directories, out }) {
  const files = [];
  for (const directory of directories) {
    if (!directory) continue;
    for (const path of await regularFiles(directory)) files.push({ directory, path });
  }
  files.sort((left, right) => left.path.localeCompare(right.path));

  const cases = [];
  const rejections = [];
  for (const { directory, path } of files) {
    try {
      const raw = await readFile(path);
      const args = decodeMethodInput(raw, method);
      const mapped = typeof harness.toCorpusCase === "function"
        ? await harness.toCorpusCase(args)
        : { receiver: undefined, arguments: args };
      if (!mapped || !Array.isArray(mapped.arguments)) {
        throw new Error("toCorpusCase(args) must return { receiver?, arguments: [...] }");
      }
      const seen = new WeakSet();
      cases.push({
        id: `raw-${createHash("sha256").update(raw).digest("hex").slice(0, 20)}`,
        methodId,
        receiver: encodeValue(mapped.receiver, seen, "$receiver"),
        arguments: mapped.arguments.map((value, index) => encodeValue(value, seen, `$arguments[${index}]`)),
        metadata: {
          source: basename(directory),
          rawFile: basename(path),
          rawBytes: String(raw.length),
          sha256: createHash("sha256").update(raw).digest("hex"),
        },
      });
    } catch (error) {
      rejections.push({ path, reason: error instanceof Error ? error.message : String(error) });
    }
  }
  const corpus = { schemaVersion: 1, producer: "jazzer.js@4.0.0", cases };
  await writeFile(out, `${JSON.stringify(corpus, null, 2)}\n`, "utf8");
  return { discoveredFiles: files.length, exportedCases: cases.length, rejections };
}

async function regularFiles(root) {
  let entries;
  try {
    entries = await readdir(root, { withFileTypes: true });
  } catch (error) {
    if (error?.code === "ENOENT") return [];
    throw error;
  }
  const result = [];
  for (const entry of entries) {
    const path = join(root, entry.name);
    if (entry.isDirectory()) result.push(...await regularFiles(path));
    else if (entry.isFile()) result.push(path);
  }
  return result;
}

function encodeValue(value, seen, path) {
  if (value === undefined) return { kind: "undefined" };
  if (value === null) return { kind: "null" };
  if (typeof value === "number") return { kind: "number", value: encodeNumber(value) };
  if (typeof value === "boolean") return { kind: "boolean", value: String(value) };
  if (typeof value === "string") return { kind: "string", value };
  if (typeof value !== "object") return { kind: "unrepresentable", reason: `${path} has typeof ${typeof value}` };
  if (seen.has(value)) return { kind: "unrepresentable", reason: `${path} contains a cycle or shared alias` };
  seen.add(value);

  if (Array.isArray(value)) {
    const elements = [];
    for (let index = 0; index < value.length; index += 1) {
      elements.push(index in value ? encodeValue(value[index], seen, `${path}[${index}]`) : { kind: "hole" });
    }
    return { kind: "array", elements };
  }
  if (value instanceof Map) {
    return {
      kind: "map",
      entries: [...value].map(([key, entryValue], index) => ({
        key: encodeValue(key, seen, `${path}.map[${index}].key`),
        value: encodeValue(entryValue, seen, `${path}.map[${index}].value`),
      })),
    };
  }
  if (value instanceof Set) {
    return { kind: "set", elements: [...value].map((item, index) => encodeValue(item, seen, `${path}.set[${index}]`)) };
  }
  const prototype = Object.getPrototypeOf(value);
  if (prototype !== Object.prototype && prototype !== null) {
    return { kind: "unrepresentable", reason: `${path} is an instance of ${prototype?.constructor?.name ?? "unknown"}` };
  }
  if (Object.getOwnPropertySymbols(value).length > 0) {
    return { kind: "unrepresentable", reason: `${path} has symbol-keyed properties` };
  }
  const properties = [];
  for (const key of Object.keys(value)) {
    const descriptor = Object.getOwnPropertyDescriptor(value, key);
    if (descriptor?.get || descriptor?.set) {
      return { kind: "unrepresentable", reason: `${path}.${key} is an accessor property` };
    }
    properties.push({ key, value: encodeValue(descriptor?.value, seen, `${path}.${key}`) });
  }
  return {
    kind: "object",
    properties,
  };
}

function encodeNumber(value) {
  if (Number.isNaN(value)) return "NaN";
  if (value === Infinity) return "Infinity";
  if (value === -Infinity) return "-Infinity";
  if (Object.is(value, -0)) return "-0";
  return String(value);
}

module.exports = { exportRawCorpus };
