export type JsConcreteValue =
  | undefined
  | null
  | boolean
  | string
  | number
  | JsConcreteValue[];

export type TaggedJsNumber =
  | { value: 'finite'; bits: string }
  | { value: 'nan' }
  | { value: 'positive-infinity' }
  | { value: 'negative-infinity' };

export type TaggedJsValue =
  | { kind: 'undefined' }
  | { kind: 'null' }
  | { kind: 'boolean'; value: boolean }
  | { kind: 'string'; value: string }
  | ({ kind: 'number' } & TaggedJsNumber)
  | { kind: 'array'; elements: TaggedJsValue[] };

export interface ProtocolDiagnostic {
  code: string;
  message: string;
  path: string;
}

export class ProtocolError extends Error {
  constructor(
    readonly code: string,
    readonly diagnosticMessage: string,
    readonly path: string,
  ) {
    super(`${code}: ${diagnosticMessage}`);
    this.name = 'ProtocolError';
  }
}

export function decodeJsValue(value: unknown, path = 'value'): JsConcreteValue {
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
    case 'array':
      if (!Array.isArray(value.elements)) {
        throw protocolError('js-value.array.invalid', 'Array value must contain elements', path);
      }
      return value.elements.map((element: unknown, index: number) =>
        decodeJsValue(element, `${path}.elements[${index}]`));
    default:
      throw protocolError(
        'js-value.kind.unknown',
        `Unknown JavaScript value kind: ${String(value.kind)}`,
        path,
      );
  }
}

export function encodeJsValue(value: unknown): TaggedJsValue {
  if (value === undefined) return { kind: 'undefined' };
  if (value === null) return { kind: 'null' };
  if (typeof value === 'boolean') return { kind: 'boolean', value };
  if (typeof value === 'string') return { kind: 'string', value };
  if (typeof value === 'number') return { kind: 'number', ...encodeJsNumber(value) };
  if (Array.isArray(value)) return { kind: 'array', elements: value.map(encodeJsValue) };
  throw protocolError(
    'js-value.type.unsupported',
    `Unsupported JavaScript value type: ${typeof value}`,
    'value',
  );
}

export function decodeJsNumber(taggedNumber: unknown, path = 'number'): number {
  requireObject(taggedNumber, 'js-number.invalid', 'Tagged JavaScript number must be an object', path);
  switch (taggedNumber.value) {
    case 'finite':
      if (typeof taggedNumber.bits !== 'string' || !/^[0-9a-f]{16}$/.test(taggedNumber.bits)) {
        throw protocolError(
          'js-number.encoding.invalid',
          'Finite JavaScript numbers require sixteen lowercase hexadecimal digits',
          path,
        );
      }
      return bitsToDouble(taggedNumber.bits);
    case 'nan':
      requireNoBits(taggedNumber, path);
      return Number.NaN;
    case 'positive-infinity':
      requireNoBits(taggedNumber, path);
      return Number.POSITIVE_INFINITY;
    case 'negative-infinity':
      requireNoBits(taggedNumber, path);
      return Number.NEGATIVE_INFINITY;
    default:
      throw protocolError(
        'js-number.kind.unknown',
        `Unknown JavaScript number kind: ${String(taggedNumber.value)}`,
        path,
      );
  }
}

export function encodeJsNumber(value: number): TaggedJsNumber {
  if (Number.isNaN(value)) return { value: 'nan' };
  if (value === Number.POSITIVE_INFINITY) return { value: 'positive-infinity' };
  if (value === Number.NEGATIVE_INFINITY) return { value: 'negative-infinity' };
  return { value: 'finite', bits: doubleToBits(value) };
}

export function protocolError(code: string, message: string, path: string): ProtocolError {
  return new ProtocolError(code, message, path);
}

function bitsToDouble(bits: string): number {
  const buffer = new ArrayBuffer(8);
  const view = new DataView(buffer);
  view.setBigUint64(0, BigInt(`0x${bits}`), false);
  return view.getFloat64(0, false);
}

function doubleToBits(value: number): string {
  const buffer = new ArrayBuffer(8);
  const view = new DataView(buffer);
  view.setFloat64(0, value, false);
  return view.getBigUint64(0, false).toString(16).padStart(16, '0');
}

function requireNoBits(taggedNumber: Record<string, unknown>, path: string): void {
  if (taggedNumber.bits !== undefined) {
    throw protocolError('js-number.encoding.invalid', 'Non-finite JavaScript numbers must not contain bits', path);
  }
}

function requireObject(
  value: unknown,
  code: string,
  message: string,
  path: string,
): asserts value is Record<string, unknown> {
  if (value === null || typeof value !== 'object' || Array.isArray(value)) {
    throw protocolError(code, message, path);
  }
}
