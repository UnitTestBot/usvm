import type { PropertyManifestWire } from './execute-property.js';
import { decodeJsNumber, decodeJsValue, type JsConcreteValue } from './js-value.js';

/** Admission checks the declared set independently of the arbitrary's ability to shrink external values. */
export function inWitnessSupport(manifest: PropertyManifestWire, values: JsConcreteValue[]): boolean {
  const domainsMatch = values.length === manifest.inputs.length
    && manifest.inputs.every((input, index) => contains(input.domain, values[index]));
  if (!domainsMatch) return false;
  if (manifest.generator === undefined) return true;

  const [array, index] = values;

  return Array.isArray(array) && typeof index === 'number' && Number.isInteger(index)
    && !Object.is(index, -0) && index >= 0 && index < array.length;
}

function contains(domainValue: unknown, value: JsConcreteValue): boolean {
  const domain = domainValue as Record<string, unknown>;
  switch (domain.kind) {
    case 'boolean': return typeof value === 'boolean';
    case 'integer': return typeof value === 'number' && Number.isInteger(value) && !Object.is(value, -0)
      && value >= (domain.min as number) && value <= (domain.max as number);
    case 'number': return typeof value === 'number' && (Number.isNaN(value)
      ? domain.allowNaN === true : value >= decodeJsNumber(domain.min) && value <= decodeJsNumber(domain.max));
    case 'string': return typeof value === 'string' && value.length >= (domain.minLength as number)
      && value.length <= (domain.maxLength as number);
    case 'constant': return Object.is(value, decodeJsValue(domain.value));
    case 'optional': return Object.is(value, decodeJsValue(domain.nil)) || contains(domain.value, value);
    case 'array': return Array.isArray(value) && value.length >= (domain.minLength as number)
      && value.length <= (domain.maxLength as number) && value.every((entry) => contains(domain.element, entry));
    case 'tuple': {
      const elements = domain.elements as unknown[];

      return Array.isArray(value) && value.length === elements.length
        && elements.every((element, index) => contains(element, value[index]));
    }
    case 'object': {
      const fields = domain.fields as Record<string, unknown>;

      return value !== null && typeof value === 'object' && !Array.isArray(value)
        && Object.keys(value).length === Object.keys(fields).length
        && Object.entries(fields).every(([key, field]) => Object.hasOwn(value, key) && contains(field, value[key]));
    }
    default: return false;
  }
}
