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

export function completesReturnExpression(value: string): string {
  return /* before expression */ value.trim() /* after expression */
}

function throwFromExpression(): never {
  throw new Error('expression failed');
}

export function expressionThrowsBeforeReturnCompletes(): string {
  return throwFromExpression();
}

export function finallyThrowsAfterReturnExpression(): string {
  try {
    return 'value'.trim();
  } finally {
    throw new Error('finally failed');
  }
}

export function completesBareReturn(): void {
  return /* no expression */
}

export function bareConditionalCompletedReturn(value: number): void {
  if (value > 0) return;
}

export function recursiveCompletedReturn(value: number): number {
  if (value === 0) {
    return 11;
  }
  recursiveCompletedReturn(0);
  return 22;
}

function partialReturnFailure(): never {
  throw new Error('partial return failed');
}

export function partialCaughtReturn(value: number): number {
  try {
    return value + partialReturnFailure();
  } catch {
    return 0;
  }
}

export function finallyOverridesCompletedReturn(): number {
  try {
    return 11;
  } finally {
    return 22;
  }
}

export function finallyBreaksCompletedReturn(): number | undefined {
  while (true) {
    try {
      return 11;
    } finally {
      break;
    }
  }
}
