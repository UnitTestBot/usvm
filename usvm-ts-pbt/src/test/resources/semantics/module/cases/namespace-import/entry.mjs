import { record } from '../record.mjs';
import * as util from './util.mjs';

record('namespace-entry:init');
export const result = {
  callableType: typeof util.defaultEquals,
  equal: util.defaultEquals(3, 3),
  unequal: util.defaultEquals(3, '3'),
  marker: util.marker,
};
