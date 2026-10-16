type WitnessHook = (point: string, context: string, revision: string, guard: boolean, holds: boolean) => boolean;

function observed(point: string, holds: boolean, guard = true, context = 'original'): boolean {
    const hook = (globalThis as Record<symbol, unknown>)[Symbol.for('org.usvm.ts.pbt.witness')] as WitnessHook | undefined;
    return hook?.(point, context, 'v1', guard, holds) ?? holds;
}

export function threshold(value: number): boolean {
    return value < 10;
}

export function namedChecks(value: number): boolean {
    if (value < 10) {
        return observed('other-check', false);
    }

    return observed('threshold-check', value < 15);
}

export function hypothesis(value: number): boolean {
    observed('hypothesis', value < 5, value > 0);
    return true;
}

export function repeatedHypothesis(value: number): boolean {
    observed('hypothesis', true);
    observed('hypothesis', value < 5);
    return true;
}

export function differentContext(value: number): boolean {
    observed('hypothesis', value < 5, true, 'different');
    return true;
}

export function rejected(): boolean { return false; }
export function admitted(value: number): boolean { return value >= 20; }
export function preconditionError(): boolean { throw new Error('precondition'); }
export function predicateError(): boolean { throw new TypeError('original predicate'); }
export function contractError(): number { return 42; }
export function hangs(): boolean { while (true) { /* supervised timeout */ } }

let flipped = false;
export function nondeterministic(): boolean { flipped = !flipped; return flipped; }

export function mutation(values: number[]): boolean {
    const originalLength = values.length;
    values.pop();
    return originalLength < 3;
}

export function specialValues(zero: number, nan: number, text: string, nil: undefined, infinity: number): boolean {
    return !(Object.is(zero, -0) && Number.isNaN(nan) && text === '\ud800' && nil === undefined && infinity === Infinity);
}

export function slowShrink(value: number): boolean {
    if (value !== 80) {
        while (true) { /* original still replayable; reduction is supervised */ }
    }

    return false;
}

export function supportedIndex(values: number[], index: number): boolean {
    return values[index] < 10;
}

export function shrinkError(value: number): boolean {
    return value === 80 ? false : 42 as unknown as boolean;
}

let initialSeen = false;
export function requiresInitial(value: number): boolean {
    if (value === 80) {
        initialSeen = true;
        return false;
    }

    return !initialSeen || value < 10;
}

export function nondeterministicShrink(value: number): boolean {
    if (value === 80) return false;

    flipped = !flipped;
    return flipped;
}

export function eventError(): boolean {
    const hook = (globalThis as Record<symbol, unknown>)[Symbol.for('org.usvm.ts.pbt.witness')] as WitnessHook | undefined;
    hook?.('', 'original', 'v1', true, false);
    return observed('oracle-completed', true);
}
