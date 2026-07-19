const VALUE_KINDS = new Set([
  "undefined",
  "null",
  "number",
  "boolean",
  "string",
  "hole",
  "array",
  "object",
  "map",
  "set",
  "callable",
  "alias",
  "unrepresentable",
]);

const CALLABLE_KINDS = new Set(["function", "class", "staticMethod", "instanceMethod", "arrow"]);
const NUMBER_TOKEN = /^(?:NaN|Infinity|-Infinity|-0|-?(?:0|[1-9][0-9]*)(?:\.[0-9]+)?(?:[eE][+-]?[0-9]+)?)$/;

export class ValueEncodingError extends Error {
  constructor(path, message) {
    super(`${path}: ${message}`);
    this.name = "ValueEncodingError";
    this.path = path;
  }
}

export function encodeConcreteValues({ receiverPresent = false, receiver, arguments: args }) {
  if (!Array.isArray(args)) throw new ValueEncodingError("$arguments", "arguments must be an array");
  const encoder = new ConcreteEncoder();
  const result = {
    arguments: args.map((value, index) => encoder.encode(value, `$arguments[${index}]`, false)),
  };
  if (receiverPresent) result.receiver = encoder.encode(receiver, "$receiver", false);
  assertEncodedValues(result);
  return result;
}

export function assertEncodedValues({ receiver, arguments: args }) {
  if (!Array.isArray(args)) throw new ValueEncodingError("$arguments", "arguments must be an array");
  const state = { definitions: new Set(), references: new Set() };
  if (receiver !== undefined) assertEtcValue(receiver, "$receiver", false, state, 0);
  args.forEach((value, index) => assertEtcValue(value, `$arguments[${index}]`, false, state, 0));
  const missing = [...state.references].filter((alias) => !state.definitions.has(alias)).sort();
  if (missing.length > 0) throw new ValueEncodingError("$", `unknown alias reference '${missing[0]}'`);
}

export function assertEtcValue(value, path, holeAllowed = false, state = undefined, depth = 0) {
  const aliases = state ?? { definitions: new Set(), references: new Set() };
  if (depth > 256) throw new ValueEncodingError(path, "value nesting exceeds 256");
  if (!isRecord(value)) throw new ValueEncodingError(path, "ETC value must be an object");
  if (!VALUE_KINDS.has(value.kind)) throw new ValueEncodingError(`${path}.kind`, `unknown ETC kind '${value.kind}'`);

  if (value.aliasId !== undefined) {
    nonEmptyString(value.aliasId, `${path}.aliasId`);
    if (aliases.definitions.has(value.aliasId)) {
      throw new ValueEncodingError(`${path}.aliasId`, `duplicate alias definition '${value.aliasId}'`);
    }
    aliases.definitions.add(value.aliasId);
  }

  switch (value.kind) {
    case "undefined":
    case "null":
      break;
    case "number":
      if (typeof value.value !== "string" || !NUMBER_TOKEN.test(value.value)) {
        throw new ValueEncodingError(`${path}.value`, "invalid canonical number token");
      }
      break;
    case "boolean":
      if (value.value !== "true" && value.value !== "false") {
        throw new ValueEncodingError(`${path}.value`, "boolean must be 'true' or 'false'");
      }
      break;
    case "string":
      if (typeof value.value !== "string") throw new ValueEncodingError(`${path}.value`, "string value is required");
      break;
    case "hole":
      if (!holeAllowed) throw new ValueEncodingError(path, "hole is only valid as a direct array element");
      break;
    case "array":
      array(value.elements, `${path}.elements`).forEach((entry, index) =>
        assertEtcValue(entry, `${path}.elements[${index}]`, true, aliases, depth + 1));
      break;
    case "object": {
      const keys = new Set();
      array(value.properties, `${path}.properties`).forEach((property, index) => {
        if (!isRecord(property) || typeof property.key !== "string") {
          throw new ValueEncodingError(`${path}.properties[${index}]`, "property requires a string key");
        }
        if (keys.has(property.key)) {
          throw new ValueEncodingError(`${path}.properties[${index}].key`, `duplicate property '${property.key}'`);
        }
        keys.add(property.key);
        assertEtcValue(property.value, `${path}.properties[${index}].value`, false, aliases, depth + 1);
      });
      if (value.constructorPlan !== undefined) {
        assertCallableReference(value.constructorPlan?.callable, `${path}.constructorPlan.callable`);
        array(value.constructorPlan?.arguments, `${path}.constructorPlan.arguments`).forEach((argument, index) =>
          assertEtcValue(argument, `${path}.constructorPlan.arguments[${index}]`, false, aliases, depth + 1));
      }
      break;
    }
    case "map":
      array(value.entries, `${path}.entries`).forEach((entry, index) => {
        if (!isRecord(entry)) throw new ValueEncodingError(`${path}.entries[${index}]`, "map entry must be an object");
        assertEtcValue(entry.key, `${path}.entries[${index}].key`, false, aliases, depth + 1);
        assertEtcValue(entry.value, `${path}.entries[${index}].value`, false, aliases, depth + 1);
      });
      break;
    case "set":
      array(value.elements, `${path}.elements`).forEach((entry, index) =>
        assertEtcValue(entry, `${path}.elements[${index}]`, false, aliases, depth + 1));
      break;
    case "callable":
      assertCallableReference(value.callableReference, `${path}.callableReference`);
      break;
    case "alias":
      if (value.aliasId !== undefined) throw new ValueEncodingError(`${path}.aliasId`, "alias references cannot define aliasId");
      nonEmptyString(value.aliasReference, `${path}.aliasReference`);
      aliases.references.add(value.aliasReference);
      break;
    case "unrepresentable":
      throw new ValueEncodingError(path, `unrepresentable value rejected: ${value.reason ?? "missing reason"}`);
    default:
      throw new ValueEncodingError(`${path}.kind`, `unsupported ETC kind '${value.kind}'`);
  }
  return true;
}

class ConcreteEncoder {
  constructor() {
    this.aliases = new WeakMap();
    this.nextAlias = 1;
  }

  encode(value, path, holeAllowed) {
    if (value === undefined) return { kind: "undefined" };
    if (value === null) return { kind: "null" };
    if (typeof value === "number") return { kind: "number", value: encodeNumber(value) };
    if (typeof value === "boolean") return { kind: "boolean", value: String(value) };
    if (typeof value === "string") return { kind: "string", value };
    if (typeof value === "function") throw new ValueEncodingError(path, "arbitrary function values are not materializable");
    if (typeof value === "symbol") throw new ValueEncodingError(path, "symbol values are not materializable");
    if (typeof value === "bigint") throw new ValueEncodingError(path, "bigint values are outside ETC v2");
    if (typeof value !== "object") throw new ValueEncodingError(path, `unsupported typeof ${typeof value}`);

    const previous = this.aliases.get(value);
    if (previous !== undefined) return { kind: "alias", aliasReference: previous };
    const aliasId = `syntest-object-${this.nextAlias++}`;
    this.aliases.set(value, aliasId);

    if (Array.isArray(value)) {
      const elements = [];
      for (let index = 0; index < value.length; index += 1) {
        elements.push(index in value ? this.encode(value[index], `${path}[${index}]`, true) : { kind: "hole" });
      }
      return { kind: "array", aliasId, elements };
    }
    if (value instanceof Map) {
      return {
        kind: "map",
        aliasId,
        entries: [...value.entries()].map(([key, entryValue], index) => ({
          key: this.encode(key, `${path}.map[${index}].key`, false),
          value: this.encode(entryValue, `${path}.map[${index}].value`, false),
        })),
      };
    }
    if (value instanceof Set) {
      return {
        kind: "set",
        aliasId,
        elements: [...value.values()].map((entry, index) => this.encode(entry, `${path}.set[${index}]`, false)),
      };
    }

    const prototype = Object.getPrototypeOf(value);
    if (prototype !== Object.prototype && prototype !== null) {
      throw new ValueEncodingError(path, `class instance '${prototype?.constructor?.name ?? "unknown"}' needs a constructor plan`);
    }
    if (Object.getOwnPropertySymbols(value).length > 0) {
      throw new ValueEncodingError(path, "symbol-keyed properties are not materializable");
    }
    const properties = [];
    for (const key of Object.keys(value)) {
      const descriptor = Object.getOwnPropertyDescriptor(value, key);
      if (descriptor?.get || descriptor?.set) throw new ValueEncodingError(`${path}.${key}`, "accessor property is not materializable");
      properties.push({ key, value: this.encode(descriptor?.value, `${path}.${key}`, false) });
    }
    return { kind: "object", aliasId, properties };
  }
}

function encodeNumber(value) {
  if (Number.isNaN(value)) return "NaN";
  if (value === Infinity) return "Infinity";
  if (value === -Infinity) return "-Infinity";
  if (Object.is(value, -0)) return "-0";
  return String(value);
}

function assertCallableReference(value, path) {
  if (!isRecord(value)) throw new ValueEncodingError(path, "callable reference must be an object");
  nonEmptyString(value.modulePath, `${path}.modulePath`);
  nonEmptyString(value.exportName, `${path}.exportName`);
  if (!CALLABLE_KINDS.has(value.callableKind)) {
    throw new ValueEncodingError(`${path}.callableKind`, `unknown callable kind '${value.callableKind}'`);
  }
}

function array(value, path) {
  if (!Array.isArray(value)) throw new ValueEncodingError(path, "array is required");
  return value;
}

function nonEmptyString(value, path) {
  if (typeof value !== "string" || value.length === 0) throw new ValueEncodingError(path, "non-empty string is required");
}

function isRecord(value) {
  return value !== null && typeof value === "object" && !Array.isArray(value);
}
