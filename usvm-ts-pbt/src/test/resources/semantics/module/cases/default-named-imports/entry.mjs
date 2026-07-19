import { record } from '../record.mjs';
import scale, { offset as plusOne } from './library.mjs';

record('default-named-entry:init');
export const result = {
  defaultResult: scale(5),
  namedResult: plusOne(5),
};
