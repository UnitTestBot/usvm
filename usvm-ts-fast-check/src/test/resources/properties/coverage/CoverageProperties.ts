import { classify } from './source-under-test.ts';
import { classifyNested } from './nested-source-under-test.ts';
import { nonterminal } from './nonterminal-source-under-test.ts';

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

export function alwaysCovers(value: number): boolean {
  classify(value);
  return true;
}

export function coversNested(value: number): boolean {
  classifyNested(value);
  return true;
}

export function coversNonterminal(value: number): boolean {
  nonterminal(value);
  return true;
}
