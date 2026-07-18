export function classifyManifest(manifest) {
  if (manifest?.schemaVersion !== 1 || !Array.isArray(manifest.methods)) {
    throw new Error("expected target manifest schemaVersion 1");
  }
  const methods = manifest.methods.map((method) => {
    const parameters = (method.parameters ?? method.parameterTypes.map((type, index) => ({ index, type })))
      .map(classifyParameter);
    const reasons = [];
    if (method.entryKind === "instance") reasons.push("instance receiver construction needs a custom ES5 harness");
    parameters.filter((parameter) => parameter.status !== "automatic").forEach((parameter) => {
      reasons.push(`parameter ${parameter.index} (${parameter.type}): ${parameter.reason}`);
    });
    const status = reasons.length === 0 ? "automatic-symbol-declarations" : "custom-harness";
    return {
      methodId: method.methodId,
      status,
      symbolicDeclarations: parameters.map((parameter) => parameter.declaration),
      reasons,
    };
  });
  return {
    schemaVersion: 1,
    tool: "gillian-js-feasibility",
    constraints: [
      "source must be transpiled/bundled to ES5 accepted by JS-2-GIL",
      "successful path models are not exported by upstream wpst JSON UI",
      "ETC export requires an upstream model-export patch or per-target failing assertions",
    ],
    summary: {
      methods: methods.length,
      automaticSymbolDeclarations: methods.filter((method) => method.status === "automatic-symbol-declarations").length,
      customHarness: methods.filter((method) => method.status === "custom-harness").length,
    },
    methods,
  };
}

function classifyParameter(parameter) {
  const type = stripOuterParentheses(String(parameter.type).trim());
  const base = { index: parameter.index, type };
  if (parameter.rest) {
    return { ...base, status: "custom-harness", declaration: null, reason: "rest arrays need explicit heap shape" };
  }
  if (parameter.optional || splitTopLevel(type, "|").length > 1) {
    return { ...base, status: "custom-harness", declaration: `symb()`, reason: "optional/union type needs an explicit type partition" };
  }
  if (type === "number" || isNumericLiteral(type)) {
    return { ...base, status: "automatic", declaration: "symb_number()", reason: null };
  }
  if (type === "boolean" || type === "true" || type === "false") {
    return { ...base, status: "automatic", declaration: "symb_bool()", reason: null };
  }
  if (type === "string" || /^"/.test(type)) {
    return { ...base, status: "automatic", declaration: "symb_string()", reason: null };
  }
  if (type === "any" || type === "unknown") {
    return { ...base, status: "automatic", declaration: "symb()", reason: null };
  }
  if (type.endsWith("[]") || /^(?:Array|ReadonlyArray)</.test(type) || type.startsWith("[")) {
    return { ...base, status: "custom-harness", declaration: null, reason: "symbolic array length/elements need an explicit bounded heap shape" };
  }
  return { ...base, status: "custom-harness", declaration: null, reason: "object/class/nullish construction is not inferable from the manifest type" };
}

function isNumericLiteral(type) {
  return /^[+-]?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][+-]?\d+)?$/.test(type);
}

function stripOuterParentheses(value) {
  if (!value.startsWith("(") || !value.endsWith(")")) return value;
  let depth = 0;
  for (let index = 0; index < value.length; index += 1) {
    if (value[index] === "(") depth += 1;
    if (value[index] === ")") depth -= 1;
    if (depth === 0 && index !== value.length - 1) return value;
  }
  return stripOuterParentheses(value.slice(1, -1).trim());
}

function splitTopLevel(value, separator) {
  const result = [];
  let start = 0;
  let angle = 0;
  let square = 0;
  let round = 0;
  for (let index = 0; index < value.length; index += 1) {
    const char = value[index];
    if (char === "<") angle += 1;
    else if (char === ">") angle -= 1;
    else if (char === "[") square += 1;
    else if (char === "]") square -= 1;
    else if (char === "(") round += 1;
    else if (char === ")") round -= 1;
    else if (char === separator && angle === 0 && square === 0 && round === 0) {
      result.push(value.slice(start, index).trim());
      start = index + 1;
    }
  }
  result.push(value.slice(start).trim());
  return result.filter(Boolean);
}
