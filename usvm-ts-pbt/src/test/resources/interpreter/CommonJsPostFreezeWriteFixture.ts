"use strict";

function directAdd(left, right) {
  return left + right;
}

const exported = {};
const frozen = Object.freeze(exported);
exported.directAdd = directAdd;
module.exports = frozen;
