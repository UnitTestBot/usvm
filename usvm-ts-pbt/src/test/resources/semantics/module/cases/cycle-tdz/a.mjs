import { record } from '../record.mjs';
import { valueB } from './b.mjs';

record('cycle-a:read-b');
export const valueA = `a:${valueB}`;
