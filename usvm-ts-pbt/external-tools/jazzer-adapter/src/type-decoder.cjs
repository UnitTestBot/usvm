"use strict";

const { decodeEnvelope, encodeEnvelope } = require("./etc-v2.cjs");

const EDGE_NUMBERS = [NaN, Infinity, -Infinity, -0, 0, 1, -1, Number.MAX_SAFE_INTEGER, Number.MIN_SAFE_INTEGER];

class ByteCursor {
  constructor(buffer) {
    this.buffer = Buffer.from(buffer);
    this.offset = 0;
  }

  byte() {
    const value = this.offset < this.buffer.length ? this.buffer[this.offset] : 0;
    this.offset += 1;
    return value;
  }

  bytes(count) {
    const result = Buffer.alloc(count);
    const available = Math.max(0, Math.min(count, this.buffer.length - this.offset));
    if (available > 0) this.buffer.copy(result, 0, this.offset, this.offset + available);
    this.offset += count;
    return result;
  }
}

function decodeMethodInput(buffer, method) {
  const cursor = new ByteCursor(buffer);
  const parameters = method.parameters ?? method.parameterTypes.map((type, index) => ({
    index,
    name: `p${index}`,
    type,
    optional: false,
    rest: false,
  }));
  return parameters.map((parameter) => {
    if (parameter.optional && cursor.byte() % 4 === 0) return undefined;
    if (parameter.rest) {
      const length = cursor.byte() % 9;
      return Array.from({ length }, () => decodeType(cursor, parameter.type));
    }
    return decodeType(cursor, parameter.type);
  });
}

/**
 * ETC-v2 imports use a self-describing envelope. Every other byte sequence is
 * decoded by the original 0.1 decoder unchanged, preserving old Jazzer corpus
 * meaning and therefore the frozen primitive baseline.
 */
function decodeMethodInvocation(buffer, method, hooks = {}) {
  const envelope = decodeEnvelope(buffer, hooks);
  if (envelope) {
    assertArity(envelope.arguments, method);
    return { ...envelope, encoding: "etc-v2-envelope" };
  }
  return {
    receiver: undefined,
    arguments: decodeMethodInput(buffer, method),
    externalInput: null,
    encoding: "legacy-typed-bytes",
  };
}

function encodeMethodInvocation(testCase, method, hooks = {}) {
  const raw = encodeEnvelope(testCase);
  // Reject a seed before it reaches Jazzer if the declared receiver/callable
  // plan cannot be materialized by this harness.
  const decoded = decodeMethodInvocation(raw, method, hooks);
  if (decoded.receiver !== undefined && typeof hooks.invokeCase !== "function") {
    throw new Error("receiver_requires_invokeCase: harness.invokeCase({ receiver, arguments }) is required");
  }
  return raw;
}

function assertArity(argumentsList, method) {
  const parameters = method.parameters ?? method.parameterTypes.map((type, index) => ({ index, type, rest: false }));
  if (argumentsList.length !== parameters.length) {
    throw new Error(`ETC v2 envelope expected ${parameters.length} arguments, got ${argumentsList.length}`);
  }
}

function decodeType(cursor, rawType) {
  const type = stripOuterParentheses(rawType.trim());
  const union = splitTopLevel(type, "|");
  if (union.length > 1) return decodeType(cursor, union[cursor.byte() % union.length]);

  if (type.endsWith("[]")) {
    const length = cursor.byte() % 9;
    return Array.from({ length }, () => decodeType(cursor, type.slice(0, -2)));
  }
  const arrayMatch = /^(?:Array|ReadonlyArray)<(.+)>$/.exec(type);
  if (arrayMatch) {
    const length = cursor.byte() % 9;
    return Array.from({ length }, () => decodeType(cursor, arrayMatch[1]));
  }
  if (type.startsWith("[") && type.endsWith("]")) {
    return splitTopLevel(type.slice(1, -1), ",").map((item) => decodeType(cursor, item));
  }

  if (/^"(?:[^"\\]|\\.)*"$/.test(type)) return JSON.parse(type);
  if (type === "true") return true;
  if (type === "false") return false;
  if (/^[+-]?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][+-]?\d+)?$/.test(type)) return Number(type);

  switch (type) {
    case "number": return decodeNumber(cursor);
    case "boolean": return cursor.byte() % 2 === 1;
    case "string": return decodeString(cursor);
    case "null": return null;
    case "undefined":
    case "void":
    case "never": return undefined;
    case "any":
    case "unknown": return decodeUnknown(cursor);
    default: return decodeObject(cursor);
  }
}

function decodeNumber(cursor) {
  switch (cursor.byte() % 3) {
    case 0: return cursor.bytes(4).readInt32LE(0);
    case 1: return cursor.bytes(8).readDoubleLE(0);
    default: return EDGE_NUMBERS[cursor.byte() % EDGE_NUMBERS.length];
  }
}

function decodeString(cursor) {
  const length = cursor.byte() % 65;
  return cursor.bytes(length).toString("utf8");
}

function decodeUnknown(cursor) {
  switch (cursor.byte() % 7) {
    case 0: return decodeNumber(cursor);
    case 1: return cursor.byte() % 2 === 1;
    case 2: return decodeString(cursor);
    case 3: return null;
    case 4: return undefined;
    case 5: {
      const length = cursor.byte() % 7;
      return Array.from({ length }, () => decodeUnknown(cursor));
    }
    default: return decodeObject(cursor);
  }
}

function decodeObject(cursor) {
  const result = {};
  const count = cursor.byte() % 5;
  for (let index = 0; index < count; index += 1) {
    result[decodeString(cursor)] = decodeUnknown(cursor);
  }
  return result;
}

function encodeMethodInput(externalArguments, method) {
  const parameters = method.parameters ?? method.parameterTypes.map((type, index) => ({
    index,
    name: `p${index}`,
    type,
    optional: false,
    rest: false,
  }));
  if (externalArguments.length !== parameters.length) {
    throw new Error(`expected ${parameters.length} arguments, got ${externalArguments.length}`);
  }
  const chunks = [];
  parameters.forEach((parameter, index) => {
    const value = externalArguments[index];
    if (parameter.optional) {
      if (value.kind === "undefined") {
        chunks.push(Buffer.from([0]));
        return;
      }
      chunks.push(Buffer.from([1]));
    }
    if (parameter.rest) {
      if (value.kind !== "array" || value.elements.length > 8) throw new Error(`rest parameter ${index} needs <=8 elements`);
      chunks.push(Buffer.from([value.elements.length]));
      value.elements.forEach((element) => chunks.push(...encodeType(element, parameter.type)));
    } else {
      chunks.push(...encodeType(value, parameter.type));
    }
  });
  return Buffer.concat(chunks);
}

function encodeType(value, rawType) {
  const type = stripOuterParentheses(rawType.trim());
  const union = splitTopLevel(type, "|");
  if (union.length > 1) {
    const index = union.findIndex((candidate) => acceptsKind(candidate, value.kind));
    if (index < 0) throw new Error(`value kind ${value.kind} does not match union ${type}`);
    return [Buffer.from([index]), ...encodeType(value, union[index])];
  }

  if (type.endsWith("[]")) return encodeArray(value, type.slice(0, -2));
  const arrayMatch = /^(?:Array|ReadonlyArray)<(.+)>$/.exec(type);
  if (arrayMatch) return encodeArray(value, arrayMatch[1]);
  if (type.startsWith("[") && type.endsWith("]")) {
    const items = splitTopLevel(type.slice(1, -1), ",");
    if (value.kind !== "array" || value.elements.length !== items.length) throw new Error(`tuple ${type} shape mismatch`);
    return value.elements.flatMap((item, index) => encodeType(item, items[index]));
  }

  if (/^"(?:[^"\\]|\\.)*"$/.test(type)) {
    const expected = JSON.parse(type);
    if (value.kind !== "string" || value.value !== expected) throw new Error(`expected literal ${type}`);
    return [];
  }
  if (type === "true" || type === "false") {
    if (value.kind !== "boolean" || value.value !== type) throw new Error(`expected literal ${type}`);
    return [];
  }
  if (isNumericLiteral(type)) {
    if (value.kind !== "number" || !Object.is(Number(value.value), Number(type))) throw new Error(`expected literal ${type}`);
    return [];
  }
  switch (type) {
    case "number": return encodeNumber(value);
    case "boolean": {
      if (value.kind !== "boolean" || (value.value !== "true" && value.value !== "false")) {
        throw new Error(`expected boolean, got ${value.kind}`);
      }
      return [Buffer.from([value.value === "true" ? 1 : 0])];
    }
    case "string": return encodeString(value);
    case "null":
      if (value.kind !== "null") throw new Error(`expected null, got ${value.kind}`);
      return [];
    case "undefined":
    case "void":
    case "never":
      if (value.kind !== "undefined") throw new Error(`expected undefined, got ${value.kind}`);
      return [];
    case "any":
    case "unknown": return encodeUnknown(value);
    default: return encodeObject(value);
  }
}

function encodeNumber(value) {
  if (value.kind !== "number") throw new Error(`expected number, got ${value.kind}`);
  const raw = value.value;
  const edgeIndex = ["NaN", "Infinity", "-Infinity", "-0", "0", "1", "-1"].indexOf(raw);
  if (edgeIndex >= 0) return [Buffer.from([2, edgeIndex])];
  const number = Number(raw);
  if (!Number.isFinite(number)) throw new Error(`invalid number ${raw}`);
  if (Number.isInteger(number) && number >= -2147483648 && number <= 2147483647) {
    const bytes = Buffer.alloc(4);
    bytes.writeInt32LE(number, 0);
    return [Buffer.from([0]), bytes];
  }
  const bytes = Buffer.alloc(8);
  bytes.writeDoubleLE(number, 0);
  return [Buffer.from([1]), bytes];
}

function encodeString(value) {
  if (value.kind !== "string") throw new Error(`expected string, got ${value.kind}`);
  const bytes = Buffer.from(value.value ?? "", "utf8");
  if (bytes.length > 64) throw new Error(`UTF-8 string is ${bytes.length} bytes, maximum is 64`);
  return [Buffer.from([bytes.length]), bytes];
}

function encodeArray(value, elementType) {
  if (value.kind !== "array" || value.elements.length > 8) throw new Error(`array needs <=8 elements`);
  if (value.elements.some((element) => element.kind === "hole")) throw new Error("array holes are not seed-encodable");
  return [Buffer.from([value.elements.length]), ...value.elements.flatMap((element) => encodeType(element, elementType))];
}

function encodeUnknown(value) {
  switch (value.kind) {
    case "number": return [Buffer.from([0]), ...encodeNumber(value)];
    case "boolean": return [Buffer.from([1, value.value === "true" ? 1 : 0])];
    case "string": return [Buffer.from([2]), ...encodeString(value)];
    case "null": return [Buffer.from([3])];
    case "undefined": return [Buffer.from([4])];
    case "array": {
      if (value.elements.length > 6) throw new Error("unknown array needs <=6 elements");
      return [Buffer.from([5, value.elements.length]), ...value.elements.flatMap(encodeUnknown)];
    }
    case "object": return [Buffer.from([6]), ...encodeObject(value)];
    default: throw new Error(`unknown value kind ${value.kind} is not seed-encodable`);
  }
}

function encodeObject(value) {
  if (value.kind !== "object" || value.className) throw new Error("expected a plain object");
  if (!Array.isArray(value.properties)) throw new Error("plain object properties must be an array");
  if (value.properties.length > 4) throw new Error("object needs <=4 properties");
  return [
    Buffer.from([value.properties.length]),
    ...value.properties.flatMap((property) => [
      ...encodeString({ kind: "string", value: property.key }),
      ...encodeUnknown(property.value),
    ]),
  ];
}

function acceptsKind(rawType, kind) {
  const type = stripOuterParentheses(rawType.trim());
  if (type.includes("|")) return splitTopLevel(type, "|").some((item) => acceptsKind(item, kind));
  if (type.endsWith("[]") || /^(?:Array|ReadonlyArray)</.test(type) || (type.startsWith("[") && type.endsWith("]"))) {
    return kind === "array";
  }
  if (/^"/.test(type)) return kind === "string";
  if (isNumericLiteral(type)) return kind === "number";
  if (type === "true" || type === "false" || type === "boolean") return kind === "boolean";
  if (type === "number" || type === "string" || type === "null" || type === "undefined") return kind === type;
  if (type === "void" || type === "never") return kind === "undefined";
  if (type === "any" || type === "unknown") return true;
  return kind === "object";
}

function isNumericLiteral(type) {
  return /^[+-]?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][+-]?\d+)?$/.test(type);
}

function stripOuterParentheses(value) {
  if (!value.startsWith("(") || !value.endsWith(")")) return value;
  let depth = 0;
  for (let index = 0; index < value.length; index += 1) {
    if (value[index] === "(") depth += 1;
    if (value[index] === ")") depth -= 1;
    if (depth === 0 && index !== value.length - 1) return value;
  }
  return stripOuterParentheses(value.slice(1, -1).trim());
}

function splitTopLevel(value, separator) {
  const result = [];
  let start = 0;
  let angle = 0;
  let square = 0;
  let round = 0;
  let quoted = false;
  let escaped = false;
  for (let index = 0; index < value.length; index += 1) {
    const char = value[index];
    if (quoted) {
      if (escaped) escaped = false;
      else if (char === "\\") escaped = true;
      else if (char === '"') quoted = false;
      continue;
    }
    if (char === '"') quoted = true;
    else if (char === "<") angle += 1;
    else if (char === ">") angle -= 1;
    else if (char === "[") square += 1;
    else if (char === "]") square -= 1;
    else if (char === "(") round += 1;
    else if (char === ")") round -= 1;
    else if (char === separator && angle === 0 && square === 0 && round === 0) {
      result.push(value.slice(start, index).trim());
      start = index + 1;
    }
  }
  result.push(value.slice(start).trim());
  return result.filter(Boolean);
}

module.exports = { decodeMethodInput, decodeMethodInvocation, encodeMethodInput, encodeMethodInvocation };
