import { readFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import {
  PRODUCER_LABEL,
  SCHEMA_VERSION,
  TOOL_COMMIT,
  TOOL_NAME,
  TOOL_VERSION,
} from "./constants.mjs";

const schemaDirectory = resolve(
  dirname(fileURLToPath(import.meta.url)),
  "../../../artifact-contract/v2",
);

let frozenContractPromise;

/**
 * Read the repository-owned contract instead of carrying an adapter-private
 * copy of the ETC schema. Runtime checks below intentionally cover only the
 * fields used by fast-check; the common Kotlin validator remains authoritative.
 */
export function loadFrozenContract() {
  frozenContractPromise ??= Promise.all([
    readSchema("external-test-corpus-record.schema.json"),
    readSchema("target-manifest.schema.json"),
    readSchema("source-target-record.schema.json"),
    readSchema("run-config.schema.json"),
  ]).then(([etc, targetManifest, sourceTarget, runConfig]) => {
    const schemaVersion = etc.$defs.header.properties.schemaVersion.const;
    if (schemaVersion !== SCHEMA_VERSION) {
      throw new Error(`adapter supports frozen ETC v${SCHEMA_VERSION}, repository has v${schemaVersion}`);
    }
    return Object.freeze({
      schemaVersion,
      producerPattern: new RegExp(etc.$defs.header.properties.producer.pattern),
      valueKinds: new Set(etc.$defs.value.oneOf.map((entry) => {
        const definition = entry.$ref.split("/").at(-1);
        const kind = etc.$defs[definition]?.allOf?.[1]?.properties?.kind?.const
          ?? etc.$defs[definition]?.allOf?.[0]?.properties?.kind?.const;
        if (typeof kind !== "string") throw new Error(`cannot derive ETC kind from ${entry.$ref}`);
        return kind;
      })),
      callableKinds: new Set(etc.$defs.callableReference.properties.callableKind.enum),
      unrepresentableKinds: new Set(etc.$defs.unrepresentableValue.allOf[1].properties.unrepresentableKind.enum),
      mappingStatuses: new Set(sourceTarget.properties.mappingStatus.enum),
      entryKinds: new Set(targetManifest.$defs.method.properties.entryKind.enum),
      cacheModes: new Set(runConfig.properties.cacheMode.enum),
    });
  });
  return frozenContractPromise;
}

export function validateRunConfig(config, contract) {
  requireObject(config, "run-config");
  requireVersion(config, "run-config");
  requireNonEmptyString(config.runId, "run-config.runId");
  validateProducer(config.adapter, "run-config.adapter");
  if (
    config.adapter.name !== TOOL_NAME
    || config.adapter.version !== TOOL_VERSION
    || config.adapter.commit !== TOOL_COMMIT
  ) {
    throw new Error(
      `run-config.adapter must be ${PRODUCER_LABEL} at audited commit ${TOOL_COMMIT}`,
    );
  }
  requireInteger(config.seed, "run-config.seed", 0, 0xffff_ffff);
  requireInteger(config.budgetMs, "run-config.budgetMs", 1);
  const expectedGrace = Math.min(5_000, Math.max(1_000, Math.floor(config.budgetMs / 10)));
  requireInteger(config.exportReplayGraceMs, "run-config.exportReplayGraceMs", 1);
  if (config.exportReplayGraceMs !== expectedGrace) {
    throw new Error(`run-config.exportReplayGraceMs must be ${expectedGrace}`);
  }
  if (config.budgetMs <= config.exportReplayGraceMs) {
    throw new Error("run-config.budgetMs must exceed exportReplayGraceMs");
  }
  if (config.explorationDeadlineMs !== config.budgetMs - config.exportReplayGraceMs) {
    throw new Error("run-config.explorationDeadlineMs must equal budgetMs - exportReplayGraceMs");
  }
  if (config.hardResultDeadlineMs !== config.budgetMs) {
    throw new Error("run-config.hardResultDeadlineMs must equal budgetMs");
  }
  if (!contract.cacheModes.has(config.cacheMode)) {
    throw new Error(`run-config.cacheMode '${config.cacheMode}' is not in the frozen schema`);
  }
  requireStringMap(config.versions, "run-config.versions");
  requireStringMap(config.commits, "run-config.commits");
  requireObject(config.flags, "run-config.flags");
  return config;
}

export function makeInlineRunConfig(options) {
  const seed = requireInteger(options.seed, "--seed", 0, 0xffff_ffff);
  const budgetMs = requireInteger(options.budgetMs, "--budget-ms", 1);
  const expectedGrace = Math.min(5_000, Math.max(1_000, Math.floor(budgetMs / 10)));
  const exportReplayGraceMs = requireInteger(
    options.exportReplayGraceMs,
    "--export-replay-grace-ms",
    1,
  );
  if (exportReplayGraceMs !== expectedGrace || budgetMs <= exportReplayGraceMs) {
    throw new Error(
      `--export-replay-grace-ms must be ${expectedGrace} and smaller than --budget-ms`,
    );
  }
  return {
    schemaVersion: SCHEMA_VERSION,
    runId: options.runId ?? `${TOOL_NAME}-${seed}-${budgetMs}`,
    adapter: producerIdentity(),
    seed,
    budgetMs,
    exportReplayGraceMs,
    explorationDeadlineMs: budgetMs - exportReplayGraceMs,
    hardResultDeadlineMs: budgetMs,
    cacheMode: options.cacheMode ?? "cold",
    versions: { node: process.versions.node, fastCheck: TOOL_VERSION },
    commits: { fastCheck: TOOL_COMMIT },
    flags: {},
  };
}

export function validateTargetManifest(manifest, contract) {
  requireObject(manifest, "target-manifest");
  requireVersion(manifest, "target-manifest");
  requireNonEmptyString(manifest.generator, "target-manifest.generator");
  if (!Array.isArray(manifest.methods)) throw new Error("target-manifest.methods must be an array");
  const ids = new Set();
  for (const [index, method] of manifest.methods.entries()) {
    requireObject(method, `target-manifest.methods[${index}]`);
    requireNonEmptyString(method.methodId, `target-manifest.methods[${index}].methodId`);
    if (ids.has(method.methodId)) throw new Error(`duplicate methodId '${method.methodId}'`);
    ids.add(method.methodId);
    if (!contract.entryKinds.has(method.entryKind)) {
      throw new Error(`method '${method.methodId}' has unsupported entryKind '${method.entryKind}'`);
    }
    if (!Array.isArray(method.parameters) || !Array.isArray(method.parameterTypes)) {
      throw new Error(`method '${method.methodId}' has no parameter schema`);
    }
    if (method.arity !== method.parameters.length || method.arity !== method.parameterTypes.length) {
      throw new Error(`method '${method.methodId}' has inconsistent arity`);
    }
    if (!Array.isArray(method.branches)) throw new Error(`method '${method.methodId}' has no branches array`);
  }
  return manifest;
}

export function parseSourceTargets(text, contract) {
  const records = parseJsonLines(text, "source-targets");
  if (records.length === 0) throw new Error("source-targets must contain at least one edge");
  const identities = new Set();
  for (const [index, record] of records.entries()) {
    requireObject(record, `source-targets[${index + 1}]`);
    requireVersion(record, `source-targets[${index + 1}]`);
    requireNonEmptyString(record.methodId, `source-targets[${index + 1}].methodId`);
    requireNonEmptyString(record.branchId, `source-targets[${index + 1}].branchId`);
    if (!contract.mappingStatuses.has(record.mappingStatus)) {
      throw new Error(`source-target '${record.branchId}' has unknown mappingStatus '${record.mappingStatus}'`);
    }
    const identity = `${record.methodId}\u0000${record.branchId}`;
    if (identities.has(identity)) throw new Error(`duplicate source target '${record.branchId}'`);
    identities.add(identity);
  }
  return records;
}

export function parseMethodIds(text, manifest) {
  const ids = text.split(/\r?\n/u).map((line) => line.trim()).filter(Boolean);
  if (ids.length === 0) throw new Error("method-ids must contain at least one methodId");
  if (new Set(ids).size !== ids.length) throw new Error("method-ids contains a duplicate methodId");
  const methodsById = new Map(manifest.methods.map((method) => [method.methodId, method]));
  for (const id of ids) {
    if (!methodsById.has(id)) throw new Error(`methodId '${id}' is absent from target-manifest`);
  }
  return ids;
}

export function validateDenominator(manifest, sourceTargets, methodIds) {
  const methods = new Map(manifest.methods.map((method) => [method.methodId, method]));
  const sourceByMethod = Map.groupBy
    ? Map.groupBy(sourceTargets, (record) => record.methodId)
    : groupBy(sourceTargets, (record) => record.methodId);
  for (const methodId of methodIds) {
    const expected = new Set(methods.get(methodId).branches.map((branch) => branch.branchId));
    const actual = new Set((sourceByMethod.get(methodId) ?? []).map((record) => record.branchId));
    for (const branchId of expected) {
      if (!actual.has(branchId)) throw new Error(`selected branch '${branchId}' is absent from source-targets`);
    }
    for (const branchId of actual) {
      if (!expected.has(branchId)) throw new Error(`source target '${branchId}' is absent from target-manifest`);
    }
  }
}

export function producerIdentity() {
  return { name: TOOL_NAME, version: TOOL_VERSION, commit: TOOL_COMMIT };
}

export function readJson(text, name) {
  let value;
  try {
    value = JSON.parse(text);
  } catch (error) {
    throw new Error(`${name} is not valid JSON: ${firstLine(error)}`);
  }
  return value;
}

export function requireVersion(value, name) {
  if (value.schemaVersion !== SCHEMA_VERSION) {
    throw new Error(`${name} must use schemaVersion ${SCHEMA_VERSION}`);
  }
}

function parseJsonLines(text, name) {
  return text.split(/\r?\n/u).map((line) => line.trim()).filter(Boolean).map((line, index) => {
    try {
      return JSON.parse(line);
    } catch (error) {
      throw new Error(`${name} line ${index + 1} is not valid JSON: ${firstLine(error)}`);
    }
  });
}

function requireObject(value, name) {
  if (value === null || typeof value !== "object" || Array.isArray(value)) {
    throw new Error(`${name} must be a JSON object`);
  }
  return value;
}

function validateProducer(producer, name) {
  requireObject(producer, name);
  requireNonEmptyString(producer.name, `${name}.name`);
  requireNonEmptyString(producer.version, `${name}.version`);
  if (producer.commit !== undefined && producer.commit !== null) {
    requireNonEmptyString(producer.commit, `${name}.commit`);
  }
}

function requireNonEmptyString(value, name) {
  if (typeof value !== "string" || value.length === 0) throw new Error(`${name} must be non-empty`);
  return value;
}

function requireInteger(value, name, minimum, maximum = Number.MAX_SAFE_INTEGER) {
  if (!Number.isSafeInteger(value) || value < minimum || value > maximum) {
    throw new Error(`${name} must be an integer in ${minimum}..${maximum}`);
  }
  return value;
}

function requireStringMap(value, name) {
  requireObject(value, name);
  if (Object.keys(value).length === 0) throw new Error(`${name} must not be empty`);
  for (const [key, entry] of Object.entries(value)) {
    if (key.length === 0 || typeof entry !== "string" || entry.length === 0) {
      throw new Error(`${name} must contain non-empty string keys and values`);
    }
  }
}

async function readSchema(name) {
  const path = resolve(schemaDirectory, name);
  return readJson(await readFile(path, "utf8"), path);
}

function groupBy(items, key) {
  const groups = new Map();
  for (const item of items) {
    const group = key(item);
    const values = groups.get(group) ?? [];
    values.push(item);
    groups.set(group, values);
  }
  return groups;
}

function firstLine(error) {
  return (error instanceof Error ? error.message : String(error)).split("\n", 1)[0];
}
