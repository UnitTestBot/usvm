export function validProperty(value: number): boolean {
  return value === value;
}

export function violatedProperty(value: number): boolean {
  return value !== 2;
}

export function positive(value: number): boolean {
  return value > 0;
}

export function signedOneProperty(value: number): boolean {
  return value !== 1 && value !== -1;
}

export function falsePrecondition(_value: number): boolean {
  return false;
}

export function throwingPrecondition(_value: number): boolean {
  throw "precondition";
}

function identity(value: number): number {
  return value;
}

export function relationalProperty(value: number): boolean {
  return identity(value) === identity(value + 1);
}

export function unexpectedException(_value: number): boolean {
  throw "unexpected";
}

export function assertionFailure(_value: number): boolean {
  throw "AssertionError: expected non-zero";
}

export function falseStringProperty(_value: string): boolean {
  return false;
}

export function falseBooleanProperty(_value: boolean): boolean {
  return false;
}

export function falseOptionalProperty(_value: number | undefined): boolean {
  return false;
}

export function falseTupleProperty(_value: [number, boolean]): boolean {
  return false;
}

export function falseNestedTupleProperty(_value: [number | undefined, boolean]): boolean {
  return false;
}

export function falseArrayProperty(_value: number[]): boolean {
  return false;
}

export function mixedUnsupportedProperty(value: number): boolean {
  if (value === 0) {
    return false;
  }

  return Math.sin(value) > 0;
}

export function caughtDirectProperty(_value: number): boolean {
  try {
    throw "expected";
  } catch {
    return true;
  }
}

export function caughtHelperProperty(value: number): boolean {
  try {
    return throwingHelper(value);
  } catch {
    return true;
  }
}

function throwingHelper(_value: number): boolean {
  throw "expected";
}

export function optionalArrayProperty(value: number[] | undefined): boolean {
  return value === undefined || value[0] === 2;
}

export function nonBooleanProperty(value: number): number {
  return value;
}

export async function asyncValidProperty(value: number): Promise<boolean> {
  return value === value;
}
