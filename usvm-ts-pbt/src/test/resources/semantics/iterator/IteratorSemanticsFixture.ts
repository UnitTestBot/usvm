"use strict";

/*
 * JavaScript-compatible TypeScript. Node executes this file directly while a
 * later integration package can feed the same source to the EtsIR frontend.
 */

const trackedState = new WeakMap();

function createTrackedIterable(values, hasReturn) {
  const subject = {
    [Symbol.iterator]() {
      const state = trackedState.get(subject);
      state.iteratorGets += 1;
      let index = 0;
      let closed = false;
      const iterator = {
        next() {
          state.nextCalls += 1;
          if (closed || index >= values.length) return { value: undefined, done: true };
          const value = values[index];
          index += 1;
          return { value, done: false };
        },
        [Symbol.iterator]() {
          return this;
        },
      };
      if (hasReturn) {
        iterator.return = function iteratorReturn() {
          state.returnCalls += 1;
          closed = true;
          const result = { value: "closed", done: true };
          if (state.events) state.events.push({ type: "return", result });
          return result;
        };
      }
      return iterator;
    },
  };
  trackedState.set(subject, {
    iteratorGets: 0,
    nextCalls: 0,
    returnCalls: 0,
    events: null,
  });
  return subject;
}

function stateOf(subject) {
  const state = trackedState.get(subject);
  if (!state) throw new Error("subject is not a tracked iterable");
  return {
    iteratorGets: state.iteratorGets,
    nextCalls: state.nextCalls,
    returnCalls: state.returnCalls,
  };
}

function beginTracked(subject, events) {
  const state = trackedState.get(subject);
  if (!state) throw new Error("subject is not a tracked iterable");
  state.events = events;
}

function nextSequence(subject, count) {
  const events = [{ type: "get" }];
  const iterator = subject[Symbol.iterator]();
  const results = [];
  for (let index = 0; index < count; index += 1) {
    const result = iterator.next();
    results.push(result);
    events.push({ type: "next", result });
  }
  return { result: results, events };
}

function iteratorSelf(subject) {
  const events = [{ type: "get" }];
  const iterator = subject[Symbol.iterator]();
  const same = iterator[Symbol.iterator]() === iterator;
  events.push({ type: "self", same });
  return { result: same, events };
}

function collectForOf(subject) {
  const events = [{ type: "get" }];
  const values = [];
  for (const value of subject) {
    values.push(value);
    events.push({ type: "value", value });
  }
  events.push({ type: "complete" });
  return { result: values, events };
}

function collectTracked(subject) {
  const events = [{ type: "get" }];
  beginTracked(subject, events);
  const values = [];
  for (const value of subject) {
    values.push(value);
    events.push({ type: "value", value });
  }
  events.push({ type: "complete" });
  return { result: { values, stats: stateOf(subject) }, events };
}

function breakTracked(subject) {
  const events = [{ type: "get" }];
  beginTracked(subject, events);
  const values = [];
  let broke = false;
  for (const value of subject) {
    values.push(value);
    events.push({ type: "value", value });
    broke = true;
    break;
  }
  events.push({ type: broke ? "break" : "complete" });
  return { result: { values, broke, stats: stateOf(subject) }, events };
}

function firstValue(subject, events) {
  for (const value of subject) {
    events.push({ type: "value", value });
    return value;
  }
  return undefined;
}

function returnTracked(subject) {
  const events = [{ type: "get" }];
  beginTracked(subject, events);
  const value = firstValue(subject, events);
  events.push({ type: "functionReturn" });
  return { result: { value, stats: stateOf(subject) }, events };
}

function throwInside(subject, events) {
  for (const value of subject) {
    events.push({ type: "value", value });
    throw new Error("boom");
  }
}

function throwTracked(subject) {
  const events = [{ type: "get" }];
  beginTracked(subject, events);
  let message = null;
  try {
    throwInside(subject, events);
  } catch (error) {
    message = error.message;
    events.push({ type: "caught", message });
  }
  return { result: { error: message, stats: stateOf(subject) }, events };
}

function directReturn(subject) {
  const events = [{ type: "get" }];
  beginTracked(subject, events);
  const iterator = subject[Symbol.iterator]();
  const first = iterator.next();
  events.push({ type: "next", result: first });
  const close = iterator.return();
  return { result: { first, close, stats: stateOf(subject) }, events };
}

// Frozen source shapes from the three broad-campaign iterator methods.
function findMinimumAfterIterator(nums) {
  if (nums.length === 0) throw new Error("array must have length of 1 or greater");
  let minimumSeen = nums[0];
  for (const num of nums) {
    if (num < minimumSeen) minimumSeen = num;
  }
  return minimumSeen;
}

function flattenRecursiveAfterIterator(array) {
  const result = [];
  for (const item of array) {
    if (Array.isArray(item)) result.push(...flattenRecursiveAfterIterator(item));
    else result.push(item);
  }
  return result;
}

function collectionForEach(array, callback) {
  for (const element of array) {
    if (callback(element) === false) return;
  }
}

module.exports = Object.freeze({
  createTrackedIterable,
  nextSequence,
  iteratorSelf,
  collectForOf,
  collectTracked,
  breakTracked,
  returnTracked,
  throwTracked,
  directReturn,
  findMinimumAfterIterator,
  flattenRecursiveAfterIterator,
  collectionForEach,
});
