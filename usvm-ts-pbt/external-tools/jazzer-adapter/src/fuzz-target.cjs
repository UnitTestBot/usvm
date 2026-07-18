"use strict";

const { loadConfiguration } = require("./config.cjs");
const { decodeMethodInput } = require("./type-decoder.cjs");

let configuration;

module.exports.fuzz = function fuzz(data) {
  configuration ??= loadConfiguration();
  return invokeForFuzz(configuration, data);
};

function invokeForFuzz(configuration, data) {
  const args = decodeMethodInput(data, configuration.method);
  try {
    return configuration.invoke(args);
  } catch (error) {
    if (!configuration.ignoreExceptions) throw error;
    return undefined;
  }
}

module.exports.invokeForFuzz = invokeForFuzz;
