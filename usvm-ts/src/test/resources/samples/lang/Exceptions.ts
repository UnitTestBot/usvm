// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

class Exceptions {
    simpleThrow(): number {
        throw new Error("test");
        return 42;
    }

    throwString(): number {
        throw "error message";
        return 42;
    }

    throwNumber(): number {
        throw 123;
        return 42;
    }

    throwBoolean(): number {
        throw true;
        return 42;
    }

    throwNull(): number {
        throw null;
        return 42;
    }

    throwUndefined(): number {
        throw undefined;
        return 42;
    }

    conditionalThrow(shouldThrow: boolean): number {
        if (shouldThrow) {
            throw new Error("conditional error");
        }
        return 42;
    }

    conditionalCatch(value: number): number {
        try {
            if (value === 0) {
                throw 7;
            }
            return 1;
        } catch {
            return 2;
        }
    }

    caughtValue(value: number): number {
        try {
            throw value;
        } catch (error) {
            return error + 1;
        }
    }

    nestedCatch(value: number): number {
        try {
            try {
                throw value;
            } catch (inner) {
                return inner + 1;
            }
        } catch (outer) {
            return 99;
        }
    }

    throwsValue(value: number): number {
        throw value;
    }

    catchesCall(value: number): number {
        try {
            return this.throwsValue(value);
        } catch (error) {
            return error + 1;
        }
    }

    rethrowToOuter(value: number): number {
        try {
            try {
                throw value;
            } catch (inner) {
                throw inner;
            }
        } catch (outer) {
            return outer + 1;
        }
    }
}
