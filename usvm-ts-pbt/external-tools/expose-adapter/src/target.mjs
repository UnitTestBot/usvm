import { parameterInitialValue } from "./types.mjs";

export function methodParameters(method) {
  return method.parameters ?? method.parameterTypes.map((type, index) => ({
    index, name: `p${index}`, type, optional: false, rest: false,
  }));
}

export function defaultArguments(method) {
  return methodParameters(method).map((parameter) => {
    const initial = parameterInitialValue(parameter.type);
    return parameter.rest && !Array.isArray(initial) ? [initial] : initial;
  });
}

export function symbolName(index) {
  return `usvm_arg_${index}`;
}

export function generateTarget({ harnessPath, method, initialArguments = defaultArguments(method) }) {
  const parameters = methodParameters(method);
  if (initialArguments.length !== parameters.length) {
    throw new Error(`expected ${parameters.length} initial arguments, got ${initialArguments.length}`);
  }
  initialArguments.forEach((value, index) => {
    const encoded = JSON.stringify(value);
    if (encoded === undefined) throw new Error(`initial argument ${index} is not JSON-encodable`);
  });
  const symbols = initialArguments.map((value, index) =>
    `S$.symbol(${JSON.stringify(symbolName(index))}, ${JSON.stringify(value)})`).join(",\n  ");
  return `"use strict";\n\n` +
    `var S$ = require("S$");\n` +
    `var harness = require(${JSON.stringify(harnessPath)});\n` +
    `var invoke = harness.invoke || harness.default || harness;\n` +
    `if (typeof invoke !== "function") throw new Error("harness must export invoke(args)");\n` +
    `var args = [\n  ${symbols}\n];\n` +
    `invoke(args);\n`;
}
