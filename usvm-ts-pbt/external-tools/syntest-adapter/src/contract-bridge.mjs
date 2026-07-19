import { spawnSync } from "node:child_process";
import { readFile, readdir, stat } from "node:fs/promises";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { RAW_RUN_FILES, UPSTREAM_COMMIT } from "./constants.mjs";
import { assertEncodedValues } from "./value-codec.mjs";

const HERE = dirname(fileURLToPath(import.meta.url));
export const CONTRACT_ROOT = resolve(HERE, "../../../artifact-contract/v2");
const SCHEMAS = Object.freeze([
  "target-manifest.schema.json",
  "source-target-record.schema.json",
  "run-config.schema.json",
  "external-test-corpus-record.schema.json",
  "native-coverage.schema.json",
  "run-meta.schema.json",
]);

export async function loadAdapterInputs(paths) {
  await assertCanonicalSchemasPresent();
  const [manifest, sourceTargets, methodIds, runConfig] = await Promise.all([
    readJson(paths.targetManifest),
    readJsonl(paths.sourceTargets),
    readMethodIds(paths.methodIds),
    readJson(paths.runConfig),
  ]);
  validateTargetManifest(manifest);
  validateSourceTargets(sourceTargets);
  validateRunConfig(runConfig);
  return { manifest, sourceTargets, methodIds, runConfig };
}

export async function assertCanonicalSchemasPresent() {
  await Promise.all(SCHEMAS.map(async (name) => {
    const schema = await readJson(join(CONTRACT_ROOT, name));
    if (schema?.$schema !== "https://json-schema.org/draft/2020-12/schema") {
      throw new Error(`canonical schema '${name}' is not draft 2020-12`);
    }
    if (typeof schema?.$id !== "string" || !schema.$id.includes("/artifact-contract/v2/")) {
      throw new Error(`canonical schema '${name}' has an unexpected $id`);
    }
  }));
}

export function validateTargetManifest(manifest) {
  record(manifest, "target manifest");
  version(manifest, "target manifest");
  nonEmptyString(manifest.generator, "target manifest.generator");
  array(manifest.methods, "target manifest.methods");
  const methodIds = new Set();
  const branchIds = new Set();
  for (const [methodIndex, method] of manifest.methods.entries()) {
    const path = `target manifest.methods[${methodIndex}]`;
    record(method, path);
    nonEmptyString(method.methodId, `${path}.methodId`);
    if (methodIds.has(method.methodId)) throw new Error(`${path}.methodId is duplicated`);
    methodIds.add(method.methodId);
    for (const field of ["signature", "projectName", "fileName", "className", "methodName"]) {
      nonEmptyString(method[field], `${path}.${field}`);
    }
    if (!Number.isInteger(method.arity) || method.arity < 0) throw new Error(`${path}.arity must be non-negative`);
    if (!new Set(["free", "static", "instance"]).has(method.entryKind)) {
      throw new Error(`${path}.entryKind is not a closed v2 value`);
    }
    array(method.parameterTypes, `${path}.parameterTypes`);
    array(method.parameters, `${path}.parameters`);
    if (method.arity !== method.parameterTypes.length || method.arity !== method.parameters.length) {
      throw new Error(`${path} has inconsistent arity`);
    }
    method.parameterTypes.forEach((type, index) => nonEmptyString(type, `${path}.parameterTypes[${index}]`));
    method.parameters.forEach((parameter, index) => {
      record(parameter, `${path}.parameters[${index}]`);
      if (parameter.index !== index || parameter.type !== method.parameterTypes[index]) {
        throw new Error(`${path}.parameters[${index}] does not match parameterTypes`);
      }
      nonEmptyString(parameter.name, `${path}.parameters[${index}].name`);
      if (typeof parameter.optional !== "boolean" || typeof parameter.rest !== "boolean") {
        throw new Error(`${path}.parameters[${index}] requires boolean optional/rest`);
      }
    });
    array(method.branches, `${path}.branches`);
    method.branches.forEach((branch, branchIndex) => {
      const branchPath = `${path}.branches[${branchIndex}]`;
      record(branch, branchPath);
      nonEmptyString(branch.branchId, `${branchPath}.branchId`);
      if (!branch.branchId.startsWith(`${method.methodId}#`)) throw new Error(`${branchPath}.branchId is not method-scoped`);
      if (branchIds.has(branch.branchId)) throw new Error(`${branchPath}.branchId is duplicated`);
      branchIds.add(branch.branchId);
      for (const field of ["ifStmtIndex", "successorStmtIndex"]) nonNegativeInteger(branch[field], `${branchPath}.${field}`);
      if (branch.successorOrdinal !== 0 && branch.successorOrdinal !== 1) {
        throw new Error(`${branchPath}.successorOrdinal must be 0 or 1`);
      }
    });
  }
}

export function validateSourceTargets(records) {
  if (records.length === 0) throw new Error("source-targets.jsonl is empty");
  const identities = new Set();
  const mappingStatuses = new Set(["exact", "oneToMany", "ambiguous", "unmapped", "synthetic"]);
  const callableKinds = new Set(["free", "static", "instance", "constructor", "arrow", "synthetic"]);
  records.forEach((entry, index) => {
    const path = `source-targets[${index}]`;
    record(entry, path);
    version(entry, path);
    nonEmptyString(entry.methodId, `${path}.methodId`);
    nonEmptyString(entry.branchId, `${path}.branchId`);
    const identity = `${entry.methodId}\0${entry.branchId}`;
    if (identities.has(identity)) throw new Error(`${path} duplicates a method/branch identity`);
    identities.add(identity);
    for (const field of ["stmtIndex", "successorStmtIndex", "successorOrdinal"]) {
      nonNegativeInteger(entry[field], `${path}.${field}`);
    }
    validateRange(entry.tsSourceRange, `${path}.tsSourceRange`);
    record(entry.sourceOrigin, `${path}.sourceOrigin`);
    nonEmptyString(entry.sourceOrigin.modulePath, `${path}.sourceOrigin.modulePath`);
    nonEmptyString(entry.sourceOrigin.callableName, `${path}.sourceOrigin.callableName`);
    if (!callableKinds.has(entry.sourceOrigin.callableKind)) throw new Error(`${path}.sourceOrigin.callableKind is unknown`);
    if (!mappingStatuses.has(entry.mappingStatus)) throw new Error(`${path}.mappingStatus is unknown`);
  });
}

export function validateRunConfig(config) {
  record(config, "run config");
  version(config, "run config");
  nonEmptyString(config.runId, "run config.runId");
  validateProducer(config.adapter, "run config.adapter");
  if (config.adapter.commit !== UPSTREAM_COMMIT) {
    throw new Error(`run config.adapter.commit must pin SynTest-JavaScript ${UPSTREAM_COMMIT}`);
  }
  if (!Number.isInteger(config.seed) || config.seed < 0 || config.seed > 0xffff_ffff) {
    throw new Error("run config.seed must be an unsigned 32-bit integer");
  }
  if (!Number.isInteger(config.budgetMs) || config.budgetMs <= 0) throw new Error("run config.budgetMs must be positive");
  const grace = Math.min(5000, Math.max(1000, Math.floor(config.budgetMs / 10)));
  if (config.exportReplayGraceMs !== grace || config.explorationDeadlineMs !== config.budgetMs - grace
      || config.hardResultDeadlineMs !== config.budgetMs) {
    throw new Error("run config deadlines do not satisfy canonical v2 arithmetic");
  }
  if (config.cacheMode !== "cold" && config.cacheMode !== "warm") throw new Error("run config.cacheMode is unknown");
  stringMap(config.versions, "run config.versions");
  stringMap(config.commits, "run config.commits");
  record(config.flags, "run config.flags");
}

export async function readEtcCorpus(path) {
  const lines = await readJsonl(path);
  if (lines.length === 0) throw new Error(`${path}: ETC JSONL is empty`);
  const [header, ...cases] = lines;
  record(header, "ETC header");
  version(header, "ETC header");
  nonEmptyString(header.producer, "ETC header.producer");
  if (!/^[^@]+@[^@]+$/.test(header.producer)) throw new Error("ETC header.producer must be name@version");
  const ids = new Set();
  cases.forEach((entry, index) => {
    const pathName = `ETC cases[${index}]`;
    record(entry, pathName);
    nonEmptyString(entry.id, `${pathName}.id`);
    if (ids.has(entry.id)) throw new Error(`${pathName}.id is duplicated`);
    ids.add(entry.id);
    nonEmptyString(entry.methodId, `${pathName}.methodId`);
    nonNegativeInteger(entry.generatedAtMs, `${pathName}.generatedAtMs`);
    if (!nonBlank(entry.seed) && !nonBlank(entry.path)) throw new Error(`${pathName} requires seed or path`);
    assertEncodedValues({ receiver: entry.receiver, arguments: entry.arguments });
  });
  return { producer: header.producer, cases };
}

export async function validateRawRunLocal(outDir) {
  const names = (await readdir(outDir)).sort();
  const expected = [...RAW_RUN_FILES].sort();
  if (JSON.stringify(names) !== JSON.stringify(expected)) {
    throw new Error(`raw run must contain exactly ${expected.join(", ")}; got ${names.join(", ")}`);
  }
  const corpus = await readEtcCorpus(join(outDir, "corpus.etc.jsonl"));
  const native = await readJson(join(outDir, "native-coverage.json"));
  const meta = await readJson(join(outDir, "run-meta.json"));
  version(native, "native coverage");
  version(meta, "run meta");
  validateProducer(native.producer, "native coverage.producer");
  validateProducer(meta.producer, "run meta.producer");
  if (JSON.stringify(native.producer) !== JSON.stringify(meta.producer)) throw new Error("raw run producers differ");
  if (corpus.producer !== `${meta.producer.name}@${meta.producer.version}`) throw new Error("ETC producer differs from run meta");
  array(native.claims, "native coverage.claims");
  const claimIds = new Set();
  for (const [index, claim] of native.claims.entries()) {
    const path = `native coverage.claims[${index}]`;
    nonEmptyString(claim.methodId, `${path}.methodId`);
    nonEmptyString(claim.nativeTargetId, `${path}.nativeTargetId`);
    if (typeof claim.claimedCovered !== "boolean") throw new Error(`${path}.claimedCovered must be boolean`);
    const identity = `${claim.methodId}\0${claim.nativeTargetId}`;
    if (claimIds.has(identity)) throw new Error(`${path} is duplicated`);
    claimIds.add(identity);
  }
  for (const field of ["startupMs", "generationMs", "exportMs", "totalMs", "overBudgetMs"]) {
    nonNegativeInteger(meta[field], `run meta.${field}`);
  }
  if (meta.startupMs + meta.generationMs + meta.exportMs > meta.totalMs) throw new Error("run meta timing is inconsistent");
  const exitStatuses = new Set(["success", "unsupported_configuration", "tool_failure", "timeout_partial_corpus"]);
  if (!exitStatuses.has(meta.exitStatus)) throw new Error("run meta exitStatus is unknown");
  if (meta.timedOut !== (meta.exitStatus === "timeout_partial_corpus")) throw new Error("run meta timedOut is inconsistent");
  stringMap(meta.commits, "run meta.commits");
  if (!Number.isInteger(meta.logCapBytes) || meta.logCapBytes <= 0) throw new Error("run meta.logCapBytes must be positive");
  const logSize = (await stat(join(outDir, "stderr.log"))).size;
  if (logSize > meta.logCapBytes) throw new Error("stderr.log exceeds run meta.logCapBytes");
  if (corpus.cases.some((entry) => entry.generatedAtMs > meta.totalMs)) throw new Error("ETC case time exceeds run totalMs");
  return { valid: true, cases: corpus.cases.length, claims: native.claims.length };
}

export function commonValidatorBridge(command) {
  if (command === undefined || command === null) {
    return { validateRawRun: async () => ({ status: "deferred-a-int", invoked: false }) };
  }
  if (!Array.isArray(command) || command.length === 0 || command.some((part) => typeof part !== "string" || part.length === 0)) {
    throw new Error("common artifact validator command must be a non-empty string array");
  }
  return {
    validateRawRun: async (outDir) => {
      const result = spawnSync(command[0], [...command.slice(1), "validate", "raw-run", resolve(outDir)], {
        encoding: "utf8",
        stdio: ["ignore", "pipe", "pipe"],
        maxBuffer: 16 * 1024 * 1024,
      });
      if (result.error) throw result.error;
      if (result.status !== 0) {
        throw new Error(`common ArtifactValidator rejected raw run: ${(result.stderr || result.stdout).trim()}`);
      }
      return { status: "pass", invoked: true, stdout: result.stdout.trim() };
    },
  };
}

async function readJson(path) {
  const text = await readFile(path, "utf8");
  try {
    return JSON.parse(text);
  } catch (error) {
    throw new Error(`${path}: invalid JSON: ${error.message}`);
  }
}

async function readJsonl(path) {
  const text = await readFile(path, "utf8");
  return text.split(/\r?\n/).filter((line) => line.trim().length > 0).map((line, index) => {
    try {
      return JSON.parse(line);
    } catch (error) {
      throw new Error(`${path}:${index + 1}: invalid JSON: ${error.message}`);
    }
  });
}

async function readMethodIds(path) {
  const text = await readFile(path, "utf8");
  const lines = text.split(/\r?\n/);
  if (lines.at(-1) === "") lines.pop();
  if (lines.length === 0) throw new Error(`${path}: method-ids.txt is empty`);
  const seen = new Set();
  for (const [index, methodId] of lines.entries()) {
    if (methodId.length === 0 || methodId.trim() !== methodId) throw new Error(`${path}:${index + 1}: invalid method ID`);
    if (seen.has(methodId)) throw new Error(`${path}:${index + 1}: duplicate method ID`);
    seen.add(methodId);
  }
  return lines;
}

function validateProducer(value, path) {
  record(value, path);
  nonEmptyString(value.name, `${path}.name`);
  nonEmptyString(value.version, `${path}.version`);
  if (value.commit !== undefined) nonEmptyString(value.commit, `${path}.commit`);
}

function validateRange(value, path) {
  record(value, path);
  nonEmptyString(value.fileName, `${path}.fileName`);
  for (const field of ["startOffset", "endOffset", "startLine", "startColumn", "endLine", "endColumn"]) {
    nonNegativeInteger(value[field], `${path}.${field}`);
  }
  if (value.endOffset < value.startOffset || value.endLine < value.startLine
      || (value.endLine === value.startLine && value.endColumn < value.startColumn)) {
    throw new Error(`${path} is not ordered`);
  }
}

function stringMap(value, path) {
  record(value, path);
  const entries = Object.entries(value);
  if (entries.length === 0) throw new Error(`${path} must not be empty`);
  entries.forEach(([key, entry]) => {
    nonEmptyString(key, `${path} key`);
    nonEmptyString(entry, `${path}.${key}`);
  });
}

function version(value, path) {
  if (value.schemaVersion !== 2) throw new Error(`${path}.schemaVersion must be integer 2`);
}

function record(value, path) {
  if (value === null || typeof value !== "object" || Array.isArray(value)) throw new Error(`${path} must be an object`);
  return value;
}

function array(value, path) {
  if (!Array.isArray(value)) throw new Error(`${path} must be an array`);
  return value;
}

function nonNegativeInteger(value, path) {
  if (!Number.isInteger(value) || value < 0) throw new Error(`${path} must be a non-negative integer`);
}

function nonEmptyString(value, path) {
  if (!nonBlank(value)) throw new Error(`${path} must be a non-empty string`);
}

function nonBlank(value) {
  return typeof value === "string" && value.length > 0;
}
