export function record(event) {
  if (!Array.isArray(globalThis.__usvmModuleSemanticsTrace)) {
    throw new Error('module semantics trace was not initialized by the runner');
  }
  globalThis.__usvmModuleSemanticsTrace.push(event);
}
