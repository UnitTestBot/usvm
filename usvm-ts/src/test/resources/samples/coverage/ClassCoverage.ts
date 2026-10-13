// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

class HelperCoverage {
    static entry(value: number): number {
        return HelperCoverage.choose(value);
    }

    private static choose(value: number): number {
        if (value > 0) {
            return 1;
        }

        return 2;
    }

    static unvisited(): number {
        return 3;
    }
}

class ConstructorCoverage {
    private value: number;

    constructor(value: number) {
        if (value > 0) {
            this.value = 1;
        } else {
            this.value = 2;
        }
    }

    static entry(value: number): number {
        const instance = new ConstructorCoverage(value);

        return 0;
    }
}

class ForeignCaller {
    static entry(value: number): number {
        return ForeignHelper.choose(value);
    }
}

class ForeignHelper {
    static choose(value: number): number {
        if (value > 0) {
            return 10;
        }

        return 20;
    }
}
