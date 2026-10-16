export function logical(value: number): number {
  let result = 0;
  if (value > 0 && value < 10) {
    result += 1;
  }
  return result;
}
