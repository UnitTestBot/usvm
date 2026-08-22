export function decodeJsValue(value, path = 'value') {
  requireObject(value, 'js-value.invalid', 'Tagged JavaScript value must be an object', path);
  switch (value.kind) {
    case 'undefined':
      return undefined;
    case 'null':
      return null;
    case 'boolean':
      if (typeof value.value !== 'boolean') {
        throw protocolError('js-value.boolean.invalid', 'Boolean value must contain a boolean', path);
      }
      return value.value;
    case 'string':
      if (typeof value.value !== 'string') {
        throw protocolError('js-value.string.invalid', 'String value must contain a string', path);
      }
      return value.value;
    case 'number':
      return decodeJsNumber(value, path);
    default:
      throw protocolError('js-value.kind.unknown', `Unknown JavaScript value kind: ${String(value.kind)}`, path);
  }
}

export function encodeJsValue(value) {
  if (value === undefined) return { kind: 'undefined' };
  if (value === null) return { kind: 'null' };
  if (typeof value === 'boolean') return { kind: 'boolean', value };
  if (typeof value === 'string') return { kind: 'string', value };
  if (typeof value === 'number') return { kind: 'number', ...encodeJsNumber(value) };
  throw protocolError(
    'js-value.type.unsupported',
    `Unsupported JavaScript value type: ${typeof value}`,
    'value',
  );
}

export function decodeJsNumber(number, path = 'number') {
  requireObject(number, 'js-number.invalid', 'Tagged JavaScript number must be an object', path);
  switch (number.value) {
    case 'finite':
      if (typeof number.bits !== 'string' || !/^[0-9a-f]{16}$/.test(number.bits)) {
        throw protocolError(
          'js-number.encoding.invalid',
          'Finite JavaScript numbers require sixteen lowercase hexadecimal digits',
          path,
        );
      }
      return bitsToDouble(number.bits);
    case 'nan':
      requireNoBits(number, path);
      return Number.NaN;
    case 'positive-infinity':
      requireNoBits(number, path);
      return Number.POSITIVE_INFINITY;
    case 'negative-infinity':
      requireNoBits(number, path);
      return Number.NEGATIVE_INFINITY;
    default:
      throw protocolError(
        'js-number.kind.unknown',
        `Unknown JavaScript number kind: ${String(number.value)}`,
        path,
      );
  }
}

export function encodeJsNumber(value) {
  if (Number.isNaN(value)) return { value: 'nan' };
  if (value === Number.POSITIVE_INFINITY) return { value: 'positive-infinity' };
  if (value === Number.NEGATIVE_INFINITY) return { value: 'negative-infinity' };
  return { value: 'finite', bits: doubleToBits(value) };
}

export function protocolError(code, message, path) {
  const error = new Error(`${code}: ${message}`);
  error.code = code;
  error.path = path;
  return error;
}

function bitsToDouble(bits) {
  const buffer = new ArrayBuffer(8);
  const view = new DataView(buffer);
  view.setBigUint64(0, BigInt(`0x${bits}`), false);
  return view.getFloat64(0, false);
}

function doubleToBits(value) {
  const buffer = new ArrayBuffer(8);
  const view = new DataView(buffer);
  view.setFloat64(0, value, false);
  return view.getBigUint64(0, false).toString(16).padStart(16, '0');
}

function requireNoBits(number, path) {
  if (number.bits !== undefined) {
    throw protocolError('js-number.encoding.invalid', 'Non-finite JavaScript numbers must not contain bits', path);
  }
}

function requireObject(value, code, message, path) {
  if (value === null || typeof value !== 'object' || Array.isArray(value)) {
    throw protocolError(code, message, path);
  }
}
