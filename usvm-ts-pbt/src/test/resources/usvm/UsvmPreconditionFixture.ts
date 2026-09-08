export function predicate(value: number): boolean {
  return value !== 0;
}

export function isPositive(value: number): boolean {
  return value > 0;
}

export function alwaysFalse(_value: number): boolean {
  return false;
}

export function acceptsNonPositiveOrThrows(value: number): boolean {
  if (value > 0) {
    throw new Error("positive");
  }

  return true;
}

export async function asyncIsPositive(value: number): Promise<boolean> {
  return value > 0;
}

export function returnsNumber(value: number): number {
  return value;
}
