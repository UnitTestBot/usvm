import * as util from "./ConcreteModuleUtil";

export function moduleDefaultEquals(left: unknown, right: unknown): boolean {
  const equals = util.defaultEquals;
  return equals(left, right);
}

export function moduleExportIdentity(): boolean {
  return util.defaultEquals === util.defaultEquals;
}

export function literalTypedParameter(value: "./ConcreteModuleUtil"): string {
  return value;
}

export function directAdd(left: number, right: number): number {
  return left + right;
}

export const topLevelArrow = (value: number): number => value * 2;

function notExported(value: number): number {
  return value;
}

export function fieldMultiply(left: number, right: number): number {
  return left * right;
}

export function readBase(delta: number): number {
  return (this as { base: number }).base + delta;
}

export function invokeDirect(
  callable: (left: number, right: number) => number,
  left: number,
  right: number,
): number {
  return callable(left, right);
}

export function invokeField(
  receiver: { operation: (left: number, right: number) => number },
  left: number,
  right: number,
): number {
  return receiver.operation(left, right);
}

export function extractAndInvoke(
  receiver: { operation: (delta: number) => number },
  delta: number,
): number {
  const operation = receiver.operation;
  return operation(delta);
}

export function extractComputedAndInvoke(
  receiver: { operation: (delta: number) => number },
  delta: number,
): number {
  const operation = receiver["operation"];
  return operation(delta);
}

export function invokeComputedUnaryField(
  receiver: { operation: (delta: number) => number },
  delta: number,
): number {
  return receiver["operation"](delta);
}

export function invokeUnaryField(
  receiver: { operation: (delta: number) => number },
  delta: number,
): number {
  return receiver.operation(delta);
}

export function invokeBoxAdd(receiver: ReceiverBox, delta: number): number {
  return receiver.add(delta);
}

export function instanceMethodIdentity(receiver: ReceiverBox): boolean {
  return receiver.add === receiver.add;
}

export function invokeWithCall(
  callable: (delta: number) => number,
  receiver: { base: number },
  delta: number,
): number {
  return callable.call(receiver, delta);
}

export function recursiveFactorial(value: number): number {
  return value <= 1 ? 1 : value * recursiveFactorial(value - 1);
}

export function arityLength(first: number, second?: number): number {
  return arguments.length * 100 + first * 10 + (second === undefined ? 0 : second);
}

export function continueUntilTwo(value: number): boolean {
  return value !== 2;
}

export function readInherited(subject: { inherited?: number }): number | undefined {
  return subject.inherited;
}

export function spliceWithoutArguments(values: number[]): number {
  return values.splice().length * 100 + values.length;
}

export function spliceWithOnlyStart(values: number[], start: number): number {
  return values.splice(start).length * 100 + values.length;
}

export function freezeObject(subject: object): object {
  return Object.freeze(subject);
}

export function sealObject(subject: object): object {
  return Object.seal(subject);
}

export function preventObjectExtensions(subject: object): object {
  return Object.preventExtensions(subject);
}

export class ReceiverBox {
  base: number;

  constructor(base: number) {
    this.base = base;
  }

  add(delta: number): number {
    return this.base + delta;
  }

  static staticSum(left: number, right: number): number {
    return left + right;
  }
}

export function iteratorSum(values: number[]): number {
  let sum = 0;
  for (const value of values) sum += value;
  return sum;
}

export function iteratorSelf(values: number[]): boolean {
  const iterator = values[Symbol.iterator]();
  return iterator[Symbol.iterator]() === iterator;
}

export function iteratorReturnIsAbsent(values: number[]): boolean {
  const iterator = values[Symbol.iterator]();
  return iterator.return === undefined;
}

export function iteratorFunctionsAreObservable(values: number[]): boolean {
  const factory = values[Symbol.iterator];
  const iterator = values[Symbol.iterator]();
  return typeof factory === "function" &&
    typeof iterator.next === "function" &&
    Object.prototype.toString.call(iterator.next) === "[object Function]";
}

export function iteratorFunctionIdentity(values: number[]): boolean {
  const iterator = values[Symbol.iterator]();
  return values[Symbol.iterator] === values[Symbol.iterator] &&
    iterator.next === iterator.next &&
    iterator[Symbol.iterator] === iterator[Symbol.iterator];
}

export function arrayIteratorObservesAppend(values: number[]): number {
  const iterator = values[Symbol.iterator]();
  iterator.next();
  values.push(42);
  return iterator.next().value;
}

export function mapIteratorObservesOverwrite(values: Map<number, number>): number {
  const iterator = values.values();
  values.set(1, 42);
  return iterator.next().value;
}

export function setIteratorObservesAppend(values: Set<number>): number {
  const iterator = values.values();
  iterator.next();
  values.add(42);
  return iterator.next().value;
}

export function arrayIteratorDoneIsSticky(values: number[]): boolean {
  const iterator = values[Symbol.iterator]();
  iterator.next();
  const exhausted = iterator.next().done;
  values.push(42);
  return exhausted === true && iterator.next().done === true;
}

export function mapIteratorDoneIsSticky(values: Map<number, number>): boolean {
  const iterator = values.values();
  iterator.next();
  const exhausted = iterator.next().done;
  values.set(2, 42);
  return exhausted === true && iterator.next().done === true;
}

export function setIteratorDoneIsSticky(values: Set<number>): boolean {
  const iterator = values.values();
  iterator.next();
  const exhausted = iterator.next().done;
  values.add(42);
  return exhausted === true && iterator.next().done === true;
}

export function iteratorStoredInField(this: { values: number[] }): Iterator<number> {
  return this.values[Symbol.iterator]();
}

export function customIterableFirst(subject: { [Symbol.iterator]: () => Iterator<number> }): number {
  return subject[Symbol.iterator]().next().value;
}

export function mapKeySum(values: Map<number, number>): number {
  let sum = 0;
  for (const key of values.keys()) sum += key;
  return sum;
}

export function mapDefaultEntrySum(values: Map<number, number>): number {
  let sum = 0;
  for (const entry of values) sum += entry[0] + entry[1];
  return sum;
}

export function setValueSum(values: Set<number>): number {
  let sum = 0;
  for (const value of values.values()) sum += value;
  return sum;
}

export function setDefaultValueSum(values: Set<number>): number {
  let sum = 0;
  for (const value of values) sum += value;
  return sum;
}
