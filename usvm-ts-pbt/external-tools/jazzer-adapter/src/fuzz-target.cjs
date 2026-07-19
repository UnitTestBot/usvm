"use strict";

const { loadConfiguration } = require("./config.cjs");
const { decodeMethodInvocation } = require("./type-decoder.cjs");

let configuration;

module.exports.fuzz = function fuzz(data) {
  configuration ??= loadConfiguration();
  return invokeForFuzz(configuration, data);
};

function invokeForFuzz(configuration, data) {
  const hooks = configuration.harness ?? configuration;
  const invocation = decodeMethodInvocation(data, configuration.method, hooks);
  let invoke;
  if (typeof hooks.invokeCase === "function" && invocation.encoding === "etc-v2-envelope") {
    invoke = () => hooks.invokeCase({ receiver: invocation.receiver, arguments: invocation.arguments });
  } else {
    if (invocation.receiver !== undefined) {
      throw new Error("receiver_requires_invokeCase: harness.invokeCase({ receiver, arguments }) is required");
    }
    invoke = () => configuration.invoke(invocation.arguments);
  }
  try {
    return invoke();
  } catch (error) {
    if (!configuration.ignoreExceptions) throw error;
    return undefined;
  }
}

module.exports.invokeForFuzz = invokeForFuzz;
