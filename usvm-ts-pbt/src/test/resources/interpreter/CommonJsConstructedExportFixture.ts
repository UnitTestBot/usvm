"use strict";

function directAdd(left, right) {
  return left + right;
}

class ExportHolder {
  constructor() {
    this.directAdd = directAdd;
  }
}

module.exports = Object.freeze(new ExportHolder());
