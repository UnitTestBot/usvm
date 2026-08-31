export function predicate(value: number): boolean {
  return value > 0;
}

export class PredicateContainer {
  predicate(left: number, right: number): boolean {
    return left > right;
  }
}
