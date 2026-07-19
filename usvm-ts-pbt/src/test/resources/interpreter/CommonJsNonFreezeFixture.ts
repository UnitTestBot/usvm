"use strict";

function expose(value) {
  return value;
}

function directAdd(left, right) {
  return left + right;
}

module.exports = expose({ directAdd });
