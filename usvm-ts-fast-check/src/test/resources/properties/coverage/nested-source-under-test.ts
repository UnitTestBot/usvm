export function classifyNested(value: number): string {
  if (value > 0) {
    if (value > 10) {
      return 'large';
    } else {
      return 'small';
    }
  } else {
    return 'non-positive';
  }
}
