import type { MappingMarker } from "./MappingMarker";

export function ifElse(value: number): number {
    if (value > 0) {
        return 1;
    } else {
        return -1;
    }
}

export function loop(limit: number): number {
    let sum = 0;
    for (let index = 0; index < limit; index++) {
        sum += index;
    }
    return sum;
}

export const ternary = (value: number): number => value > 0 ? 1 : -1;

export function shortCircuit(left: boolean, right: boolean): boolean {
    if (left && right) {
        return true;
    }
    return false;
}

export function optionalChain(value: MappingMarker | undefined): number {
    return value?.member ?? -1;
}

export class StaticGolden {
    static classify(value: number): number {
        return value === 42 ? 1 : 0;
    }
}
