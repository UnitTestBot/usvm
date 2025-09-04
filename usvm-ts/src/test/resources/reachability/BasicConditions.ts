// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

/**
 * Basic conditional reachability samples covering simple variable comparisons
 * and branching scenarios for reachability analysis.
 */
class BasicConditions {

    // Simple reachable path: condition can be satisfied
    simpleReachablePath(x: number): number {
        if (x > 10) {
            if (x < 20) {
                return 1; // Reachable when 10 < x < 20
            }
        }
        return 0;
    }

    // Simple unreachable path: contradicting conditions
    simpleUnreachablePath(x: number): number {
        if (x > 15) {
            if (x < 10) {
                return -1; // Unreachable: x cannot be both > 15 and < 10
            }
        }
        return 0;
    }

    // Multiple variables with reachable path
    multiVariableReachable(x: number, y: number): number {
        if (x > 0) {
            if (y > 5) {
                if (x + y > 10) {
                    return 1; // Reachable with appropriate x, y values
                }
            }
        }
        return 0;
    }

    // Multiple variables with unreachable path
    multiVariableUnreachable(x: number, y: number): number {
        if (x > 0) {
            if (y > 0) {
                if (x + y < 0) {
                    return -1; // Unreachable: x > 0 and y > 0 means x + y > 0
                }
            }
        }
        return 0;
    }

    // Nested conditions with equality checks
    equalityBasedReachability(value: number): number {
        if (value === 42) {
            if (value > 40) {
                if (value < 50) {
                    return 1; // Reachable when value is exactly 42
                }
            }
        }
        return 0;
    }

    // Boolean variable conditions
    booleanConditions(flag1: boolean, flag2: boolean): number {
        if (flag1) {
            if (!flag2) {
                if (flag1 && !flag2) {
                    return 1; // Reachable when flag1 is true and flag2 is false
                }
            }
        }
        return 0;
    }

    // Complex boolean unreachable condition
    booleanUnreachable(flag: boolean): number {
        if (flag) {
            if (!flag) {
                return -1; // Unreachable: flag cannot be both true and false
            }
        }
        return 0;
    }
}
