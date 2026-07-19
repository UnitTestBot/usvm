"use strict";

const ETC_SCHEMA_VERSION = 2;
const PRODUCER_NAME = "jazzer.js";
const PRODUCER_VERSION = "4.0.0";
const PRODUCER = `${PRODUCER_NAME}@${PRODUCER_VERSION}`;
const ETC_ENVELOPE_MAGIC = Buffer.from("USVM-ETC-V2\0", "utf8");

const VALUE_KINDS = new Set([
  "undefined", "null", "number", "boolean", "string", "hole", "array", "object",
  "map", "set", "callable", "alias", "unrepresentable",
]);
const CALLABLE_KINDS = new Set(["function", "class", "staticMethod", "instanceMethod", "arrow"]);
const UNREPRESENTABLE_KINDS = new Set(["function", "cycle", "symbol", "accessor", "classInstance", "namespace", "other"]);
const SPECIAL_NUMBERS = new Set(["NaN", "Infinity", "-Infinity", "-0"]);
const DECIMAL_NUMBER = /^[+-]?(?:(?:\d+(?:\.\d*)?)|(?:\.\d+))(?:[eE][+-]?\d+)?$/;

function parseEtcV2(text, sourceName = "<memory>") {
  const lines = String(text).split(/\r?\n/).map((line) => line.trim()).filter(Boolean);
  if (lines.length === 0) throw new Error(`ETC ${sourceName} is empty`);
  const records = lines.map((line, index) => parseJson(line, `${sourceName}:${index + 1}`));
  const header = records.shift();
  if (!isPlainObject(header) || header.schemaVersion !== ETC_SCHEMA_VERSION || typeof header.producer !== "string" || !header.producer) {
    throw new Error(`ETC ${sourceName} must start with a schemaVersion 2 producer header`);
  }
  const ids = new Set();
  records.forEach((testCase, index) => {
    validateCase(testCase, `${sourceName}:${index + 2}`);
    if (ids.has(testCase.id)) throw new Error(`${sourceName}:${index + 2}: duplicate case id '${testCase.id}'`);
    ids.add(testCase.id);
  });
  return { schemaVersion: ETC_SCHEMA_VERSION, producer: header.producer, cases: records };
}

function encodeEtcV2(cases, producer = PRODUCER) {
  if (typeof producer !== "string" || !/^.+@.+$/.test(producer)) throw new Error("ETC producer must use name@version form");
  cases.forEach((testCase, index) => validateCase(testCase, `cases[${index}]`));
  return [JSON.stringify({ schemaVersion: ETC_SCHEMA_VERSION, producer }), ...cases.map(JSON.stringify)].join("\n") + "\n";
}

function validateCase(testCase, path = "case") {
  if (!isPlainObject(testCase)) throw new Error(`${path}: case must be an object`);
  if (typeof testCase.id !== "string" || !testCase.id) throw new Error(`${path}: id must be non-empty`);
  if (typeof testCase.methodId !== "string" || !testCase.methodId) throw new Error(`${path}: methodId must be non-empty`);
  if (!Number.isSafeInteger(testCase.generatedAtMs) || testCase.generatedAtMs < 0) {
    throw new Error(`${path}: generatedAtMs must be a non-negative integer`);
  }
  if ((typeof testCase.seed !== "string" || !testCase.seed) && (typeof testCase.path !== "string" || !testCase.path)) {
    throw new Error(`${path}: at least one of seed or path is required`);
  }
  if (!Array.isArray(testCase.arguments)) throw new Error(`${path}: arguments must be an array`);
  const aliases = { definitions: new Map(), references: [] };
  validateValue(testCase.receiver ?? { kind: "undefined" }, `${path}.receiver`, false, aliases, 0);
  testCase.arguments.forEach((value, index) => validateValue(value, `${path}.arguments[${index}]`, false, aliases, 0));
  for (const { id, path: referencePath } of aliases.references) {
    if (!aliases.definitions.has(id)) throw new Error(`${referencePath}: alias '${id}' is not defined in the case`);
  }
}

function validateValue(value, path, holeAllowed, aliases, depth) {
  if (depth > 256) throw new Error(`${path}: nesting exceeds 256`);
  if (!isPlainObject(value) || !VALUE_KINDS.has(value.kind)) throw new Error(`${path}: unknown ETC value kind '${value?.kind}'`);
  if (value.aliasId !== undefined) {
    if (typeof value.aliasId !== "string" || !value.aliasId) throw new Error(`${path}.aliasId must be non-empty`);
    if (aliases.definitions.has(value.aliasId)) throw new Error(`${path}: duplicate aliasId '${value.aliasId}'`);
    aliases.definitions.set(value.aliasId, value);
  }
  if (value.constructorPlan !== undefined) {
    if (value.kind !== "object") throw new Error(`${path}: constructorPlan is only valid for object values`);
    validateCallable(value.constructorPlan.callable, `${path}.constructorPlan.callable`);
    if (!Array.isArray(value.constructorPlan.arguments)) throw new Error(`${path}.constructorPlan.arguments must be an array`);
    value.constructorPlan.arguments.forEach((argument, index) =>
      validateValue(argument, `${path}.constructorPlan.arguments[${index}]`, false, aliases, depth + 1));
  }
  if (value.className !== undefined && (typeof value.className !== "string" || !value.className)) {
    throw new Error(`${path}.className must be a non-empty string when present`);
  }
  switch (value.kind) {
    case "undefined":
    case "null":
      if (value.value !== undefined) throw new Error(`${path}: ${value.kind} must not carry value`);
      break;
    case "number":
      if (typeof value.value !== "string" || (!SPECIAL_NUMBERS.has(value.value) && !DECIMAL_NUMBER.test(value.value))) {
        throw new Error(`${path}: invalid number token`);
      }
      break;
    case "boolean":
      if (value.value !== "true" && value.value !== "false") throw new Error(`${path}: invalid boolean token`);
      break;
    case "string":
      if (typeof value.value !== "string") throw new Error(`${path}: string value is required`);
      break;
    case "hole":
      if (!holeAllowed) throw new Error(`${path}: hole is only valid directly inside an array`);
      break;
    case "array":
    case "set":
      if (!Array.isArray(value.elements)) throw new Error(`${path}.elements must be an array`);
      value.elements.forEach((element, index) =>
        validateValue(element, `${path}.elements[${index}]`, value.kind === "array", aliases, depth + 1));
      break;
    case "object": {
      if (!Array.isArray(value.properties)) throw new Error(`${path}.properties must be an array`);
      const keys = new Set();
      value.properties.forEach((property, index) => {
        if (!isPlainObject(property) || typeof property.key !== "string") throw new Error(`${path}.properties[${index}] is invalid`);
        if (keys.has(property.key)) throw new Error(`${path}: duplicate property '${property.key}'`);
        keys.add(property.key);
        validateValue(property.value, `${path}.properties[${index}].value`, false, aliases, depth + 1);
      });
      break;
    }
    case "map":
      if (!Array.isArray(value.entries)) throw new Error(`${path}.entries must be an array`);
      value.entries.forEach((entry, index) => {
        if (!isPlainObject(entry)) throw new Error(`${path}.entries[${index}] is invalid`);
        validateValue(entry.key, `${path}.entries[${index}].key`, false, aliases, depth + 1);
        validateValue(entry.value, `${path}.entries[${index}].value`, false, aliases, depth + 1);
      });
      break;
    case "callable":
      validateCallable(value.callableReference, `${path}.callableReference`);
      break;
    case "alias":
      if (typeof value.aliasReference !== "string" || !value.aliasReference) throw new Error(`${path}.aliasReference is required`);
      if (value.aliasId !== undefined) throw new Error(`${path}: alias reference cannot define aliasId`);
      aliases.references.push({ id: value.aliasReference, path });
      break;
    case "unrepresentable":
      if (typeof value.reason !== "string" || !value.reason) throw new Error(`${path}.reason is required`);
      if (!UNREPRESENTABLE_KINDS.has(value.unrepresentableKind)) throw new Error(`${path}.unrepresentableKind is invalid`);
      break;
  }
}

function validateCallable(reference, path) {
  if (!isPlainObject(reference) || typeof reference.modulePath !== "string" || !reference.modulePath ||
      typeof reference.exportName !== "string" || !reference.exportName || !CALLABLE_KINDS.has(reference.callableKind)) {
    throw new Error(`${path}: invalid callable reference`);
  }
}

function encodeEnvelope(testCase) {
  validateCase(testCase, `case '${testCase?.id ?? "<unknown>"}'`);
  const input = normalizeInput(testCase.receiver ?? { kind: "undefined" }, testCase.arguments);
  return Buffer.concat([ETC_ENVELOPE_MAGIC, Buffer.from(JSON.stringify(input), "utf8")]);
}

function decodeEnvelope(buffer, hooks = {}) {
  const raw = Buffer.from(buffer);
  if (raw.length < ETC_ENVELOPE_MAGIC.length || !raw.subarray(0, ETC_ENVELOPE_MAGIC.length).equals(ETC_ENVELOPE_MAGIC)) return null;
  const input = parseJson(raw.subarray(ETC_ENVELOPE_MAGIC.length).toString("utf8"), "ETC v2 seed envelope");
  const synthetic = { id: "envelope", methodId: "envelope", generatedAtMs: 0, seed: "envelope", ...input };
  validateCase(synthetic, "ETC v2 seed envelope");
  return { externalInput: input, ...materializeInput(input, hooks) };
}

function materializeInput(input, hooks = {}) {
  const definitions = new Map();
  const collect = (value) => {
    if (value.aliasId) definitions.set(value.aliasId, value);
    for (const element of value.elements ?? []) collect(element);
    for (const property of value.properties ?? []) collect(property.value);
    for (const entry of value.entries ?? []) { collect(entry.key); collect(entry.value); }
    for (const argument of value.constructorPlan?.arguments ?? []) collect(argument);
  };
  collect(input.receiver);
  input.arguments.forEach(collect);
  const materialized = new Map();
  const active = new Set();

  const decode = (value, path) => {
    if (value.kind === "alias") {
      const target = definitions.get(value.aliasReference);
      if (!target) throw new Error(`unsupported_alias_reference:${value.aliasReference} at ${path}`);
      return decode(target, `${path}->${value.aliasReference}`);
    }
    if (value.aliasId && materialized.has(value.aliasId)) return materialized.get(value.aliasId);
    if (value.aliasId && active.has(value.aliasId)) {
      throw new Error(`unsupported_constructor_alias_cycle:${value.aliasId} at ${path}`);
    }
    switch (value.kind) {
      case "undefined": return undefined;
      case "null": return null;
      case "number": return decodeNumber(value.value);
      case "boolean": return value.value === "true";
      case "string": return value.value;
      case "hole": throw new Error(`invalid_top_level_hole at ${path}`);
      case "callable": {
        if (typeof hooks.materializeCallable !== "function") {
          throw new Error(`unsupported_callable:${renderCallable(value.callableReference)} at ${path}; harness.materializeCallable is required`);
        }
        const callable = hooks.materializeCallable(value.callableReference, path);
        if (typeof callable !== "function") throw new Error(`materializeCallable returned non-function at ${path}`);
        if (value.aliasId) materialized.set(value.aliasId, callable);
        return callable;
      }
      case "unrepresentable":
        throw new Error(`unrepresentable_${value.unrepresentableKind}:${value.reason} at ${path}`);
      case "array": {
        const result = new Array(value.elements.length);
        if (value.aliasId) materialized.set(value.aliasId, result);
        value.elements.forEach((element, index) => {
          if (element.kind !== "hole") result[index] = decode(element, `${path}[${index}]`);
        });
        return result;
      }
      case "map": {
        const result = new Map();
        if (value.aliasId) materialized.set(value.aliasId, result);
        value.entries.forEach((entry, index) => result.set(
          decode(entry.key, `${path}.entries[${index}].key`),
          decode(entry.value, `${path}.entries[${index}].value`),
        ));
        return result;
      }
      case "set": {
        const result = new Set();
        if (value.aliasId) materialized.set(value.aliasId, result);
        value.elements.forEach((element, index) => result.add(decode(element, `${path}.elements[${index}]`)));
        return result;
      }
      case "object": {
        let result;
        if (value.constructorPlan) {
          if (typeof hooks.materializeConstructorPlan !== "function") {
            throw new Error(`unsupported_constructor_plan:${renderCallable(value.constructorPlan.callable)} at ${path}; harness.materializeConstructorPlan is required`);
          }
          if (value.aliasId) active.add(value.aliasId);
          try {
            const planArguments = value.constructorPlan.arguments.map((argument, index) =>
              decode(argument, `${path}.constructorPlan.arguments[${index}]`));
            result = hooks.materializeConstructorPlan(value.constructorPlan.callable, planArguments, path);
          } finally {
            if (value.aliasId) active.delete(value.aliasId);
          }
          if ((typeof result !== "object" && typeof result !== "function") || result === null) {
            throw new Error(`materializeConstructorPlan returned non-object at ${path}`);
          }
        } else if (value.className) {
          if (typeof hooks.materializeClassName !== "function") {
            throw new Error(`unsupported_class_name:${value.className} at ${path}; harness.materializeClassName is required`);
          }
          result = hooks.materializeClassName(value.className, path);
          if ((typeof result !== "object" && typeof result !== "function") || result === null) {
            throw new Error(`materializeClassName returned non-object at ${path}`);
          }
        } else {
          result = Object.create(null);
        }
        if (value.aliasId) materialized.set(value.aliasId, result);
        if (value.aliasId) active.add(value.aliasId);
        value.properties.forEach((property, index) => {
          result[property.key] = decode(property.value, `${path}.properties[${index}].value`);
        });
        if (value.aliasId) active.delete(value.aliasId);
        return result;
      }
      default: throw new Error(`unsupported_value_kind:${value.kind} at ${path}`);
    }
  };
  return {
    receiver: decode(input.receiver, "$receiver"),
    arguments: input.arguments.map((argument, index) => decode(argument, `$arguments[${index}]`)),
  };
}

function normalizeInput(receiver, argumentsList) {
  return {
    receiver: normalizeValue(receiver),
    arguments: argumentsList.map(normalizeValue),
  };
}

function normalizeValue(value) {
  const result = { kind: value.kind };
  if (value.value !== undefined) result.value = value.value;
  if (value.elements !== undefined) result.elements = value.elements.map(normalizeValue);
  if (value.properties !== undefined) result.properties = value.properties.map((property) => ({ key: property.key, value: normalizeValue(property.value) }));
  if (value.entries !== undefined) result.entries = value.entries.map((entry) => ({ key: normalizeValue(entry.key), value: normalizeValue(entry.value) }));
  if (value.className !== undefined) result.className = value.className;
  if (value.reason !== undefined) result.reason = value.reason;
  if (value.unrepresentableKind !== undefined) result.unrepresentableKind = value.unrepresentableKind;
  if (value.aliasId !== undefined) result.aliasId = value.aliasId;
  if (value.aliasReference !== undefined) result.aliasReference = value.aliasReference;
  if (value.callableReference !== undefined) result.callableReference = { ...value.callableReference };
  if (value.constructorPlan !== undefined) result.constructorPlan = {
    callable: { ...value.constructorPlan.callable },
    arguments: value.constructorPlan.arguments.map(normalizeValue),
  };
  return result;
}

function decodeNumber(raw) {
  if (raw === "NaN") return NaN;
  if (raw === "Infinity") return Infinity;
  if (raw === "-Infinity") return -Infinity;
  if (raw === "-0") return -0;
  return Number(raw);
}

function renderCallable(reference) {
  return `${reference.modulePath}#${reference.exportName}:${reference.callableKind}`;
}

function parseJson(text, source) {
  try {
    return JSON.parse(text);
  } catch (error) {
    throw new Error(`${source}: ${error instanceof Error ? error.message : String(error)}`);
  }
}

function isPlainObject(value) {
  return value !== null && typeof value === "object" && !Array.isArray(value);
}

module.exports = {
  ETC_ENVELOPE_MAGIC,
  ETC_SCHEMA_VERSION,
  PRODUCER,
  PRODUCER_NAME,
  PRODUCER_VERSION,
  decodeEnvelope,
  encodeEnvelope,
  encodeEtcV2,
  materializeInput,
  normalizeInput,
  parseEtcV2,
  validateCase,
};
