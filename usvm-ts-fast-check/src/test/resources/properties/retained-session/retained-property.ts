import { negative, positive } from './retained-branches';

export function valid(value: number): boolean {
    let result = positive(value) + negative(value);
    result = result + 1;
    result = result + 1;
    result = result + 1;
    return result >= 0;
}

export function partialUnsupported(value: number): boolean {
    if (value > 0) {
        const values = { present: 1 };
        return values[value] === 1;
    } else {
        return valid(value);
    }
}

export function earlyViolation(value: number): boolean {
    if (value > 0) {
        return false;
    } else {
        return valid(value);
    }
}
