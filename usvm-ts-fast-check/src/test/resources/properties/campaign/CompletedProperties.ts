import { BranchingCounter } from './CompletedSubject';

export function healthy(value: number): boolean {
    const counter = new BranchingCounter();
    counter.value(value);
    return true;
}

export function faulty(value: number): boolean {
    const counter = new BranchingCounter();
    return counter.value(value) !== value;
}

export function positive(value: number): boolean {
    return value > 0;
}
