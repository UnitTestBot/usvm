"use strict";

const Object = {
  freeze(value) {
    return value;
  },
};

function directAdd(left, right) {
  return left + right;
}

module.exports = Object.freeze({ directAdd });
