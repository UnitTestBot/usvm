import { record } from '../record.mjs';
import { valueA } from './a.mjs';

record('cycle-b:read-a-before-init');
export const valueB = `b:${valueA}`;
