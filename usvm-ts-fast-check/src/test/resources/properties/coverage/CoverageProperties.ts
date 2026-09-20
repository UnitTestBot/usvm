import { classify } from './source-under-test.ts';

export function coversPositive(value: number): boolean {
  return classify(value) === 'positive';
}

export function coversNonPositive(value: number): boolean {
  return classify(value) === 'non-positive';
}

export function failsAfterClassifying(value: number): boolean {
  classify(value);
  return false;
}
