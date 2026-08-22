import fc from 'fast-check';
import {
  decodeJsNumber,
  decodeJsValue,
  protocolError,
} from './js-value.mjs';

export const FAST_CHECK_BACKEND_ID = 'fast-check';
export const FAST_CHECK_BACKEND_VERSION = '4.9.0';

export function projectDomain(domain, path = 'domain') {
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
      return fc.tuple(...domain.elements.map((element, index) => projectDomain(element, `${path}.elements[${index}]`)));
    case 'array':
      validateLengths(domain, path);
      return fc.array(projectDomain(domain.element, `${path}.element`), {
        minLength: domain.minLength,
        maxLength: domain.maxLength,
      });
    default:
      throw protocolError('domain.kind.unknown', `Unknown property domain kind: ${String(domain.kind)}`, path);
  }
}

export function projectionCapability(domain, path = 'domain') {
  try {
    projectDomain(domain, path);
    return {
      backendId: FAST_CHECK_BACKEND_ID,
      backendVersion: FAST_CHECK_BACKEND_VERSION,
      level: 'exact',
      diagnostics: [],
    };
  } catch (error) {
    if (typeof error?.code !== 'string') throw error;
    return {
      backendId: FAST_CHECK_BACKEND_ID,
      backendVersion: FAST_CHECK_BACKEND_VERSION,
      level: 'unsupported',
      diagnostics: [{
        code: error.code,
        message: error.message.slice(error.message.indexOf(':') + 2),
        path: error.path ?? path,
      }],
    };
  }
}

function projectNumber(domain, path) {
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
  const arbitraries = [fc.double({
    min: finiteMin,
    max: finiteMax,
    noNaN: true,
    noDefaultInfinity: true,
  })];
  if (domain.allowNaN) arbitraries.push(fc.constant(Number.NaN));
  if (min === Number.NEGATIVE_INFINITY) arbitraries.push(fc.constant(Number.NEGATIVE_INFINITY));
  if (max === Number.POSITIVE_INFINITY) arbitraries.push(fc.constant(Number.POSITIVE_INFINITY));
  if (min <= 0 && max >= 0) arbitraries.push(fc.constant(-0));
  return arbitraries.length === 1 ? arbitraries[0] : fc.oneof(...arbitraries);
}

function validateIntegerDomain(domain, path) {
  const valid = Number.isInteger(domain.min)
    && Number.isInteger(domain.max)
    && domain.min >= -0x80000000
    && domain.max <= 0x7fffffff
    && domain.min <= domain.max;
  if (!valid) {
    throw protocolError('domain.integer.bounds', 'Integer bounds must be an inclusive signed 32-bit range', path);
  }
}

function validateLengths(domain, path) {
  const valid = Number.isInteger(domain.minLength)
    && Number.isInteger(domain.maxLength)
    && domain.minLength >= 0
    && domain.minLength <= domain.maxLength;
  if (!valid) {
    throw protocolError('domain.length.invalid', 'Domain length bounds are invalid', path);
  }
}

function requireDomainObject(domain, path) {
  if (domain === null || typeof domain !== 'object' || Array.isArray(domain)) {
    throw protocolError('domain.invalid', 'Property domain must be an object', path);
  }
}
