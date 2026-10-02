class Exponentiation {
    square(value: number): number {
        if (value === 3) return value ** 2;
        if (value === -3) return value ** 2;
        if (value === Infinity) return value ** 2;
        if (value === -Infinity) return value ** 2;
        if (value !== value) return value ** 2;
        return value ** 2;
    }

    reciprocal(value: number): number {
        if (1 / value === -Infinity) return value ** -1;
        if (value === 0) return value ** -1;
        return value ** -1;
    }

    squareRoot(value: number): number {
        if (1 / value === -Infinity) return value ** 0.5;
        if (value === 9) return value ** 0.5;
        if (value === -1) return value ** 0.5;
        return value ** 0.5;
    }

    constantFractional(): number {
        return 9 ** 0.5;
    }

    nanToZero(): number {
        return (0 / 0) ** 0;
    }

    negativeZeroToMinusOne(): number {
        return (-0) ** -1;
    }

    negativeInfinitySquared(): number {
        return (-1 / 0) ** 2;
    }

    negativeOneInfinite(): number {
        return (-1) ** (1 / 0);
    }

    negativeFractional(): number {
        return (-9) ** 0.5;
    }

    symbolicExponent(value: number, exponent: number): number {
        return value ** exponent;
    }

    unsupportedFractional(value: number): number {
        return value ** 1.5;
    }

    stringBase(): number {
        return "3" ** 2;
    }
}
