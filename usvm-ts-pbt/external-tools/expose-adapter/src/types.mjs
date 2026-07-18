export function parameterInitialValue(rawType) {
  const type = stripOuterParentheses(String(rawType).trim());
  const union = splitTopLevel(type, "|");
  if (union.length > 1) {
    for (const candidate of union) {
      try {
        return parameterInitialValue(candidate);
      } catch {
        // Try the next representable member.
      }
    }
    throw new Error(`no ExpoSE-representable member in ${type}`);
  }
  if (type.endsWith("[]")) return [parameterInitialValue(type.slice(0, -2))];
  const array = /^(?:Array|ReadonlyArray)<(.+)>$/.exec(type);
  if (array) return [parameterInitialValue(array[1])];
  if (type.startsWith("[") && type.endsWith("]")) {
    return splitTopLevel(type.slice(1, -1), ",").map(parameterInitialValue);
  }
  if (/^"(?:[^"\\]|\\.)*"$/.test(type)) return JSON.parse(type);
  if (type === "true") return true;
  if (type === "false") return false;
  if (/^[+-]?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][+-]?\d+)?$/.test(type)) return Number(type);
  switch (type) {
    case "number": return 0;
    case "boolean": return false;
    case "string": return "";
    case "any":
    case "unknown": return 0;
    case "null":
    case "undefined":
    case "void":
    case "never": throw new Error(`ExpoSE cannot make a ${type} seed symbolic`);
    default: return {};
  }
}

export function stripOuterParentheses(value) {
  if (!value.startsWith("(") || !value.endsWith(")")) return value;
  let depth = 0;
  for (let index = 0; index < value.length; index += 1) {
    if (value[index] === "(") depth += 1;
    if (value[index] === ")") depth -= 1;
    if (depth === 0 && index !== value.length - 1) return value;
  }
  return stripOuterParentheses(value.slice(1, -1).trim());
}

export function splitTopLevel(value, separator) {
  const result = [];
  let start = 0;
  let angle = 0;
  let square = 0;
  let round = 0;
  let quoted = false;
  let escaped = false;
  for (let index = 0; index < value.length; index += 1) {
    const char = value[index];
    if (quoted) {
      if (escaped) escaped = false;
      else if (char === "\\") escaped = true;
      else if (char === '"') quoted = false;
      continue;
    }
    if (char === '"') quoted = true;
    else if (char === "<") angle += 1;
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
