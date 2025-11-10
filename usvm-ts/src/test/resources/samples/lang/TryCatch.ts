// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

class TryCatch {
    emptyTryCatch(): number {
        try {
            return 1;
        } catch (e) {
            return -1; // unreachable
        }
    }

    simpleTryCatch(): number {
        try {
            throw new Error("test");
            return -1; // unreachable
        } catch (e) {
            return 1;
        }
        return -2; // unreachable
    }

    conditionalThrowTryCatch(shouldThrow: boolean): number {
        try {
            if (shouldThrow) {
                throw new Error("conditional error");
                return -1; // unreachable
            }
            return 1;
        } catch (e) {
            return 2;
        }
        return -2; // unreachable
    }

    nestedTryCatch(shouldThrow: boolean): number {
        try {
            try {
                if (shouldThrow) {
                    throw new Error("inner error");
                    return -1; // unreachable
                }
                return 1;
            } catch (e) {
                return 2;
            }
            return -2; // unreachable - inner try-catch always returns
        } catch (e) {
            return -3; // unreachable - inner catch doesn't rethrow
        }
    }

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
        return -2; // unreachable - exception propagates
    }

    tryCatchFinally(shouldThrow: boolean): number {
        let result = 0;
        try {
            if (shouldThrow) {
                throw new Error("test");
                result = -1; // unreachable
            }
            result = 1;
        } catch (e) {
            result = 2;
        } finally {
            result = result + 10;
        }
        return result; // 11 or 12
    }

    catchWithReturn(): number {
        try {
            throw new Error("test");
            return -1; // unreachable
        } catch (e) {
            return 1;
        }
        return -2; // unreachable
    }

    finallyOverridesReturn(shouldThrow: boolean): number {
        try {
            if (shouldThrow) {
                throw new Error("test");
                return -1; // unreachable
            }
            return 1; // will be overridden
        } catch (e) {
            return 2; // will be overridden
        } finally {
            return 100; // always wins
        }
        return -2; // unreachable
    }

    rethrowInCatch(): number {
        try {
            try {
                throw new Error("test");
                return -1; // unreachable
            } catch (e) {
                throw e; // rethrow
                return -2; // unreachable
            }
            return -3; // unreachable
        } catch (e) {
            return 1;
        }
        return -4; // unreachable
    }

    catchDifferentTypes(value: any): number {
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
            }
            return 0; // no exception
        } catch (e) {
            return 1; // caught any exception
        }
        return -1; // unreachable
    }

    multipleReturnsInTry(x: number): number {
        try {
            if (x < 0) {
                return 1;
            }
            if (x === 0) {
                return 2;
            }
            if (x > 0) {
                return 3;
            }
            return -1; // unreachable - all cases covered
        } catch (e) {
            return -2; // unreachable - no throws
        }
        return -3; // unreachable
    }

    finallyWithThrow(): number {
        try {
            return -1; // will be overridden by finally
        } finally {
            throw new Error("finally throws");
            return -2; // unreachable
        }
        return -3; // unreachable
    }

    catchWithoutVariable(): number {
        try {
            throw new Error("test");
            return -1; // unreachable
        } catch {
            return 1; // catch without binding
        }
        return -2; // unreachable
    }

    conditionalRethrow(shouldRethrow: boolean): number {
        try {
            try {
                throw new Error("test");
                return -1; // unreachable
            } catch (e) {
                if (shouldRethrow) {
                    throw e;
                    return -2; // unreachable
                }
                return 1;
            }
            return -3; // unreachable
        } catch (e) {
            return 2;
        }
        return -4; // unreachable
    }

    multipleCatchPaths(x: number): number {
        try {
            if (x === 0) {
                throw new Error("zero");
            }
            if (x < 0) {
                return 1;
            }
            if (x > 0) {
                throw new Error("positive");
            }
            return -1; // unreachable - all cases covered
        } catch (e) {
            return 2;
        }
        return -2; // unreachable
    }

    finallyModifiesVariable(shouldThrow: boolean): number {
        let x = 0;
        try {
            x = 10;
            if (shouldThrow) {
                throw new Error("test");
                x = -1; // unreachable
            }
            x = 20;
        } catch (e) {
            x = 30;
        } finally {
            x = x + 5;
        }
        return x; // 25 or 35
    }

    earlyReturnInFinally(): number {
        try {
            return -1; // will be overridden by finally
        } finally {
            if (true) {
                return 1; // always executed
            }
            return -2; // unreachable
        }
        return -3; // unreachable
    }

    // Realistic scenarios combining try-catch with other constructs

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
        return sum;
    }

    tryCatchWithObjectAccess(obj: any): number {
        try {
            if (obj === null || obj === undefined) {
                return 1; // null/undefined check
            }
            const value = obj.x;
            if (value === 42) {
                return 2;
            }
            return 3;
        } catch (e) {
            return 4; // property access error
        }
        return -1; // unreachable
    }

    tryCatchWithArrayAccess(arr: any, index: number): number {
        try {
            if (arr === null || arr === undefined) {
                throw new Error("null array");
            }
            const value = arr[index];
            if (value === undefined) {
                return 1; // out of bounds or missing element
            }
            if (value > 10) {
                return 2;
            }
            return 3;
        } catch (e) {
            return 4; // caught exception
        }
        return -1; // unreachable
    }

    tryCatchWithFunctionCall(shouldFail: boolean): number {
        function mayThrow(flag: boolean): number {
            if (flag) {
                throw new Error("function error");
            }
            return 42;
        }

        try {
            const result = mayThrow(shouldFail);
            if (result > 40) {
                return 1;
            }
            return 2;
        } catch (e) {
            return 3;
        }
        return -1; // unreachable
    }

    tryCatchWithMultipleConditions(x: number, y: number): number {
        try {
            if (x < 0 && y < 0) {
                throw new Error("both negative");
            }
            if (x < 0 || y < 0) {
                return 1; // one negative
            }
            if (x === 0 && y === 0) {
                return 2; // both zero
            }
            if (x > 0 && y > 0) {
                return 3; // both positive
            }
            return 4; // mixed
        } catch (e) {
            return 5; // exception caught
        }
        return -1; // unreachable
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
        try {
            if (x < 0) {
                return 1;
            }
            if (x === 0) {
                throw new Error("zero");
            }
            return 2;
        } catch (e) {
            if (x === 0) {
                return 3;
            }
            return 4;
        }
        return -1; // unreachable
    }

    // cascadingExceptions(level: number): number {
    //     try {
    //         if (level >= 3) {
    //             throw new Error("level 3");
    //         }
    //         try {
    //             if (level >= 2) {
    //                 throw new Error("level 2");
    //             }
    //             try {
    //                 if (level >= 1) {
    //                     throw new Error("level 1");
    //                 }
    //                 return 1; // level 0
    //             } catch (e) {
    //                 return 2; // caught level 1
    //             }
    //         } catch (e) {
    //             return 3; // caught level 2
    //         }
    //     } catch (e) {
    //         return 4; // caught level 3
    //     }
    //     return -1; // unreachable
    // }

    tryCatchWithLogicalOps(a: boolean, b: boolean): number {
        try {
            if (a && b) {
                throw new Error("both true");
            }
            if (a || b) {
                return 1; // at least one true
            }
            return 2; // both false
        } catch (e) {
            if (a) {
                return 3; // a is true
            }
            return 4; // should be unreachable
        }
        return -1; // unreachable
    }

    finallyWithSideEffects(x: number): number {
        let counter = 0;
        try {
            counter++;
            if (x < 0) {
                throw new Error("negative");
            }
            counter++;
            if (x === 0) {
                return counter; // 2
            }
            counter++;
            return counter; // 3
        } catch (e) {
            counter += 10;
            return counter; // 11
        } finally {
            counter += 100; // always executed, but return already happened
        }
        return -1; // unreachable
    }

    exceptionInComplexExpression(x: number, y: number): number {
        try {
            const temp = (x > 0 && y > 0) ? x + y : x - y;
            if (temp === 0) {
                throw new Error("zero result");
            }
            if (temp > 0) {
                return 1;
            }
            return 2;
        } catch (e) {
            return 3;
        }
        return -1; // unreachable
    }

    tryCatchWithTernary(x: number): number {
        try {
            const value = x > 0 ? (x > 10 ? 1 : 2) : (x < -10 ? 3 : 4);
            if (value === 2) {
                throw new Error("case 2");
            }
            return value;
        } catch (e) {
            return 5;
        }
        return -1; // unreachable
    }

    exceptionAfterMultipleOps(x: number): number {
        try {
            let temp = x;
            temp = temp + 5;
            temp = temp * 2;
            if (temp > 20) {
                throw new Error("too large");
            }
            temp = temp - 3;
            return temp;
        } catch (e) {
            return 100;
        }
        return -1; // unreachable
    }

    tryCatchWithEarlyExit(x: number, y: number): number {
        try {
            if (x === 0) {
                return 1; // early exit
            }
            const ratio = y;
            if (ratio < 0) {
                throw new Error("negative ratio");
            }
            if (ratio === 0) {
                return 2;
            }
            return 3;
        } catch (e) {
            return 4;
        }
        return -1; // unreachable
    }

    finallyDoesNotCatchException(shouldThrow: boolean): number {
        try {
            if (shouldThrow) {
                throw new Error("test");
            }
            return 1;
        } finally {
            const temp = 42; // side effect, doesn't catch
        }
        return -1; // unreachable when exception thrown
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
        return result; // 112 or 1011
    }

    tryCatchSwallowsException(x: number): number {
        try {
            if (x < 0) {
                throw new Error("negative");
            }
            return 1;
        } catch (e) {
            // swallow the exception, don't rethrow
            const temp = 42;
        }
        return 2; // reachable after catching
    }
}
