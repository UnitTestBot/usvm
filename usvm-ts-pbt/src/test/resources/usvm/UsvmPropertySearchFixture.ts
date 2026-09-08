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

export function nonBooleanProperty(value: number): number {
  return value;
}

export async function asyncValidProperty(value: number): Promise<boolean> {
  return value === value;
}
