import { record } from '../record.mjs';

record('collections-util:init');
export function defaultEquals(a, b) {
  record(`collections-util:defaultEquals:${typeof a}:${String(a)}:${typeof b}:${String(b)}`);
  return a === b;
}
