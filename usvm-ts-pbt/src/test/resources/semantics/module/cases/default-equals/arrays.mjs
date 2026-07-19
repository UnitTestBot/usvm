import { record } from '../record.mjs';
import * as util from './util.mjs';

record('collections-arrays:init');

export function indexOf(array, item, equalsFunction) {
  const equals = equalsFunction || util.defaultEquals;
  for (let index = 0; index < array.length; index += 1) {
    if (equals(array[index], item)) return index;
  }
  return -1;
}

export function lastIndexOf(array, item, equalsFunction) {
  const equals = equalsFunction || util.defaultEquals;
  for (let index = array.length - 1; index >= 0; index -= 1) {
    if (equals(array[index], item)) return index;
  }
  return -1;
}

export function remove(array, item, equalsFunction) {
  const index = indexOf(array, item, equalsFunction);
  if (index < 0) return false;
  array.splice(index, 1);
  return true;
}

export function frequency(array, item, equalsFunction) {
  const equals = equalsFunction || util.defaultEquals;
  let result = 0;
  for (let index = 0; index < array.length; index += 1) {
    if (equals(array[index], item)) result += 1;
  }
  return result;
}

export function equals(left, right, equalsFunction) {
  const itemEquals = equalsFunction || util.defaultEquals;
  if (left.length !== right.length) return false;
  for (let index = 0; index < left.length; index += 1) {
    if (!itemEquals(left[index], right[index])) return false;
  }
  return true;
}
