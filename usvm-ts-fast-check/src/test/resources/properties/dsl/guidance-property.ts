import { branch } from './edge-guidance';

export function valid(value: number): boolean {
    return branch(value) > 0;
}

export function admission(value: number): boolean {
    branch(-value);
    return value > 0;
}

export function guarded(value: number): boolean {
    return branch(-value) > 0;
}

export function retryAdmission(value: number): boolean {
    if (value === -1) return true;
    if (value === -2) return true;
    return true;
}
