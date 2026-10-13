import { types } from 'node:util';
import {
  adapterDiagnostic,
  type AdapterDiagnosticDescriptor,
  type AdapterDiagnosticKind,
} from './diagnostics.js';

export type JsConcreteValue =
  | undefined
  | null
  | boolean
  | string
  | number
  | JsConcreteValue[]
  | { [key: string]: JsConcreteValue };

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
  | { kind: 'array'; elements: TaggedJsValue[] }
  | { kind: 'object'; fields: Record<string, TaggedJsValue> };

export interface ProtocolDiagnostic {
  kind: AdapterDiagnosticKind;
  code: string;
  message: string;
  path: string;
}

export class ProtocolError extends Error {
  constructor(
    diagnostic: AdapterDiagnosticDescriptor,
    readonly diagnosticMessage: string,
    readonly path: string,
  ) {
    super(`${diagnostic.code}: ${diagnosticMessage}`);
    this.name = 'ProtocolError';
    this.kind = diagnostic.kind;
    this.code = diagnostic.code;
  }

  readonly kind: AdapterDiagnosticKind;
  readonly code: string;
}

export function decodeJsValue(value: unknown, path = 'value'): JsConcreteValue {
  requireObject(value, adapterDiagnostic.jsValueInvalid, 'Tagged JavaScript value must be an object', path);

  switch (value.kind) {
    case 'undefined':
      return undefined;

    case 'null':
      return null;

    case 'boolean':
      if (typeof value.value !== 'boolean') {
        throw protocolError(
          adapterDiagnostic.jsValueBooleanInvalid,
          'Boolean value must contain a boolean',
          path,
        );
      }

      return value.value;

    case 'string':
      if (typeof value.value !== 'string') {
        throw protocolError(
          adapterDiagnostic.jsValueStringInvalid,
          'String value must contain a string',
          path,
        );
      }

      return value.value;

    case 'number':
      return decodeJsNumber(value, path);

    case 'array':
      if (!Array.isArray(value.elements)) {
        throw protocolError(adapterDiagnostic.jsValueArrayInvalid, 'Array value must contain elements', path);
      }

      return value.elements.map((element: unknown, index: number) =>
        decodeJsValue(element, `${path}.elements[${index}]`));

    case 'object': {
      const fields = value.fields;
      if (fields === null || typeof fields !== 'object' || Array.isArray(fields)) {
        throw protocolError(adapterDiagnostic.jsValueObjectInvalid, 'Object value must contain fields', path);
      }

      const entries = Object.entries(fields).map(([name, field]) => {
        requireSafeObjectKey(name, `${path}.fields.${name}`);
        return [name, decodeJsValue(field, `${path}.fields.${name}`)] as const;
      });

      return Object.fromEntries(entries);
    }

    default:
      throw protocolError(
        adapterDiagnostic.jsValueKindUnknown,
        `Unknown JavaScript value kind: ${String(value.kind)}`,
        path,
      );
  }
}

export function encodeJsValue(value: unknown): TaggedJsValue {
  return encodeValue(value, new WeakSet());
}

function encodeValue(value: unknown, seen: WeakSet<object>): TaggedJsValue {
  if (value === undefined) return { kind: 'undefined' };
  if (value === null) return { kind: 'null' };
  if (typeof value === 'boolean') return { kind: 'boolean', value };
  if (typeof value === 'string') return { kind: 'string', value };
  if (typeof value === 'number') return { kind: 'number', ...encodeJsNumber(value) };

  if (typeof value !== 'object' || types.isProxy(value) || seen.has(value)) {
    throw unsupportedValue();
  }
  seen.add(value);
  const isArray = Array.isArray(value);
  const expectedPrototype = isArray ? Array.prototype : Object.prototype;
  if (Object.getPrototypeOf(value) !== expectedPrototype || Object.getOwnPropertySymbols(value).length > 0) {
    throw unsupportedValue();
  }

  if (isArray) {
    const elements: TaggedJsValue[] = [];
    for (let index = 0; index < value.length; index += 1) {
      elements.push(encodeValue(ownDataValue(value, String(index)), seen));
    }
    if (Object.getOwnPropertyNames(value).length !== value.length + 1) throw unsupportedValue();

    return { kind: 'array', elements };
  }

  const entries = Object.getOwnPropertyNames(value).map((name) => {
    requireSafeObjectKey(name, `value.fields.${name}`);

    return [name, encodeValue(ownDataValue(value, name), seen)] as const;
  });

  return { kind: 'object', fields: Object.fromEntries(entries) };
}

function ownDataValue(value: object, name: string): unknown {
  const descriptor = Object.getOwnPropertyDescriptor(value, name);
  if (descriptor === undefined || !descriptor.enumerable || !('value' in descriptor)) throw unsupportedValue();

  return descriptor.value;
}

function unsupportedValue(): ProtocolError {
  return protocolError(
    adapterDiagnostic.jsValueTypeUnsupported,
    'Only primitive values, dense arrays, and plain own-data objects without aliases are supported',
    'value',
  );
}

function requireSafeObjectKey(name: string, path: string): void {
  if (['__proto__', 'constructor', 'prototype'].includes(name)) {
    throw protocolError(adapterDiagnostic.jsValueObjectInvalid, 'Unsupported object property name', path);
  }
}

export function decodeJsNumber(taggedNumber: unknown, path = 'number'): number {
  requireObject(
    taggedNumber,
    adapterDiagnostic.jsNumberInvalid,
    'Tagged JavaScript number must be an object',
    path,
  );

  switch (taggedNumber.value) {
    case 'finite':
      if (typeof taggedNumber.bits !== 'string' || !/^[0-9a-f]{16}$/.test(taggedNumber.bits)) {
        throw protocolError(
          adapterDiagnostic.jsNumberEncodingInvalid,
          'Finite JavaScript numbers require sixteen lowercase hexadecimal digits',
          path,
        );
      }

      const finiteValue = bitsToDouble(taggedNumber.bits);
      if (!Number.isFinite(finiteValue)) {
        throw protocolError(
          adapterDiagnostic.jsNumberEncodingInvalid,
          'Finite JavaScript numbers require finite IEEE-754 bits',
          path,
        );
      }

      return finiteValue;

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
        adapterDiagnostic.jsNumberKindUnknown,
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

export function protocolError(
  diagnostic: AdapterDiagnosticDescriptor,
  message: string,
  path: string,
): ProtocolError {
  return new ProtocolError(diagnostic, message, path);
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
    throw protocolError(
      adapterDiagnostic.jsNumberEncodingInvalid,
      'Non-finite JavaScript numbers must not contain bits',
      path,
    );
  }
}

function requireObject(
  value: unknown,
  diagnostic: AdapterDiagnosticDescriptor,
  message: string,
  path: string,
): asserts value is Record<string, unknown> {
  if (value === null || typeof value !== 'object' || Array.isArray(value)) {
    throw protocolError(diagnostic, message, path);
  }
}
