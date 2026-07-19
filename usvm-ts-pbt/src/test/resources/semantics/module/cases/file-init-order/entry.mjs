import { record } from '../record.mjs';
import { left } from './left.mjs';
import { right } from './right.mjs';

record('entry:init');
export const result = { left, right };
