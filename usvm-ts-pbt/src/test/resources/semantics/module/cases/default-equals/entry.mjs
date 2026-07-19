import { record } from '../record.mjs';
import * as util from './util.mjs';
import { equals, frequency, indexOf, lastIndexOf, remove } from './arrays.mjs';

record('collections-entry:init');
const mutable = [1, 2, 1];
export const result = {
  callableType: typeof util.defaultEquals,
  defaultTrue: util.defaultEquals(1, 1),
  defaultFalse: util.defaultEquals(1, '1'),
  indexOf: indexOf([1, 2, 1], 2),
  lastIndexOf: lastIndexOf([1, 2, 1], 1),
  removed: remove(mutable, 2),
  afterRemove: mutable,
  frequency: frequency([1, 2, 1], 1),
  arraysEqual: equals([1, 2], [1, 2]),
  overrideIndex: indexOf(['A'], 'a', (a, b) => a.toLowerCase() === b.toLowerCase()),
};
