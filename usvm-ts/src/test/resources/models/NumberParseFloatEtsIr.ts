// @ts-nocheck

export class NumberParseFloatEtsIr {
    decimalPrefix(): number { return Number.parseFloat("  -1.25rem"); }
    exponentPrefix(): number { return Number.parseFloat(".5e+2px"); }
    incompleteExponent(): number { return Number.parseFloat("12e-foo"); }
    hexadecimalPrefix(): number { return Number.parseFloat("0x10"); }
    secondDecimalPoint(): number { return Number.parseFloat("1.2.3"); }
    unicodeWhitespace(): number { return Number.parseFloat("\uFEFFInfinity!"); }
    negativeZero(): number { return 1 / Number.parseFloat("-0.0"); }
    notANumber(): number { return Number.parseFloat("nope"); }
    decimalRounding(): number { return Number.parseFloat("0.100000000000001"); }
    unsupportedMantissa(): number { return Number.parseFloat("12345678901234567"); }

    ignoredArgument(): number {
        let calls = 0;
        const result = Number.parseFloat("12px", calls++);
        return result + calls;
    }
}
