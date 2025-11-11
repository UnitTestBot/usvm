// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

class TryCatch {
    emptyTryCatch(): number {
        let result = 0;
        try {
            result = 1;
        } catch (e) {
            result = -1; // unreachable
        }
        return result; // 1
    }

    simpleTryCatch(): number {
        let result = 0;
        try {
            throw new Error("test");
            result = -1; // unreachable
        } catch (e) {
            result = 1;
        }
        return result; // 1
    }

    conditionalThrowTryCatch(shouldThrow: boolean): number {
        let result = 0;
        try {
            if (shouldThrow) {
                throw new Error("conditional error");
                result = -1; // unreachable
            } else {
                result = 1;
            }
        } catch (e) {
            result = 2;
        }
        if (shouldThrow) {
            return result; // 2
        } else {
            return result; // 1
        }
    }

    // nestedTryCatch(shouldThrow: boolean): number {
    //     let result = 0;
    //     try {
    //         try {
    //             if (shouldThrow) {
    //                 throw new Error("inner error");
    //                 result = -1; // unreachable
    //             } else {
    //                 result = 1;
    //             }
    //         } catch (e) {
    //             result = 2;
    //         }
    //     } catch (e) {
    //         result = -3; // unreachable - inner catch doesn't rethrow
    //     }
    //     return result; // 1 if no throw, 2 if thrown
    // }

    tryFinallyNoException(): number {
        let result = 0;
        try {
            result = 1;
        } finally {
            result = result + 10;
        }
        return result; // 11
    }

    tryFinallyWithException(): number {
        let result = 0;
        try {
            result = 1;
            throw new Error("test");
            result = -1; // unreachable
        } finally {
            result = result + 10;
        }
        return result; // 11, exception is swallowed
    }

    tryCatchFinally(shouldThrow: boolean): number {
        let result = 0;
        try {
            if (shouldThrow) {
                throw new Error("test");
                result = -1; // unreachable
            } else {
                result = 1;
            }
        } catch (e) {
            result = 2;
        } finally {
            result = result + 10;
        }
        if (shouldThrow) {
            return result; // 12
        } else {
            return result; // 11
        }
    }

    catchWithReturn(): number {
        let result = 0;
        try {
            throw new Error("test");
            result = -1; // unreachable
        } catch (e) {
            result = 1;
        }
        return result; // 1
    }

    finallyOverridesReturn(shouldThrow: boolean): number {
        let result = 0;
        try {
            if (shouldThrow) {
                throw new Error("test");
                result = -1; // unreachable
            } else {
                result = 1; // will be overridden
            }
        } catch (e) {
            result = 2; // will be overridden
        } finally {
            result = 100; // always wins
        }
        return result; // always 100
    }

    rethrowInCatch(): number {
        let result = 0;
        try {
            try {
                throw new Error("test");
                result = -1; // unreachable
            } catch (e) {
                throw e; // rethrow
                result = -2; // unreachable
            }
            result = -3; // unreachable
        } catch (e) {
            result = 1;
        }
        return result; // 1
    }

    catchDifferentTypes(value: number): number {
        let result = 0;
        try {
            if (value === 1) {
                throw new Error("error object");
            } else if (value === 2) {
                throw "string error";
            } else if (value === 3) {
                throw 123;
            } else if (value === 4) {
                throw true;
            } else if (value === 5) {
                throw null;
            } else if (value === 6) {
                throw undefined;
            } else {
                result = 0; // no exception
            }
        } catch (e) {
            result = 1; // caught any exception
        }
        return result; // 0 if no throw, 1 if thrown
    }

    multipleReturnsInTry(x: number): number {
        let result = 0;
        try {
            if (x < 0) {
                result = 1;
            } else if (x === 0) {
                result = 2;
            } else if (x > 0) {
                result = 3;
            } else {
                result = -1; // unreachable - all cases covered
            }
        } catch (e) {
            result = -2; // unreachable - no throws
        }
        return result;
    }

    finallyWithThrow(): number {
        let result = 0;
        try {
            result = -1; // will be overridden by finally
        } finally {
            throw new Error("finally throws");
            result = -2; // unreachable
        }
        return result; // unreachable, exception always thrown
    }

    catchWithoutVariable(): number {
        let result = 0;
        try {
            throw new Error("test");
            result = -1; // unreachable
        } catch {
            result = 1; // catch without binding
        }
        return result; // 1
    }

    conditionalRethrow(shouldRethrow: boolean): number {
        let result = 0;
        try {
            try {
                throw new Error("test");
                result = -1; // unreachable
            } catch (e) {
                if (shouldRethrow) {
                    throw e;
                    result = -2; // unreachable
                } else {
                    result = 1;
                }
            }
        } catch (e) {
            result = 2;
        }
        if (shouldRethrow) {
            return result; // 2
        } else {
            return result; // 1
        }
    }

    multipleCatchPaths(x: number): number {
        let result = 0;
        try {
            if (x === 0) {
                throw new Error("zero");
            } else if (x < 0) {
                result = 1;
            } else if (x > 0) {
                throw new Error("positive");
            } else {
                result = -1; // unreachable - all cases covered
            }
        } catch (e) {
            result = 2;
        }
        if (x == 0) {
            return result; // 2
        } else if (x < 0) {
            return result; // 1
        } else if (x > 0) {
            return result; // 2
        }
        return result; // unreachable
    }

    finallyModifiesVariable(shouldThrow: boolean): number {
        let result = 0;
        try {
            result = 10;
            if (shouldThrow) {
                throw new Error("test");
                result = -1; // unreachable
            } else {
                result = 20;
            }
        } catch (e) {
            result = 30;
        } finally {
            result = result + 5;
        }
        if (shouldThrow) {
            return result; // 35
        } else {
            return result; // 25
        }
    }

    earlyReturnInFinally(): number {
        let result = 0;
        try {
            result = -1; // will be overridden by finally
        } finally {
            if (true) {
                result = 1; // always executed
            } else {
                result = -2; // unreachable
            }
        }
        return result; // always 1
    }

    tryCatchInLoop(n: number): number {
        let sum = 0;
        for (let i = 0; i < n; i++) {
            try {
                if (i === 3) {
                    throw new Error("three");
                }
                sum += i;
            } catch (e) {
                sum += 100;
            }
        }
        if (n == 1) {
            return sum; // sum is 0
        } else if (n == 2) {
            return sum; // sum is 1
        } else if (n == 3) {
            return sum; // sum is 3
        } else if (n == 4) {
            return sum; // sum is 103
        } else if (n == 5) {
            return sum; // sum is 107
        }
        return sum; // sum of 0..n-1 with 100 added for i=3
    }

    tryCatchWithObjectAccess(obj: any): number {
        let result = 0;
        try {
            if (obj === null || obj === undefined) {
                result = 1; // null/undefined check
            } else {
                const value = obj.x;
                if (value === 42) {
                    result = 2;
                } else {
                    result = 3;
                }
            }
        } catch (e) {
            result = 4; // property access error
        }
        return result; // 1, 2, 3, or 4
    }

    tryCatchWithArrayAccess(arr: any, index: number): number {
        let result = 0;
        try {
            if (arr === null || arr === undefined) {
                throw new Error("null array");
            }
            const value = arr[index];
            if (value === undefined) {
                result = 1; // out of bounds or missing element
            } else if (value > 10) {
                result = 2;
            } else {
                result = 3;
            }
        } catch (e) {
            result = 4; // caught exception
        }
        return result; // 1, 2, 3, or 4
    }

    tryCatchWithFunctionCall(shouldFail: boolean): number {
        function mayThrow(flag: boolean): number {
            if (flag) {
                throw new Error("function error");
            }
            return 42;
        }

        let result = 0;
        try {
            const value = mayThrow(shouldFail);
            if (value > 40) {
                result = 1;
            } else {
                result = 2;
            }
        } catch (e) {
            result = 3;
        }
        return result; // 1 if no throw, 3 if thrown
    }

    tryCatchWithMultipleConditions(x: number, y: number): number {
        let result = 0;
        try {
            if (x < 0 && y < 0) {
                throw new Error("both negative");
            } else if (x < 0 || y < 0) {
                result = 1; // one negative
            } else if (x === 0 && y === 0) {
                result = 2; // both zero
            } else if (x > 0 && y > 0) {
                result = 3; // both positive
            } else {
                result = 4; // mixed
            }
        } catch (e) {
            result = 5; // exception caught
        }
        return result;
    }

    tryCatchInConditional(flag: boolean, x: number): number {
        let result = 0;
        if (flag) {
            try {
                if (x === 0) {
                    throw new Error("zero");
                }
                result = x > 0 ? 1 : 2;
            } catch (e) {
                result = 3;
            }
        } else {
            result = 4;
        }
        return result;
    }

    tryCatchWithReturn(x: number): number {
        let result = 0;
        try {
            if (x < 0) {
                result = 1;
            } else if (x === 0) {
                throw new Error("zero");
            } else {
                result = 2;
            }
        } catch (e) {
            if (x === 0) {
                result = 3;
            } else {
                result = 4;
            }
        }
        return result;
    }

    tryCatchWithLogicalOps(a: boolean, b: boolean): number {
        let result = 0;
        try {
            if (a && b) {
                throw new Error("both true");
            } else if (a || b) {
                result = 1; // at least one true
            } else {
                result = 2; // both false
            }
        } catch (e) {
            if (a) {
                result = 3; // a is true
            } else {
                result = 4; // should be unreachable
            }
        }
        return result;
    }

    finallyWithSideEffects(x: number): number {
        let counter = 0;
        let result = 0;
        try {
            counter++;
            if (x < 0) {
                throw new Error("negative");
            }
            counter++;
            if (x === 0) {
                result = counter; // 2
            } else {
                counter++;
                result = counter; // 3
            }
        } catch (e) {
            counter += 10;
            result = counter; // 11
        } finally {
            counter += 100; // always executed, but return already happened
        }
        return result;
    }

    exceptionInComplexExpression(x: number, y: number): number {
        let result = 0;
        try {
            const temp = (x > 0 && y > 0) ? x + y : x - y;
            if (temp === 0) {
                throw new Error("zero result");
            } else if (temp > 0) {
                result = 1;
            } else {
                result = 2;
            }
        } catch (e) {
            result = 3;
        }
        result = result || -1; // unreachable
        return result;
    }

    tryCatchWithTernary(x: number): number {
        let result = 0;
        try {
            const value = x > 0 ? (x > 10 ? 1 : 2) : (x < -10 ? 3 : 4);
            if (value === 2) {
                throw new Error("case 2");
            }
            result = value;
        } catch (e) {
            result = 5;
        }
        return result;
    }

    exceptionAfterMultipleOps(x: number): number {
        let result = 0;
        try {
            let temp = x;
            temp = temp + 5;
            temp = temp * 2;
            if (temp > 20) {
                throw new Error("too large");
            }
            temp = temp - 3;
            result = temp;
        } catch (e) {
            result = 100;
        }
        return result;
    }

    tryCatchWithEarlyExit(x: number, y: number): number {
        let result = 0;
        try {
            if (x === 0) {
                result = 1; // early exit
            } else {
                const ratio = y;
                if (ratio < 0) {
                    throw new Error("negative ratio");
                } else if (ratio === 0) {
                    result = 2;
                } else {
                    result = 3;
                }
            }
        } catch (e) {
            result = 4;
        }
        return result;
    }

    finallyDoesNotCatchException(shouldThrow: boolean): number {
        let result = 0;
        try {
            if (shouldThrow) {
                throw new Error("test");
            }
            result = 1;
        } finally {
            const temp = 42; // side effect, doesn't catch
        }
        return result; // -1 when exception thrown (actually unreachable)
    }

    multipleFinallyBlocks(x: number): number {
        let result = 0;
        try {
            try {
                result = 1;
                if (x < 0) {
                    throw new Error("negative");
                }
                result = 2;
            } finally {
                result += 10; // always executed
            }
            result += 100; // executed if no exception
        } catch (e) {
            result += 1000; // executed on exception
        }
        if (x < 0) {
            return result; // 111
        } else {
            return result; // 112
        }
    }

    tryCatchSwallowsException(x: number): number {
        let result = 0;
        try {
            if (x < 0) {
                throw new Error("negative");
            }
            result = 1;
        } catch (e) {
            // swallow the exception, don't rethrow
            const temp = 42;
        }
        if (x < 0) {
            return result; // 0, exception swallowed
        } else {
            return result; // 1
        }
    }
}
