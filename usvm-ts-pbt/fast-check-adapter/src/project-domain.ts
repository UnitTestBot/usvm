import fc from 'fast-check';
import {
  decodeJsNumber,
  decodeJsValue,
  ProtocolError,
  protocolError,
} from './js-value.js';

export interface ProjectionDiagnostic {
  code: string;
  message: string;
  path: string;
}

export interface ProjectionCapability {
  level: 'exact' | 'unsupported';
  diagnostics: ProjectionDiagnostic[];
}

type DomainRecord = Record<string, unknown>;

export function projectDomain(domain: unknown, path = 'domain'): fc.Arbitrary<unknown> {
  requireDomainObject(domain, path);

  switch (domain.kind) {
    case 'boolean':
      return fc.boolean();

    case 'integer':
      validateIntegerDomain(domain, path);

      return fc.integer({ min: domain.min, max: domain.max });

    case 'number':
      return projectNumber(domain, path);

    case 'string':
      validateLengths(domain, path);

      return fc.array(fc.integer({ min: 0, max: 0xffff }), {
        minLength: domain.minLength,
        maxLength: domain.maxLength,
      }).map((units) => units.map((unit) => String.fromCharCode(unit)).join(''));

    case 'constant':
      return fc.constant(decodeJsValue(domain.value, `${path}.value`));

    case 'optional': {
      const nil = decodeJsValue(domain.nil, `${path}.nil`);

      if (nil !== undefined && nil !== null) {
        throw protocolError('domain.optional.nil', 'Optional nil must be null or undefined', `${path}.nil`);
      }

      return fc.option(projectDomain(domain.value, `${path}.value`), { nil });
    }

    case 'tuple':
      if (!Array.isArray(domain.elements) || domain.elements.length === 0) {
        throw protocolError('domain.tuple.empty', 'Tuple domain must contain elements', path);
      }

      return fc.tuple(...domain.elements.map(
        (element: unknown, index: number) => projectDomain(element, `${path}.elements[${index}]`),
      ));

    case 'array':
      validateLengths(domain, path);

      return fc.array(projectDomain(domain.element, `${path}.element`), {
        minLength: domain.minLength,
        maxLength: domain.maxLength,
      });

    default:
      throw protocolError(
        'domain.kind.unknown',
        `Unknown property domain kind: ${String(domain.kind)}`,
        path,
      );
  }
}

export function projectionCapability(domain: unknown, path = 'domain'): ProjectionCapability {
  try {
    projectDomain(domain, path);

    return {
      level: 'exact',
      diagnostics: [],
    };
  } catch (error: unknown) {
    if (!(error instanceof ProtocolError)) throw error;

    return {
      level: 'unsupported',
      diagnostics: [{
        code: error.code,
        message: error.diagnosticMessage,
        path: error.path,
      }],
    };
  }
}

function projectNumber(domain: DomainRecord, path: string): fc.Arbitrary<number> {
  if (typeof domain.allowNaN !== 'boolean') {
    throw protocolError('domain.number.allow-nan.invalid', 'allowNaN must be a boolean', `${path}.allowNaN`);
  }

  const min = decodeJsNumber(domain.min, `${path}.min`);
  const max = decodeJsNumber(domain.max, `${path}.max`);

  if (Number.isNaN(min) || Number.isNaN(max)) {
    throw protocolError('domain.number.bound.nan', 'Number bounds must not be NaN', path);
  }

  if (min > max) {
    throw protocolError('domain.number.bounds', 'Number minimum exceeds maximum', path);
  }

  const bounded = min !== Number.NEGATIVE_INFINITY || max !== Number.POSITIVE_INFINITY;

  if (bounded && domain.allowNaN) {
    throw protocolError('domain.number.nan-bounded', 'Bounded number domains must exclude NaN', `${path}.allowNaN`);
  }

  const finiteMin = min === Number.NEGATIVE_INFINITY ? -Number.MAX_VALUE : min;
  const finiteMax = max === Number.POSITIVE_INFINITY ? Number.MAX_VALUE : max;
  const arbitraries: fc.Arbitrary<number>[] = [];

  if (finiteMin <= finiteMax) {
    arbitraries.push(fc.double({
      min: finiteMin,
      max: finiteMax,
      noNaN: true,
      noDefaultInfinity: true,
    }));
  }

  if (domain.allowNaN) arbitraries.push(fc.constant(Number.NaN));
  if (min === Number.NEGATIVE_INFINITY) arbitraries.push(fc.constant(Number.NEGATIVE_INFINITY));
  if (max === Number.POSITIVE_INFINITY) arbitraries.push(fc.constant(Number.POSITIVE_INFINITY));
  if (min <= 0 && max >= 0) arbitraries.push(fc.constant(-0));

  const [first, ...rest] = arbitraries;
  if (first === undefined) {
    throw protocolError('domain.number.empty', 'Number domain does not contain any values', path);
  }

  return rest.length === 0 ? first : fc.oneof(first, ...rest);
}

function validateIntegerDomain(
  domain: DomainRecord,
  path: string,
): asserts domain is DomainRecord & { min: number; max: number } {
  const valid = typeof domain.min === 'number'
    && typeof domain.max === 'number'
    && Number.isInteger(domain.min)
    && Number.isInteger(domain.max)
    && domain.min >= -0x80000000
    && domain.max <= 0x7fffffff
    && domain.min <= domain.max;

  if (!valid) {
    throw protocolError('domain.integer.bounds', 'Integer bounds must be an inclusive signed 32-bit range', path);
  }
}

function validateLengths(
  domain: DomainRecord,
  path: string,
): asserts domain is DomainRecord & { minLength: number; maxLength: number } {
  const valid = typeof domain.minLength === 'number'
    && typeof domain.maxLength === 'number'
    && Number.isInteger(domain.minLength)
    && Number.isInteger(domain.maxLength)
    && domain.minLength >= 0
    && domain.minLength <= domain.maxLength;

  if (!valid) {
    throw protocolError('domain.length.invalid', 'Domain length bounds are invalid', path);
  }
}

function requireDomainObject(domain: unknown, path: string): asserts domain is DomainRecord {
  if (domain === null || typeof domain !== 'object' || Array.isArray(domain)) {
    throw protocolError('domain.invalid', 'Property domain must be an object', path);
  }
}
