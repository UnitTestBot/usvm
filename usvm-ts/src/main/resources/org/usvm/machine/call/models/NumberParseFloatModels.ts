declare class StringModelPrimitives {
    static length(receiver: string): number;
    static codeUnitAt(receiver: string, index: number): number;
}

declare class NumberParseFloatPrimitives {
    // Deliberately unmodeled: the residual policy handles decimals outside the exact domain.
    static unsupportedDecimal(): number;
}

export class NumberParseFloatModels {
    static parseFloat(input: string): number {
        const length = StringModelPrimitives.length(input);
        let index = 0;
        while (index < length && NumberParseFloatModels.isWhitespace(
            StringModelPrimitives.codeUnitAt(input, index))) index++;

        let negative = false;
        if (index < length) {
            const sign = StringModelPrimitives.codeUnitAt(input, index);
            if (sign === 43 || sign === 45) {
                negative = sign === 45;
                index++;
            }
        }
        if (NumberParseFloatModels.isInfinity(input, index, length)) return negative ? -Infinity : Infinity;

        let coefficient = 0;
        let significantDigits = 0;
        let digits = 0;
        let fractionalDigits = 0;
        let point = false;
        while (index < length) {
            const code = StringModelPrimitives.codeUnitAt(input, index);
            if (code >= 48 && code <= 57) {
                const digit = code - 48;
                if (coefficient !== 0 || digit !== 0) significantDigits++;
                // Keep all arithmetic exact; rejected long mantissas never produce an approximation.
                if (significantDigits > 15) return NumberParseFloatPrimitives.unsupportedDecimal();
                coefficient = coefficient * 10 + digit;
                digits++;
                if (point) fractionalDigits++;
                index++;
            } else if (code === 46 && !point) {
                point = true;
                index++;
            } else {
                break;
            }
        }
        if (digits === 0) return NaN;

        let exponent = 0;
        if (index < length) {
            const code = StringModelPrimitives.codeUnitAt(input, index);
            if (code === 69 || code === 101) {
                index++;
                let exponentNegative = false;
                if (index < length) {
                    const sign = StringModelPrimitives.codeUnitAt(input, index);
                    if (sign === 43 || sign === 45) {
                        exponentNegative = sign === 45;
                        index++;
                    }
                }
                // With no exponent digits this remains zero: parseFloat accepts the preceding prefix.
                while (index < length) {
                    const digit = StringModelPrimitives.codeUnitAt(input, index) - 48;
                    if (digit < 0 || digit > 9) break;
                    if (exponent < 1000) exponent = exponent * 10 + digit;
                    index++;
                }
                if (exponentNegative) exponent = -exponent;
            }
        }
        if (coefficient === 0) return negative ? -0 : 0;
        const scale = exponent - fractionalDigits;
        if (scale < -22 || scale > 22) return NumberParseFloatPrimitives.unsupportedDecimal();

        // Every integer coefficient and every power 10^k, k <= 22, is exactly representable.
        // A single final multiply/divide therefore supplies the required binary64 rounding.
        let power = 1;
        let remaining = scale < 0 ? -scale : scale;
        while (remaining > 0) {
            power *= 10;
            remaining--;
        }
        const result = scale < 0 ? coefficient / power : coefficient * power;
        return negative ? -result : result;
    }

    private static isInfinity(input: string, start: number, length: number): boolean {
        if (start + 8 > length) return false;
        return StringModelPrimitives.codeUnitAt(input, start) === 73 &&
            StringModelPrimitives.codeUnitAt(input, start + 1) === 110 &&
            StringModelPrimitives.codeUnitAt(input, start + 2) === 102 &&
            StringModelPrimitives.codeUnitAt(input, start + 3) === 105 &&
            StringModelPrimitives.codeUnitAt(input, start + 4) === 110 &&
            StringModelPrimitives.codeUnitAt(input, start + 5) === 105 &&
            StringModelPrimitives.codeUnitAt(input, start + 6) === 116 &&
            StringModelPrimitives.codeUnitAt(input, start + 7) === 121;
    }

    private static isWhitespace(code: number): boolean {
        return (code >= 9 && code <= 13) || code === 32 || code === 160 || code === 5760 ||
            (code >= 8192 && code <= 8202) || code === 8232 || code === 8233 || code === 8239 ||
            code === 8287 || code === 12288 || code === 65279;
    }
}
