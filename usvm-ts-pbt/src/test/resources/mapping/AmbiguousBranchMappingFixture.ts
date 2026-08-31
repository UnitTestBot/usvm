export function classifiesLargePositive(value: number): boolean {
  if (value > 0) {
    if (value > 10) {
      return true;
    }
  }
  return false;
}
