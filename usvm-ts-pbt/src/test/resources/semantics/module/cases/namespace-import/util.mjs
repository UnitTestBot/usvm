import { record } from '../record.mjs';

record('namespace-util:init');
export function defaultEquals(a, b) {
  record(`namespace-util:defaultEquals:${String(a)}:${String(b)}`);
  return a === b;
}
export const marker = 'util';
