// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

/**
 * Loops and iteration reachability scenarios.
 * Tests reachability through for loops, while loops, and loop control statements.
 */
class LoopsReachability {

    // For loop with reachable condition
    forLoopReachable(limit: number): number {
        for (let i = 0; i < limit; i++) {
            if (i === 5) {
                if (limit > 10) {
                    return 1; // Reachable when limit > 10
                }
            }
        }
        return 0;
    }

    // While loop with break condition
    whileLoopBreakReachable(start: number): number {
        let counter = start;
        while (counter < 100) {
            counter += 2;
            if (counter === 20) {
                break;
            }
            if (counter > 50) {
                return 1; // Unreachable due to break at 20
            }
        }
        return 0;
    }

    // Nested loops with continue
    nestedLoopContinueReachable(): number {
        for (let i = 0; i < 5; i++) {
            for (let j = 0; j < 5; j++) {
                if (i + j === 3) {
                    continue;
                }
                if (i === 2 && j === 3) {
                    return 1; // Reachable: 2 + 3 = 5, not equal to 3
                }
            }
        }
        return 0;
    }

    // For-in loop reachability
    forInLoopReachable(): number {
        const obj = { a: 1, b: 2, c: 3 };
        for (const key in obj) {
            if (key === "b") {
                if (obj[key] === 2) {
                    return 1; // Reachable when iterating over object
                }
            }
        }
        return 0;
    }

    // For-of loop with array
    forOfLoopReachable(): number {
        const arr = [10, 20, 30, 40];
        for (const value of arr) {
            if (value > 25) {
                if (value < 35) {
                    return 1; // Reachable for value = 30
                }
            }
        }
        return 0;
    }

    // Do-while loop reachability
    doWhileReachable(threshold: number): number {
        let count = 0;
        do {
            count++;
            if (count === threshold) {
                return 1; // Always reachable at least once
            }
        } while (count < threshold);
        return 0;
    }
}
