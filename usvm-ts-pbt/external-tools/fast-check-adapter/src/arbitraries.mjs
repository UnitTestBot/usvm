import fc from "fast-check";

const EDGE_NUMBERS = [NaN, Infinity, -Infinity, -0, 0, 1, -1, Number.MAX_SAFE_INTEGER, Number.MIN_SAFE_INTEGER];
// Full-range doubles mostly produce huge non-integral magnitudes. Keep them for
// semantic edge discovery, but give ordinary program-sized numbers enough mass
// to exercise loops, indexes, and allocation guards in real code.
const numberArbitrary = fc.oneof(
  fc.integer({ min: -1_000, max: 1_000 }),
  fc.double({ min: -1_000, max: 1_000, noNaN: true }),
  fc.constantFrom(...EDGE_NUMBERS),
  fc.double(),
);
const primitiveArbitrary = fc.oneof(
  numberArbitrary,
  fc.boolean(),
  fc.string({ maxLength: 32 }),
  fc.constant(null),
  fc.constant(undefined),
);
const unknownArbitrary = fc.oneof(
  primitiveArbitrary,
  fc.array(primitiveArbitrary, { maxLength: 6 }),
  fc.dictionary(fc.string({ maxLength: 12 }), primitiveArbitrary, { maxKeys: 6 }),
);

export function arbitraryForMethod(method) {
  const parameters = method.parameters ?? method.parameterTypes.map((type, index) => ({
    index,
    name: `p${index}`,
    type,
    optional: false,
    rest: false,
  }));
  return fc.tuple(...parameters.map(arbitraryForParameter));
}

export function arbitraryForParameter(parameter) {
  let arbitrary = arbitraryForType(parameter.type);
  if (parameter.rest) arbitrary = fc.array(arbitrary, { maxLength: 8 });
  if (parameter.optional) arbitrary = fc.oneof(fc.constant(undefined), arbitrary);
  return arbitrary;
}

export function arbitraryForType(rawType) {
  const type = stripOuterParentheses(rawType.trim());
  const union = splitTopLevel(type, "|");
  if (union.length > 1) return fc.oneof(...union.map(arbitraryForType));

  if (type.endsWith("[]")) {
    return fc.array(arbitraryForType(type.slice(0, -2)), { maxLength: 8 });
  }
  const arrayMatch = /^(?:Array|ReadonlyArray)<(.+)>$/.exec(type);
  if (arrayMatch) return fc.array(arbitraryForType(arrayMatch[1]), { maxLength: 8 });

  if (type.startsWith("[") && type.endsWith("]")) {
    const items = splitTopLevel(type.slice(1, -1), ",");
    return fc.tuple(...items.map(arbitraryForType));
  }

  if (/^"(?:[^"\\]|\\.)*"$/.test(type)) return fc.constant(JSON.parse(type));
  if (type === "true") return fc.constant(true);
  if (type === "false") return fc.constant(false);
  if (/^[+-]?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][+-]?\d+)?$/.test(type)) return fc.constant(Number(type));

  switch (type) {
    case "number": return numberArbitrary;
    case "boolean": return fc.boolean();
    case "string": return fc.string({ maxLength: 64 });
    case "null": return fc.constant(null);
    case "undefined":
    case "void": return fc.constant(undefined);
    case "never": return fc.constant(undefined);
    case "any":
    case "unknown": return unknownArbitrary;
    default:
      // Class/reference layouts need a user-provided schema or harness. A
      // plain record is the least-assumptive automatically replayable seed.
      return fc.dictionary(fc.string({ maxLength: 12 }), primitiveArbitrary, { maxKeys: 6 });
  }
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
