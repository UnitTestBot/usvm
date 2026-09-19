export function shiftTarget(value: number): number {
  const values = [value];
  const removed = values.shift();
  if (removed === 42) {
    return 1;
  }

  return 0;
}
