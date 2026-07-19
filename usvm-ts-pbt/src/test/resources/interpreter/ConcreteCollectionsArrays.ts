/*
 * Production regression fixture preserving the relevant source shapes from
 * basarat/typescript-collections@309bb1b6955b, src/lib/arrays.ts (MIT).
 */
import * as util from "./ConcreteModuleUtil";

export function indexOf<T>(array: T[], item: T, equalsFunction?: (a: T, b: T) => boolean): number {
  const equals = equalsFunction || util.defaultEquals;
  const length = array.length;
  for (let i = 0; i < length; i++) {
    if (equals(array[i], item)) return i;
  }
  return -1;
}

export function lastIndexOf<T>(array: T[], item: T, equalsFunction?: (a: T, b: T) => boolean): number {
  const equals = equalsFunction || util.defaultEquals;
  const length = array.length;
  for (let i = length - 1; i >= 0; i--) {
    if (equals(array[i], item)) return i;
  }
  return -1;
}

export function remove<T>(array: T[], item: T, equalsFunction?: (a: T, b: T) => boolean): boolean {
  const index = indexOf(array, item, equalsFunction);
  if (index < 0) return false;
  array.splice(index, 1);
  return true;
}

export function frequency<T>(array: T[], item: T, equalsFunction?: (a: T, b: T) => boolean): number {
  const equals = equalsFunction || util.defaultEquals;
  const length = array.length;
  let freq = 0;
  for (let i = 0; i < length; i++) {
    if (equals(array[i], item)) freq++;
  }
  return freq;
}

export function equals<T>(array1: T[], array2: T[], equalsFunction?: (a: T, b: T) => boolean): boolean {
  const equalsValue = equalsFunction || util.defaultEquals;
  if (array1.length !== array2.length) return false;
  const length = array1.length;
  for (let i = 0; i < length; i++) {
    if (!equalsValue(array1[i], array2[i])) return false;
  }
  return true;
}

export function forEach<T>(array: T[], callback: (element: T) => boolean | void): void {
  for (const element of array) {
    if (callback(element) === false) return;
  }
}
