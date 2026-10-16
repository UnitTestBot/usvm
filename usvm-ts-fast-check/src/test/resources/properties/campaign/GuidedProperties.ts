import { branch, nested } from './GuidedSubject';

export function branchHolds(value: number): boolean {
    return branch(value) > 0;
}

export function nestedHolds(value: number): boolean {
    return nested(value) > 0;
}

export function branchDifferent(value: number): boolean {
    return branch(value) !== value;
}

export async function delayedHolds(value: number): Promise<boolean> {
    await new Promise(resolve => setTimeout(resolve, 10_000));
    return value === value;
}

export function growthLimit(value: number): boolean {
    const array = [1];
    array[value] = 2;
    return array.length > 0;
}
