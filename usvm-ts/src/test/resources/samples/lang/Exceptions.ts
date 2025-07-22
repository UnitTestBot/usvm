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
}
