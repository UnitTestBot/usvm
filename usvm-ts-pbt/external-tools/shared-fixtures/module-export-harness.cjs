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
  return target(...normalizeArguments(args));
};

exports.toCorpusCase = function toCorpusCase(args) {
  return { receiver: undefined, arguments: normalizeArguments(args) };
};

function normalizeArguments(args) {
  const minRaw = process.env.USVM_NUMBER_MIN;
  const maxRaw = process.env.USVM_NUMBER_MAX;
  if (minRaw === undefined && maxRaw === undefined) return args;
  if (minRaw === undefined || maxRaw === undefined) {
    throw new Error("USVM_NUMBER_MIN and USVM_NUMBER_MAX must be set together");
  }
  const min = Number(minRaw);
  const max = Number(maxRaw);
  if (!Number.isFinite(min) || !Number.isFinite(max) || min > max) {
    throw new Error("USVM_NUMBER_MIN/MAX must be finite and ordered");
  }
  return args.map((value) => {
    if (typeof value !== "number") return value;
    if (!Number.isFinite(value)) return 0;
    return Math.max(min, Math.min(max, Math.trunc(value)));
  });
}

exports.normalizeArguments = normalizeArguments;
