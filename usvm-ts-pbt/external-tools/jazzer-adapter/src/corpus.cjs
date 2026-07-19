"use strict";

const { createHash } = require("node:crypto");
const { readdir, readFile, writeFile } = require("node:fs/promises");
const { basename, join, relative } = require("node:path");
const { encodeEtcV2, normalizeInput, PRODUCER } = require("./etc-v2.cjs");
const { decodeMethodInvocation } = require("./type-decoder.cjs");

/**
 * Decode every persisted byte input and hand every successful case to the
 * unified Kotlin replay pipeline through canonical ETC-v2 JSONL. Native
 * coverage corpus membership is diagnostic only and never EtsIR coverage.
 */
async function exportRawCorpus({
  method,
  methodId,
  harness = {},
  directories,
  out,
  seed = "0",
  generatedAtMs = 0,
}) {
  const files = [];
  for (const descriptor of normalizeDirectories(directories)) {
    for (const path of await regularFiles(descriptor.path)) {
      files.push({ ...descriptor, file: path, relativePath: portablePath(relative(descriptor.path, path) || basename(path)) });
    }
  }
  files.sort((left, right) => left.kind.localeCompare(right.kind) || left.relativePath.localeCompare(right.relativePath));

  const cases = [];
  const rejections = [];
  const nativeClaims = [];
  for (const entry of files) {
    let sha256 = null;
    try {
      const raw = await readFile(entry.file);
      sha256 = createHash("sha256").update(raw).digest("hex");
      const invocation = decodeMethodInvocation(raw, method, harness);
      if (invocation.externalInput && invocation.receiver !== undefined && typeof harness.invokeCase !== "function") {
        throw new Error("receiver_requires_invokeCase: harness.invokeCase({ receiver, arguments }) is required");
      }
      const externalInput = invocation.externalInput ?? await externalizeLegacyInvocation(invocation, harness);
      const pathHash = createHash("sha256").update(`${entry.kind}\0${entry.relativePath}`).digest("hex").slice(0, 10);
      const testCase = {
        id: `raw-${sha256.slice(0, 20)}-${pathHash}`,
        methodId,
        generatedAtMs: Math.max(0, Math.trunc(generatedAtMs)),
        seed: String(seed),
        path: `${entry.kind}:${entry.relativePath}`,
        receiver: externalInput.receiver,
        arguments: externalInput.arguments,
        metadata: {
          corpusKind: entry.kind,
          rawFile: basename(entry.file),
          rawRelativePath: entry.relativePath,
          rawBytes: String(raw.length),
          sha256,
          decoderEncoding: invocation.encoding,
          replayDisposition: "scheduled",
        },
      };
      cases.push(testCase);
      if (entry.kind === "coverage") {
        nativeClaims.push({
          methodId,
          nativeTargetId: `libfuzzer-corpus:${sha256}:${pathHash}`,
          claimedCovered: true,
          discoveredAtMs: testCase.generatedAtMs,
        });
      }
    } catch (error) {
      rejections.push({
        corpusKind: entry.kind,
        rawRelativePath: entry.relativePath,
        sha256,
        reason: error instanceof Error ? error.message : String(error),
      });
    }
  }
  await writeFile(out, encodeEtcV2(cases), "utf8");
  const countsByKind = Object.fromEntries([...new Set(files.map((entry) => entry.kind))].sort().map((kind) => [
    kind,
    files.filter((entry) => entry.kind === kind).length,
  ]));
  return {
    discoveredCorpusEntries: files.length,
    generatedCases: cases.length,
    rejectedEntries: rejections.length,
    replayHandoffCases: cases.length,
    conservationOk: cases.length + rejections.length === files.length,
    countsByKind,
    rejections,
    nativeClaims,
  };
}

async function externalizeLegacyInvocation(invocation, harness) {
  const mapped = typeof harness.toCorpusCase === "function"
    ? await harness.toCorpusCase(invocation.arguments)
    : { receiver: invocation.receiver, arguments: invocation.arguments };
  if (!mapped || !Array.isArray(mapped.arguments)) {
    throw new Error("toCorpusCase(args) must return { receiver?, arguments: [...] }");
  }
  const state = { seen: new WeakMap(), nextAlias: 0, harness };
  return normalizeInput(
    encodeValue(mapped.receiver, state, "$receiver"),
    mapped.arguments.map((value, index) => encodeValue(value, state, `$arguments[${index}]`)),
  );
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
  for (const entry of entries.sort((left, right) => left.name.localeCompare(right.name))) {
    const path = join(root, entry.name);
    if (entry.isDirectory()) result.push(...await regularFiles(path));
    else if (entry.isFile()) result.push(path);
  }
  return result;
}

function normalizeDirectories(directories) {
  return (directories ?? []).filter(Boolean).map((entry) => typeof entry === "string"
    ? { path: entry, kind: basename(entry) || "coverage" }
    : { path: entry.path, kind: entry.kind });
}

function encodeValue(value, state, path) {
  if (value === undefined) return { kind: "undefined" };
  if (value === null) return { kind: "null" };
  if (typeof value === "number") return { kind: "number", value: encodeNumber(value) };
  if (typeof value === "boolean") return { kind: "boolean", value: String(value) };
  if (typeof value === "string") return { kind: "string", value };
  if (typeof value === "function") {
    const previous = state.seen.get(value);
    if (previous) return { kind: "alias", aliasReference: previous };
    const aliasId = `a${state.nextAlias++}`;
    state.seen.set(value, aliasId);
    if (typeof state.harness.externalizeCallable === "function") {
      const callableReference = state.harness.externalizeCallable(value, path);
      if (callableReference) return { kind: "callable", callableReference, aliasId };
    }
    return { ...unrepresentable(path, "function", "function has no source-level callable reference"), aliasId };
  }
  if (typeof value === "symbol") return unrepresentable(path, "symbol", "symbol values are not portable");
  if (typeof value !== "object") return unrepresentable(path, "other", `value has typeof ${typeof value}`);

  const previous = state.seen.get(value);
  if (previous) return { kind: "alias", aliasReference: previous };
  const aliasId = `a${state.nextAlias++}`;
  state.seen.set(value, aliasId);

  if (Array.isArray(value)) {
    const elements = [];
    for (let index = 0; index < value.length; index += 1) {
      elements.push(index in value ? encodeValue(value[index], state, `${path}[${index}]`) : { kind: "hole" });
    }
    return { kind: "array", aliasId, elements };
  }
  if (value instanceof Map) {
    return {
      kind: "map",
      aliasId,
      entries: [...value].map(([key, entryValue], index) => ({
        key: encodeValue(key, state, `${path}.map[${index}].key`),
        value: encodeValue(entryValue, state, `${path}.map[${index}].value`),
      })),
    };
  }
  if (value instanceof Set) {
    return {
      kind: "set",
      aliasId,
      elements: [...value].map((item, index) => encodeValue(item, state, `${path}.set[${index}]`)),
    };
  }
  const prototype = Object.getPrototypeOf(value);
  if (prototype !== Object.prototype && prototype !== null) {
    return { ...unrepresentable(path, "classInstance", `instance of ${prototype?.constructor?.name ?? "unknown"}`), aliasId };
  }
  if (Object.getOwnPropertySymbols(value).length > 0) {
    return { ...unrepresentable(path, "symbol", "object has symbol-keyed properties"), aliasId };
  }
  const properties = [];
  for (const key of Object.keys(value)) {
    const descriptor = Object.getOwnPropertyDescriptor(value, key);
    if (descriptor?.get || descriptor?.set) {
      return { ...unrepresentable(`${path}.${key}`, "accessor", "accessor properties are not portable"), aliasId };
    }
    properties.push({ key, value: encodeValue(descriptor?.value, state, `${path}.${key}`) });
  }
  return { kind: "object", aliasId, properties };
}

function unrepresentable(path, kind, detail) {
  return { kind: "unrepresentable", reason: `${path}: ${detail}`, unrepresentableKind: kind };
}

function encodeNumber(value) {
  if (Number.isNaN(value)) return "NaN";
  if (value === Infinity) return "Infinity";
  if (value === -Infinity) return "-Infinity";
  if (Object.is(value, -0)) return "-0";
  return String(value);
}

function portablePath(path) {
  return path.split("\\").join("/");
}

module.exports = { encodeValue, exportRawCorpus, regularFiles };
