// Protocol responses contain JSON data only. Walk them without consulting an
// object's toJSON method, including one inherited by an internal array.
const stringifyScalar = JSON.stringify.bind(JSON);
const ownKeys = Object.keys.bind(Object);
const isArray = Array.isArray.bind(Array);

export function stringifyProtocolJson(value: unknown): string {
  const ancestors = new Set<object>();

  function serialize(current: unknown, inArray: boolean): string | undefined {
    if (current === null || typeof current === 'string' || typeof current === 'boolean'
      || typeof current === 'number') {
      return stringifyScalar(current);
    }
    if (current === undefined || typeof current === 'function' || typeof current === 'symbol') {
      return inArray ? 'null' : undefined;
    }
    if (typeof current !== 'object') throw new TypeError('Unsupported protocol JSON value');
    if (ancestors.has(current)) throw new TypeError('Circular protocol JSON value');

    ancestors.add(current);
    try {
      if (isArray(current)) {
        let elements = '[';
        for (let index = 0; index < current.length; index += 1) {
          if (index > 0) elements += ',';
          elements += serialize(current[index], true);
        }

        return `${elements}]`;
      }

      let fields = '{';
      for (const key of ownKeys(current)) {
        const encoded = serialize((current as Record<string, unknown>)[key], false);
        if (encoded !== undefined) {
          if (fields.length > 1) fields += ',';
          fields += `${stringifyScalar(key)}:${encoded}`;
        }
      }

      return `${fields}}`;
    } finally {
      ancestors.delete(current);
    }
  }

  const encoded = serialize(value, false);
  if (encoded === undefined) throw new TypeError('Protocol response has no JSON representation');

  return encoded;
}
