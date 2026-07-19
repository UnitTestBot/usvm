import { record } from '../record.mjs';
import { counter, increment } from './barrel.mjs';

record('cross-file-entry:init');
const before = counter;
increment();
increment();
export const result = { before, after: counter };
