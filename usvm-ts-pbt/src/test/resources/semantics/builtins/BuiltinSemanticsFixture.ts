"use strict";

/*
 * This file deliberately uses the JavaScript-compatible subset of TypeScript.
 * Node can execute it directly while the same source remains valid TypeScript
 * input for a future EtsIR differential fixture.
 */

function arrayIsArray(subject) {
  return Array.isArray(subject);
}

function objectToStringTag(subject) {
  return Object.prototype.toString.call(subject);
}

function objectHasOwn(subject, key) {
  return Object.prototype.hasOwnProperty.call(subject, key);
}

function propertyIn(subject, key) {
  return key in subject;
}

function mapSet(subject, key, value) {
  return subject.set(key, value);
}

function mapGet(subject, key) {
  return subject.get(key);
}

function mapHas(subject, key) {
  return subject.has(key);
}

function mapSize(subject) {
  return subject.size;
}

function truthy(subject) {
  return Boolean(subject);
}

// Real static-runtime pattern from typescript-algorithms, line 61.
function flattenReduceArrayDecision(item) {
  return Array.isArray(item);
}

// Real Map.get + truthiness pattern from prime_factorization.ts, lines 21-22.
function factorizeTailDecision(result, n) {
  const occurrence = result.get(n);
  return !occurrence;
}

module.exports = Object.freeze({
  arrayIsArray,
  objectToStringTag,
  objectHasOwn,
  propertyIn,
  mapSet,
  mapGet,
  mapHas,
  mapSize,
  truthy,
  flattenReduceArrayDecision,
  factorizeTailDecision,
});
