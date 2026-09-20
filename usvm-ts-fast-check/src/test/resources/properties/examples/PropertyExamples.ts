export function isCommutative(left: number, right: number): boolean {
    return left + right === right + left;
}

export function boundedValueStaysBounded(value: number): boolean {
    return value >= -100 && value <= 100;
}

export function nonZeroDivisor(_dividend: number, divisor: number): boolean {
    return divisor !== 0;
}

export function divisionRoundTrip(dividend: number, divisor: number): boolean {
    return (dividend / divisor) * divisor === dividend;
}

export function reverseTwicePreservesValues(values: number[]): boolean {
    return [...values].reverse().reverse().every((value, index) => value === values[index]);
}
