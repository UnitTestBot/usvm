export function encodeValue(value, seen = new WeakSet(), path = "$") {
  if (value === undefined) return { kind: "undefined" };
  if (value === null) return { kind: "null" };
  if (typeof value === "number") return { kind: "number", value: encodeNumber(value) };
  if (typeof value === "boolean") return { kind: "boolean", value: String(value) };
  if (typeof value === "string") return { kind: "string", value };
  if (typeof value !== "object") return unrepresentable(`${path} has typeof ${typeof value}`);
  if (seen.has(value)) return unrepresentable(`${path} contains a cycle or shared alias`);
  seen.add(value);
  if (Array.isArray(value)) {
    const elements = [];
    for (let index = 0; index < value.length; index += 1) {
      elements.push(index in value ? encodeValue(value[index], seen, `${path}[${index}]`) : { kind: "hole" });
    }
    return { kind: "array", elements };
  }
  const prototype = Object.getPrototypeOf(value);
  if (prototype !== Object.prototype && prototype !== null) {
    return unrepresentable(`${path} is an instance of ${prototype?.constructor?.name ?? "unknown"}`);
  }
  if (Object.getOwnPropertySymbols(value).length > 0) return unrepresentable(`${path} has symbol keys`);
  const properties = [];
  for (const key of Object.keys(value)) {
    const descriptor = Object.getOwnPropertyDescriptor(value, key);
    if (descriptor?.get || descriptor?.set) return unrepresentable(`${path}.${key} is an accessor`);
    properties.push({ key, value: encodeValue(descriptor?.value, seen, `${path}.${key}`) });
  }
  return { kind: "object", properties };
}

export function decodeJsonSeed(value, path = "$") {
  switch (value?.kind) {
    case "undefined": throw new Error(`${path}: undefined is not JSON-encodable as an ExpoSE initial input`);
    case "null": return null;
    case "boolean": return value.value === "true";
    case "string": return value.value ?? "";
    case "number": {
      const number = Number(value.value);
      if (!Number.isFinite(number) || Object.is(number, -0)) {
        throw new Error(`${path}: ${value.value} is not losslessly JSON-encodable for ExpoSE`);
      }
      return number;
    }
    case "array": return value.elements.map((item, index) => {
      if (item.kind === "hole") throw new Error(`${path}[${index}]: array holes are unsupported`);
      return decodeJsonSeed(item, `${path}[${index}]`);
    });
    case "object": return Object.fromEntries((value.properties ?? []).map((property) => [
      property.key,
      decodeJsonSeed(property.value, `${path}.${property.key}`),
    ]));
    default: throw new Error(`${path}: unsupported ETC kind ${value?.kind}`);
  }
}

function encodeNumber(value) {
  if (Number.isNaN(value)) return "NaN";
  if (value === Infinity) return "Infinity";
  if (value === -Infinity) return "-Infinity";
  if (Object.is(value, -0)) return "-0";
  return String(value);
}

function unrepresentable(reason) {
  return { kind: "unrepresentable", reason };
}
