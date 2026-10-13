// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

const globalValues = [11, 22];
globalValues[0.9] = 7;

export class GlobalArrayAssignmentBoundary {
    readSecondValue(): number {
        return globalValues[1];
    }
}
