import { record } from '../record.mjs';
import * as provider from './provider.mjs';

record('explicit-absent-entry:init');
export const result = {
  present: provider.present,
  missing: provider.missing,
};
