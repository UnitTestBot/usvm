export function admits(value: number): boolean {
    return value >= 20;
}

export function isolatedFailure(value: number): boolean {
    return value !== 42;
}

export function namedResult(value: number): { passed: boolean; checkId: string; actual: number; expected: number } {
    return { passed: value !== 42, checkId: 'not-forty-two', actual: value, expected: 42 };
}

export async function asyncHolds(value: number): Promise<boolean> {
    return value === value;
}

export function hangs(value: number): boolean {
    while (value === value) {
        // The native process supervisor must stop this invocation.
    }

    return true;
}
