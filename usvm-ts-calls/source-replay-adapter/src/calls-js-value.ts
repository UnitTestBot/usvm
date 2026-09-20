export type TaggedJsValue =
  | { kind: 'undefined' }
  | { kind: 'null' }
  | { kind: 'boolean'; value: boolean }
  | { kind: 'string'; value: string }
  | { kind: 'number'; value: 'finite'; bits: string }
  | { kind: 'number'; value: 'nan' | 'positive-infinity' | 'negative-infinity' }
  | { kind: 'array'; elements: TaggedJsValue[] };

export function decodeJsValue(value: TaggedJsValue, path = 'value'): unknown {
  switch (value.kind) {
    case 'undefined':
      return undefined;
    case 'null':
      return null;
    case 'boolean':
    case 'string':
      return value.value;
    case 'number':
      return decodeNumber(value, path);
    case 'array':
      return value.elements.map((element, index) => decodeJsValue(element, `${path}.elements[${index}]`));
  }
}

function decodeNumber(value: Extract<TaggedJsValue, { kind: 'number' }>, path: string): number {
  switch (value.value) {
    case 'nan':
      return Number.NaN;
    case 'positive-infinity':
      return Number.POSITIVE_INFINITY;
    case 'negative-infinity':
      return Number.NEGATIVE_INFINITY;
    case 'finite': {
      if (!/^[0-9a-f]{16}$/.test(value.bits)) throw new Error(`${path} has an invalid finite number encoding`);

      const buffer = new ArrayBuffer(8);
      const view = new DataView(buffer);
      view.setBigUint64(0, BigInt(`0x${value.bits}`), false);

      return view.getFloat64(0, false);
    }
  }
}
