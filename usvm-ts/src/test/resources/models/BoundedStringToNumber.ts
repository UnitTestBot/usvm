export class BoundedStringToNumber {
    static literalForms(): number {
        if (Number("42") !== 42 || Number(" -12.5e1 ") !== -125) return 0;
        if (Number("0xff") !== 255 || Number("0o17") !== 15 || Number("0b101") !== 5) return 0;
        if (Number("\ufeff\u00a0") !== 0 || Number(".5") !== 0.5 || Number("1.") !== 1) return 0;
        if (Number("1e309") !== 1 / 0 || Number("-Infinity") !== -1 / 0) return 0;
        if (1 / Number("-0") !== -1 / 0) return 0;
        const invalid = ["0x1p0", "1d", "+0x1", "0b2", "1_0", "\u0085", "0xＦＦ"];
        for (const value of invalid) {
            const converted = Number(value);
            if (converted === converted) return 0;
        }
        return 1;
    }

    static selectedIntegers(value: string): number {
        if (value === "12") return Number(value);
        if (value === " -0 ") return 1 / Number(value);
        if (value === "+") return Number(value);
        if (value === "999999999999999") return Number(value);
        return 0;
    }

    static unaryInteger(value: string): number {
        if (value !== "-12") return 0;
        return +value;
    }

    static unsupportedFraction(value: string): number {
        if (value !== "1.5") return 0;
        return Number(value);
    }

    static unsupportedPrecision(value: string): number {
        if (value !== "9007199254740993") return 0;
        return Number(value);
    }
}
