import { record } from '../record.mjs';

record('default-named-library:init');
export default function scale(value) {
  record(`default-named-library:default:${value}`);
  return value * 2;
}
export function offset(value) {
  record(`default-named-library:named:${value}`);
  return value + 1;
}
