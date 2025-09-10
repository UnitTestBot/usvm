// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

/**
 * Type guards and assertions reachability scenarios.
 * Tests reachability through typeof checks, instanceof operations, and user-defined type guards.
 */
class TypeGuardsReachability {

    // typeof type guard reachability
    typeofGuardReachable(value: any): number {
        if (typeof value === "string") {
            // Note: string operations are limited to constant comparisons
            if (value === "hello") {
                return 1; // Reachable when value is exactly "hello"
            }
        }
        return 0;
    }

    // instanceof type guard reachability
    instanceofGuardReachable(obj: any): number {
        if (obj instanceof Array) {
            if (obj.length === 3) {
                return 1; // Reachable when obj is array with 3 elements
            }
        }
        return 0;
    }

    // User-defined type guard
    isString(value: any): value is string {
        return typeof value === "string";
    }

    userDefinedGuardReachable(input: any): number {
        if (this.isString(input)) {
            if (input === "test") {
                return 1; // Reachable when input is exactly "test"
            }
        }
        return 0;
    }

    // Type assertion reachability
    typeAssertionReachable(value: any): number {
        const str = value as string;
        if (str === "A") {
            return 1; // Reachable when value is exactly "A"
        }
        return 0;
    }

    // Non-null assertion reachability
    nonNullAssertionReachable(value: string | null): number {
        if (value !== null) {
            const definiteString = value!;
            if (definiteString === "nonempty") {
                return 1; // Reachable when value is non-null "nonempty"
            }
        }
        return 0;
    }

    // Numeric type guard for better symbolic support
    numericTypeGuardReachable(value: any): number {
        if (typeof value === "number") {
            if (value > 10) {
                if (value < 20) {
                    return 1; // Reachable for numbers between 10 and 20
                }
            }
        }
        return 0;
    }

    // Boolean type guard reachability
    booleanTypeGuardReachable(value: any): number {
        if (typeof value === "boolean") {
            if (value === true) {
                return 1; // Reachable when value is boolean true
            }
        }
        return 0;
    }

    // Object type guard without null
    objectTypeGuardReachable(value: any): number {
        if (typeof value === "object" && value !== null) {
            return 1; // Reachable for non-null objects
        }
        return 0;
    }

    // Complex type guard combinations
    complexTypeGuardReachable(value: any): number {
        if (typeof value === "object" && value !== null) {
            if (value instanceof Date) {
                if (value.getFullYear() > 2020) {
                    return 1; // Reachable for dates after 2020
                }
            }
        }
        return 0;
    }
}
