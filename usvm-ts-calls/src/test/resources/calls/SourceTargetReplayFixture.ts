export function inlineChoose(value: number): number { if (value > 0) { return 1; } return 0; }

export function throwsAtTarget(): never {
  throw new Error('expected');
}

export function checksArgumentCount(): number {
  if (arguments.length !== 0) {
    return 1;
  }
  return 0;
}

export function bareConditionalCompletedReturn(value: number): void {
  if (value > 0) return;
}

export function finallyOverridesCompletedReturn(): number {
  try {
    return 11;
  } finally {
    return 22;
  }
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

export function unbracedConditional(value: number): number {
  if (value > 0) return 1;
  return 0;
}

export function unbracedLoop(value: number): void {
  for (let index = 0; index < value; index++) value += 0;
}

export function labeledLoop(value: number): void {
  outer: for (let index = 0; index < value; index++) continue outer;
}

export function returnExpressionRunsOnce(): number {
  let executions = 0;
  const once = (): number => { executions++; return 7; };
  try {
    return once();
  } finally {
    while (true) { break; }
    try { throw new Error('locally caught'); } catch { /* pending return survives */ }
    if (executions !== 1) throw new Error('expression evaluated more than once');
  }
}

export function finallyBreakCancelsReturn(): number | undefined {
  while (true) {
    try {
      return 1;
    } finally {
      break;
    }
  }
}

export function finallyThrowCaughtOutside(): number | undefined {
  try {
    try { return 1; } finally { throw new Error('cancel return'); }
  } catch { /* implicit function completion does not complete the selected return */ }
}
