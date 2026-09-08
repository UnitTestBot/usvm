export function acceptsBoolean(value: boolean): boolean {
  return value;
}

export function acceptsNumber(value: number): boolean {
  return value > 0;
}

export function acceptsString(value: string): boolean {
  return value.length > 0;
}

export function acceptsOptionalNumber(value: number | undefined): boolean {
  return value === undefined || value > 0;
}

export function acceptsTuple(value: [number, string]): boolean {
  return value.length === 2;
}

export function acceptsNumberBooleanTuple(value: [number, boolean]): boolean {
  return value !== undefined;
}

export function acceptsNumberArray(value: number[]): boolean {
  return value.length > 0;
}

export function returnsNumber(value: number): number {
  return value;
}
