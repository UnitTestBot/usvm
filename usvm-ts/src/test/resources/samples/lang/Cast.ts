// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

class Cast {
    castAnyToNumber(x: any): number {
        const y = x as number;
        if (y === 42) {
            return 1;
        }
        return 0;
    }

    castAnyToBoolean(x: any): number {
        const y = x as boolean;
        if (y === true) {
            return 1;
        }
        if (y === false) {
            return 2;
        }
        return -1;
    }

    castAnyToString(x: any): number {
        const y = x as string;
        if (y === "test") {
            return 1;
        }
        return 0;
    }

    castNumberToAny(x: number): number {
        const y = x as any;
        if (y === 42) {
            return 1;
        }
        return 0;
    }

    castBooleanToAny(x: boolean): number {
        const y = x as any;
        if (y === true) {
            return 1;
        }
        if (y === false) {
            return 2;
        }
        return -1; // unreachable
    }

    castWithMultipleBranches(x: any): number {
        const y = x as number;
        if (y > 10) {
            return 1;
        } else if (y > 5) {
            return 2;
        } else if (y > 0) {
            return 3;
        }
        return 0;
    }

    castObjectToInterface(obj: any): number {
        const typed = obj as { value: number };
        if (typed.value === 100) {
            return 100;
        }
        return 0;
    }

    castNullableToNumber(x: number | null): number {
        if (x === null) {
            return -1;
        }
        const y = x as number;
        if (y === 5) {
            return 5;
        }
        return 0;
    }

    castChained(x: any): number {
        const y = x as any;
        const z = y as number;
        if (z === 7) {
            return 7;
        }
        return 0;
    }

    castInExpression(x: any): number {
        if ((x as number) === 3) {
            return 3;
        }
        return 0;
    }

    castAndArithmetic(x: any): number {
        const y = x as number;
        const result = y + 10;
        if (result === 20) {
            return 1;
        }
        return 0;
    }

    castUnionType(x: number | string): number {
        const y = x as number;
        if (y === 15) {
            return 15;
        }
        return 0;
    }
}
