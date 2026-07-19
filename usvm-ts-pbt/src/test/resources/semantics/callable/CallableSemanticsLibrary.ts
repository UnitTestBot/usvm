"use strict";

// JavaScript-compatible TypeScript on purpose: Node is one independent oracle,
// while the same source remains valid input for a later EtsIR differential.
const importedArrow = (left, right) => left * right;

function importedOffset(value) {
  return value + 40;
}

module.exports = Object.freeze({
  importedArrow,
  importedOffset,
});
