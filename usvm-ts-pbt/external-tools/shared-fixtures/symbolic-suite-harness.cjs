"use strict";

const suite = require("./compiled/symbolic-suite.js");

exports.invoke = (args) => {
  const method = process.env.USVM_FIXTURE_METHOD;
  if (typeof suite[method] !== "function") throw new Error(`unknown fixture method '${method}'`);
  return suite[method](...args);
};
