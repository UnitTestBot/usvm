class SymbolicStringInput {
    identity(value: string): string {
        return value;
    }

    lengthOne(value: string): number {
        if (value.length === 0) return 0;

        return value.length === 1 ? 1 : 0;
    }

    literal(): string {
        return "A\u0000\u03a9\uD83D\uDE00";
    }

    literalLength(): number {
        return "A\u0000\u03a9\uD83D\uDE00".length;
    }

    independentArrayLength(value: string, array: number[]): number {
        if (value.length !== 1 || array.length !== 1) return 0;

        array.length = 0;
        return value.length === 0 ? 1 : 2;
    }

    lengthIs10001(value: string): number {
        return value.length === 10001 ? 1 : 0;
    }

    anyStringLength(value: any): number {
        if (typeof value !== "string") return 0;

        return value.length === 1 ? 1 : 2;
    }
}
