export function coverage(value: number): boolean {
    const hook = (globalThis as Record<symbol, unknown>)[Symbol.for('org.usvm.ts.pbt.witness')] as
        ((point: string, context: string, revision: string, guard: boolean, holds: boolean) => boolean) | undefined;

    if (value >= 10) {
        hook?.('positive-edge', 'original', 'v1', true, true);
        return true;
    } else {
        return true;
    }
}
