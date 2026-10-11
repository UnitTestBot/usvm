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
        return value ** -1;
    }

    squareRoot(value: number): number {
        if (value === -Infinity) return value ** 0.5;
        if (1 / value === -Infinity) return value ** 0.5;
        if (value === 9) return value ** 0.5;
        if (value === -1) return value ** 0.5;
        return value ** 0.5;
    }

    constantFractional(): number {
        return 9 ** 0.5;
    }

    unmodeledConcreteFractional(): number {
        return 0.1 ** 0.7;
    }

    nanToZero(): number {
        return (0 / 0) ** 0;
    }

    negativeZeroToMinusOne(): number {
        return (-0) ** -1;
    }

    positiveZeroToMinusOne(): number {
        return 0 ** -1;
    }

    negativeInfinitySquared(): number {
        return (-1 / 0) ** 2;
    }

    negativeOneInfinite(): number {
        return (-1) ** (1 / 0);
    }

    negativeOneNegativeInfinite(): number {
        return (-1) ** (-1 / 0);
    }

    negativeFractional(): number {
        return (-9) ** 0.5;
    }

    symbolicExponent(value: number, exponent: number): number {
        const result = value ** exponent;
        if (exponent === 0) return result;
        if (exponent === 1) return result;
        if (exponent === 2) return result;
        if (exponent === 0.5) {
            if (value === 9) return result;
            return result;
        }
        return result;
    }

    numberOrBooleanBase(value: number | boolean): number {
        if (value === 3) return value ** 2;
        if (value === true) return value ** 2;
        if (value === false) return value ** 2;
        return value ** 2;
    }

    numberOrBooleanExponent(exponent: number | boolean): number {
        if (exponent === 2) return 3 ** exponent;
        if (exponent === true) return 3 ** exponent;
        if (exponent === false) return 3 ** exponent;
        return 3 ** exponent;
    }

    mixedBase(value: any): number {
        if (value === 3) return value ** 2;
        if (value === true) return value ** 2;
        if (value === false) return value ** 2;
        if (value === null) return value ** 2;
        if (value === undefined) return value ** 2;
        return value ** 2;
    }

    mixedExponent(exponent: any): number {
        if (exponent === 2) return 3 ** exponent;
        if (exponent === true) return 3 ** exponent;
        if (exponent === false) return 3 ** exponent;
        if (exponent === null) return 3 ** exponent;
        return 3 ** exponent;
    }

    unsupportedFractional(value: number): number {
        return value ** 1.5;
    }

    stringBase(): number {
        return "3" ** 2;
    }
}
