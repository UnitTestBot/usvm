"use strict";

const { loadConfiguration } = require("./config.cjs");
const { decodeMethodInput } = require("./type-decoder.cjs");

const configuration = loadConfiguration();

module.exports.fuzz = function fuzz(data) {
  const args = decodeMethodInput(data, configuration.method);
  return configuration.invoke(args);
};
