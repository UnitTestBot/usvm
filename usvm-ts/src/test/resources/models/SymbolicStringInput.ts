class SymbolicStringInput {
    identity(value: string): string {
        return value;
    }

    lengthOne(value: string): number {
        return value.length === 1 ? 1 : 0;
    }

    literal(): string {
        return "A\u0000\u03a9\uD83D\uDE00";
    }
}
