export class BuiltinOwnerBoundary {
    shadowedMath(): number {
        const Math = { abs(value: number): number { return value + 79; } };

        return Math.abs(-2);
    }

    shadowedNumber(): boolean {
        const Number = { isInteger(_value: number): boolean { return false; } };

        return Number.isInteger(2);
    }

    aliasedMath(): number {
        const numeric = Math;

        return numeric.abs(-2);
    }

    aliasedNumber(): boolean {
        const numeric = Number;

        return numeric.isInteger(1.5);
    }
}
