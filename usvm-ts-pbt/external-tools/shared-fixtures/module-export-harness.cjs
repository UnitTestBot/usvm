"use strict";

const { resolve } = require("node:path");

exports.invoke = function invoke(args) {
  const modulePath = process.env.USVM_COMPILED_MODULE;
  const exportName = process.env.USVM_MODULE_EXPORT;
  if (!modulePath || !exportName) {
    throw new Error("USVM_COMPILED_MODULE and USVM_MODULE_EXPORT are required");
  }
  const loaded = require(resolve(modulePath));
  const target = loaded[exportName];
  if (typeof target !== "function") {
    throw new Error(`module export '${exportName}' is not a function`);
  }
  return target(...args);
};
