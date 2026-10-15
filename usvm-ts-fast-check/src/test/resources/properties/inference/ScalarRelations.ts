export function nonNegativeMagnitude(value: number): boolean {
    const input = observePoint('input', value);
    const result = observePoint('result', Math.abs(input));

    return result >= 0;
}

function observePoint<T>(id: string, value: T): T {
    const hook = (globalThis as Record<symbol, unknown>)[Symbol.for('org.usvm.ts.pbt.observe')];

    return typeof hook === 'function' ? (hook as (pointId: string, value: T) => T)(id, value) : value;
}
