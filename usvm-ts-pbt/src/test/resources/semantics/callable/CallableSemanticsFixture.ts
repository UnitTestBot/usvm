"use strict";

const callableLibrary = require("./CallableSemanticsLibrary.ts");

function directAdd(left, right) {
  return left + right;
}

const topLevelArrow = (value) => value * 2;

function fieldMultiply(left, right) {
  return left * right;
}

function readBase(delta) {
  return this.base + delta;
}

function recursiveFactorial(value) {
  return value <= 1 ? 1 : value * recursiveFactorial(value - 1);
}

function arityPair(first, second) {
  const renderedSecond = second === undefined ? "undefined" : String(second);
  return `${arguments.length}:${first}:${renderedSecond}`;
}

class ReceiverBox {
  constructor(base) {
    this.base = base;
  }

  add(delta) {
    return this.base + delta;
  }

  static staticSum(left, right) {
    return left + right;
  }
}

function invokeDirect(callable, args) {
  return callable(...args);
}

function invokeField(receiver, fieldName, args) {
  return receiver[fieldName](...args);
}

function invokeWithCall(callable, receiver, args) {
  return callable.call(receiver, ...args);
}

// The factories below make the rejected boundary source-addressable. Their
// results are deliberately represented as ETC `unrepresentable/function`, not
// guessed, erased, or converted to undefined.
function makeCapturedMutableClosure() {
  let counter = 0;
  return () => ++counter;
}

function makeLexicalThisArrow() {
  return () => this.base;
}

function makeBoundCallable() {
  return readBase.bind({ base: 40 });
}

function makeProxyCallable() {
  return new Proxy(directAdd, { apply: Reflect.apply });
}

async function asyncIdentity(value) {
  return value;
}

function* generatorIdentity(value) {
  yield value;
}

function dynamicCallableLookup(holder, propertyName) {
  return holder[propertyName];
}

module.exports = Object.freeze({
  ReceiverBox,
  arityPair,
  asyncIdentity,
  callableLibrary,
  directAdd,
  dynamicCallableLookup,
  fieldMultiply,
  generatorIdentity,
  invokeDirect,
  invokeField,
  invokeWithCall,
  makeBoundCallable,
  makeCapturedMutableClosure,
  makeLexicalThisArrow,
  makeProxyCallable,
  readBase,
  recursiveFactorial,
  topLevelArrow,
});
