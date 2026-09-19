export function choose(value: number): number {
  if (value > 0) {
    return 1;
  }

  return 0;
}

export function inlineChoose(value: number): number { if (value > 0) { return 1; } return 0; }

export function throwsAtTarget(): never {
  throw new Error('expected');
}

export function importOnlyTarget(): number {
  return 7;
}

const importedTargetValue = importOnlyTarget();

export function skipsImportOnlyTarget(): number {
  return importedTargetValue;
}
