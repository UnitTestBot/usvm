const CALLABLE_REFERENCE = Symbol.for("usvm.ts-pbt.fast-check.callable-reference");

export class UnsupportedExampleError extends Error {
  constructor(path, reason) {
    super(`${path}: ${reason}`);
    this.name = "UnsupportedExampleError";
  }
}

export function referencedCallable(reference) {
  return Object.freeze({ [CALLABLE_REFERENCE]: normalizeCallableReference(reference) });
}

export function encodeInput(receiver, args, { receiverPlan = null } = {}) {
  const state = { seen: new WeakMap(), active: new WeakSet(), nextAlias: 0 };
  let encodedReceiver;
  if (receiverPlan !== null && !isPlainObject(receiver)) {
    encodedReceiver = unrepresentable(
      "$receiver has a constructor plan but is not a plain object",
      "classInstance",
    );
  } else {
    encodedReceiver = encodeValue(receiver, state, "$receiver");
    if (receiverPlan !== null && encodedReceiver.kind === "object") {
      encodedReceiver = {
        ...encodedReceiver,
        className: receiverPlan.className ?? undefined,
        constructorPlan: {
          callable: normalizeCallableReference(receiverPlan.callable),
          arguments: (receiverPlan.arguments ?? []).map((value, index) =>
            encodeValue(value, state, `$receiver.constructorPlan.arguments[${index}]`)),
        },
      };
    }
  }
  return {
    receiver: encodedReceiver,
    arguments: args.map((value, index) => encodeValue(value, state, `$arguments[${index}]`)),
  };
}

export function encodeValue(value, state = null, path = "$") {
  const encoding = state ?? { seen: new WeakMap(), active: new WeakSet(), nextAlias: 0 };
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
      return unrepresentable(`${path} is bigint ${value}`, "other");
    case "symbol":
      return unrepresentable(`${path} is a symbol`, "symbol");
    case "function":
      return unrepresentable(`${path} is an arbitrary function`, "function");
    case "object":
      break;
    default:
      return unrepresentable(`${path} has unsupported typeof ${typeof value}`, "other");
  }

  if (value[CALLABLE_REFERENCE] !== undefined) {
    return { kind: "callable", callableReference: value[CALLABLE_REFERENCE] };
  }
  const existingAlias = encoding.seen.get(value);
  if (existingAlias !== undefined) {
    return encoding.active.has(value)
      ? unrepresentable(`${path} contains a cycle`, "cycle")
      : { kind: "alias", aliasReference: existingAlias };
  }

  const aliasId = `alias-${encoding.nextAlias++}`;
  encoding.seen.set(value, aliasId);
  encoding.active.add(value);
  try {
    if (Array.isArray(value)) {
      const unsupported = unsupportedArrayLayout(value, path);
      if (unsupported !== null) return { ...unsupported, aliasId };
      const elements = [];
      for (let index = 0; index < value.length; index += 1) {
        elements.push(index in value
          ? encodeValue(value[index], encoding, `${path}[${index}]`)
          : { kind: "hole" });
      }
      return { kind: "array", aliasId, elements };
    }

    if (value instanceof Map) {
      if (!hasExactBuiltinLayout(value, Map.prototype)) {
        return { ...unrepresentable(`${path} has a non-standard Map layout`, "classInstance"), aliasId };
      }
      return {
        kind: "map",
        aliasId,
        entries: [...value].map(([key, entryValue], index) => ({
          key: encodeValue(key, encoding, `${path}.map[${index}].key`),
          value: encodeValue(entryValue, encoding, `${path}.map[${index}].value`),
        })),
      };
    }

    if (value instanceof Set) {
      if (!hasExactBuiltinLayout(value, Set.prototype)) {
        return { ...unrepresentable(`${path} has a non-standard Set layout`, "classInstance"), aliasId };
      }
      return {
        kind: "set",
        aliasId,
        elements: [...value].map((element, index) =>
          encodeValue(element, encoding, `${path}.set[${index}]`)),
      };
    }

    const prototype = Object.getPrototypeOf(value);
    if (prototype !== Object.prototype) {
      return { ...unrepresentable(
        `${path} is an instance of ${prototype?.constructor?.name ?? "an unknown class"}`,
        "classInstance",
      ), aliasId };
    }
    if (Object.getOwnPropertySymbols(value).length > 0) {
      return { ...unrepresentable(`${path} has symbol-keyed properties`, "symbol"), aliasId };
    }
    const enumerableKeys = Object.keys(value);
    if (Object.getOwnPropertyNames(value).length !== enumerableKeys.length) {
      return { ...unrepresentable(`${path} has non-enumerable properties`, "other"), aliasId };
    }
    for (const key of enumerableKeys) {
      const descriptor = Object.getOwnPropertyDescriptor(value, key);
      if (descriptor?.get || descriptor?.set) {
        return { ...unrepresentable(`${path}.${key} is an accessor property`, "accessor"), aliasId };
      }
      if (descriptor?.writable !== true || descriptor.configurable !== true || descriptor.enumerable !== true) {
        return { ...unrepresentable(`${path}.${key} has a non-default data descriptor`, "other"), aliasId };
      }
    }

    const properties = [];
    for (const key of enumerableKeys) {
      const descriptor = Object.getOwnPropertyDescriptor(value, key);
      properties.push({ key, value: encodeValue(descriptor?.value, encoding, `${path}.${key}`) });
    }
    return { kind: "object", aliasId, properties };
  } finally {
    encoding.active.delete(value);
  }
}

export function findUnrepresentable(input) {
  const result = [];
  visit(input.receiver, "$receiver", result);
  input.arguments.forEach((value, index) => visit(value, `$arguments[${index}]`, result));
  return result;
}

export async function materializeInput(testCase, resolver = {}) {
  const aliases = new Map();
  const receiver = await materializeValue(testCase.receiver ?? { kind: "undefined" }, "$receiver", aliases, resolver);
  const args = [];
  for (let index = 0; index < testCase.arguments.length; index += 1) {
    args.push(await materializeValue(testCase.arguments[index], `$arguments[${index}]`, aliases, resolver));
  }
  return { receiver, arguments: args };
}

async function materializeValue(value, path, aliases, resolver) {
  switch (value.kind) {
    case "undefined": return undefined;
    case "null": return null;
    case "number": return decodeNumber(value.value, path);
    case "boolean": {
      if (value.value === "true") return true;
      if (value.value === "false") return false;
      throw new UnsupportedExampleError(path, `invalid boolean '${value.value}'`);
    }
    case "string": return value.value;
    case "hole": throw new UnsupportedExampleError(path, "array hole is only valid inside an array");
    case "alias": {
      if (!aliases.has(value.aliasReference)) {
        throw new UnsupportedExampleError(path, `unknown or forward alias '${value.aliasReference}'`);
      }
      return aliases.get(value.aliasReference);
    }
    case "unrepresentable":
      throw new UnsupportedExampleError(path, value.reason ?? "producer marked value unrepresentable");
    case "callable": {
      if (typeof resolver.resolveCallable !== "function") {
        throw new UnsupportedExampleError(path, "callable reference requires harness.resolveCallable(reference)");
      }
      const callable = await resolver.resolveCallable(value.callableReference);
      if (typeof callable !== "function") {
        throw new UnsupportedExampleError(path, "resolveCallable did not return a function");
      }
      defineAlias(value, callable, aliases, path);
      return callable;
    }
    case "array": {
      const array = new Array(value.elements.length);
      defineAlias(value, array, aliases, path);
      for (let index = 0; index < value.elements.length; index += 1) {
        if (value.elements[index].kind !== "hole") {
          array[index] = await materializeValue(value.elements[index], `${path}[${index}]`, aliases, resolver);
        }
      }
      return array;
    }
    case "object": {
      let object;
      if (value.constructorPlan !== undefined && value.constructorPlan !== null) {
        const planArgs = [];
        for (let index = 0; index < value.constructorPlan.arguments.length; index += 1) {
          planArgs.push(await materializeValue(
            value.constructorPlan.arguments[index],
            `${path}.constructorPlan.arguments[${index}]`,
            aliases,
            resolver,
          ));
        }
        if (typeof resolver.construct === "function") {
          object = await resolver.construct(value.constructorPlan.callable, planArgs);
        } else if (typeof resolver.resolveCallable === "function") {
          const constructor = await resolver.resolveCallable(value.constructorPlan.callable);
          if (typeof constructor !== "function") {
            throw new UnsupportedExampleError(path, "constructor resolver did not return a function");
          }
          object = Reflect.construct(constructor, planArgs);
        } else {
          throw new UnsupportedExampleError(path, "constructor plan requires a harness resolver");
        }
      } else {
        if (value.className !== undefined && value.className !== null) {
          throw new UnsupportedExampleError(path, "className without constructorPlan is not materializable");
        }
        object = {};
      }
      if (object === null || typeof object !== "object") {
        throw new UnsupportedExampleError(path, "constructor plan did not produce an object");
      }
      defineAlias(value, object, aliases, path);
      for (const property of value.properties) {
        Object.defineProperty(object, property.key, {
          value: await materializeValue(property.value, `${path}.${property.key}`, aliases, resolver),
          writable: true,
          enumerable: true,
          configurable: true,
        });
      }
      return object;
    }
    case "map": {
      const map = new Map();
      defineAlias(value, map, aliases, path);
      for (let index = 0; index < value.entries.length; index += 1) {
        const entry = value.entries[index];
        const key = await materializeValue(entry.key, `${path}.map[${index}].key`, aliases, resolver);
        const entryValue = await materializeValue(entry.value, `${path}.map[${index}].value`, aliases, resolver);
        map.set(key, entryValue);
      }
      return map;
    }
    case "set": {
      const set = new Set();
      defineAlias(value, set, aliases, path);
      for (let index = 0; index < value.elements.length; index += 1) {
        set.add(await materializeValue(value.elements[index], `${path}.set[${index}]`, aliases, resolver));
      }
      return set;
    }
    default:
      throw new UnsupportedExampleError(path, `unknown ETC value kind '${value.kind}'`);
  }
}

function normalizeCallableReference(reference) {
  if (reference === null || typeof reference !== "object") {
    throw new TypeError("callable reference must be an object");
  }
  const allowed = new Set(["function", "class", "staticMethod", "instanceMethod", "arrow"]);
  if (typeof reference.modulePath !== "string" || reference.modulePath.length === 0) {
    throw new TypeError("callable reference modulePath must be non-empty");
  }
  if (typeof reference.exportName !== "string" || reference.exportName.length === 0) {
    throw new TypeError("callable reference exportName must be non-empty");
  }
  if (!allowed.has(reference.callableKind)) {
    throw new TypeError(`unsupported callableKind '${reference.callableKind}'`);
  }
  return {
    modulePath: reference.modulePath,
    exportName: reference.exportName,
    callableKind: reference.callableKind,
  };
}

function defineAlias(value, materialized, aliases, path) {
  if (value.aliasId === undefined || value.aliasId === null) return;
  if (aliases.has(value.aliasId)) {
    throw new UnsupportedExampleError(path, `duplicate alias '${value.aliasId}'`);
  }
  aliases.set(value.aliasId, materialized);
}

function visit(value, path, result) {
  if (value.kind === "unrepresentable") {
    result.push({ path, kind: value.unrepresentableKind ?? "other", reason: value.reason ?? "unrepresentable" });
  }
  value.elements?.forEach((element, index) => visit(element, `${path}.elements[${index}]`, result));
  value.properties?.forEach((property, index) =>
    visit(property.value, `${path}.properties[${index}].value`, result));
  value.entries?.forEach((entry, index) => {
    visit(entry.key, `${path}.entries[${index}].key`, result);
    visit(entry.value, `${path}.entries[${index}].value`, result);
  });
  value.constructorPlan?.arguments?.forEach((argument, index) =>
    visit(argument, `${path}.constructorPlan.arguments[${index}]`, result));
}

function encodeNumber(value) {
  if (Number.isNaN(value)) return "NaN";
  if (value === Number.POSITIVE_INFINITY) return "Infinity";
  if (value === Number.NEGATIVE_INFINITY) return "-Infinity";
  if (Object.is(value, -0)) return "-0";
  return String(value);
}

function decodeNumber(value, path) {
  if (value === "NaN") return Number.NaN;
  if (value === "Infinity") return Number.POSITIVE_INFINITY;
  if (value === "-Infinity") return Number.NEGATIVE_INFINITY;
  if (value === "-0") return -0;
  const decimal = /^-?(?:0|[1-9][0-9]*)(?:\.[0-9]+)?(?:[eE][+-]?[0-9]+)?$/u;
  const number = typeof value === "string" && decimal.test(value) ? Number(value) : Number.NaN;
  if (Number.isNaN(number)) {
    throw new UnsupportedExampleError(path, `invalid number '${value}'`);
  }
  return number;
}

function unrepresentable(reason, unrepresentableKind) {
  return { kind: "unrepresentable", reason, unrepresentableKind };
}

function isPlainObject(value) {
  return value !== null && typeof value === "object" && Object.getPrototypeOf(value) === Object.prototype;
}

function unsupportedArrayLayout(value, path) {
  for (const key of Object.getOwnPropertyNames(value)) {
    if (key === "length") continue;
    const index = canonicalArrayIndex(key);
    if (index === null || index >= value.length) {
      return unrepresentable(`${path} has non-index array property '${key}'`, "other");
    }
    const descriptor = Object.getOwnPropertyDescriptor(value, key);
    if (descriptor?.get || descriptor?.set) {
      return unrepresentable(`${path}[${index}] is an accessor element`, "accessor");
    }
    if (descriptor?.writable !== true || descriptor.configurable !== true || descriptor.enumerable !== true) {
      return unrepresentable(`${path}[${index}] has a non-default data descriptor`, "other");
    }
  }
  return null;
}

function canonicalArrayIndex(key) {
  if (!/^(?:0|[1-9][0-9]*)$/u.test(key)) return null;
  const index = Number(key);
  return Number.isSafeInteger(index) && String(index) === key ? index : null;
}

function hasExactBuiltinLayout(value, prototype) {
  return Object.getPrototypeOf(value) === prototype
    && Object.getOwnPropertyNames(value).length === 0
    && Object.getOwnPropertySymbols(value).length === 0;
}
