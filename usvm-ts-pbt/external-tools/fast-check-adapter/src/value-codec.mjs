export function encodeInput(receiver, args) {
  const seen = new WeakSet();
  return {
    receiver: encodeValue(receiver, seen, "$receiver"),
    arguments: args.map((value, index) => encodeValue(value, seen, `$arguments[${index}]`)),
  };
}

export function encodeValue(value, seen = new WeakSet(), path = "$") {
  if (value === undefined) return { kind: "undefined" };
  if (value === null) return { kind: "null" };

  switch (typeof value) {
    case "number":
      return { kind: "number", value: encodeNumber(value) };
    case "boolean":
      return { kind: "boolean", value: String(value) };
    case "string":
      return { kind: "string", value };
    case "bigint":
      return unrepresentable(`${path} is bigint ${value}`);
    case "symbol":
      return unrepresentable(`${path} is a symbol`);
    case "function":
      return unrepresentable(`${path} is a function`);
    case "object":
      break;
    default:
      return unrepresentable(`${path} has unsupported typeof ${typeof value}`);
  }

  if (seen.has(value)) return unrepresentable(`${path} contains a cycle or shared alias`);
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
    return {
      kind: "set",
      elements: [...value].map((element, index) => encodeValue(element, seen, `${path}.set[${index}]`)),
    };
  }

  const prototype = Object.getPrototypeOf(value);
  if (prototype !== Object.prototype && prototype !== null) {
    return unrepresentable(`${path} is an instance of ${prototype?.constructor?.name ?? "an unknown class"}`);
  }
  if (Object.getOwnPropertySymbols(value).length > 0) {
    return unrepresentable(`${path} has symbol-keyed properties`);
  }

  const properties = [];
  for (const key of Object.keys(value)) {
    const descriptor = Object.getOwnPropertyDescriptor(value, key);
    if (descriptor?.get || descriptor?.set) {
      return unrepresentable(`${path}.${key} is an accessor property`);
    }
    properties.push({ key, value: encodeValue(descriptor?.value, seen, `${path}.${key}`) });
  }
  return { kind: "object", properties };
}

function encodeNumber(value) {
  if (Number.isNaN(value)) return "NaN";
  if (value === Number.POSITIVE_INFINITY) return "Infinity";
  if (value === Number.NEGATIVE_INFINITY) return "-Infinity";
  if (Object.is(value, -0)) return "-0";
  return String(value);
}

function unrepresentable(reason) {
  return { kind: "unrepresentable", reason };
}
