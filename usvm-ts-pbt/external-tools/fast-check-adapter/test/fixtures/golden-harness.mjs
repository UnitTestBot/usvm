class Box {
  constructor(value) {
    this.value = value;
  }
}

export function resolveCallable(reference) {
  if (reference.exportName === "Box") return Box;
  if (reference.exportName === "identity") return (value) => value;
  throw new Error(`unknown fixture callable ${reference.exportName}`);
}

export function invoke() {
  return true;
}
