import { record } from '../record.mjs';

record('state:init');
export let counter = 0;
export function increment() {
  counter += 1;
  record(`state:increment:${counter}`);
}
