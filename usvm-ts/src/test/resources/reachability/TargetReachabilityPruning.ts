// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

class TargetReachabilityPruning {
    criticalFork(x: number): number {
        if (x > 0) {
            return 1;
        }
        return 0;
    }

    loopBackEdge(x: number): number {
        while (x > 0) {
            x = x - 1;
        }
        return 7;
    }

    private helper(): number {
        return 0;
    }

    callMakesInfinityIndeterminate(x: number): number {
        if (x > 0) {
            return 1;
        }
        this.helper();
        return 0;
    }

    pruningFixture(x: number, a: number, b: number, c: number, d: number): number {
        if (x > 0) {
            return 1;
        }

        let score = 0;
        if (a > 0) {
            score = score + 1;
        } else {
            score = score - 1;
        }
        if (b > 0) {
            score = score + 2;
        } else {
            score = score - 2;
        }
        if (c > 0) {
            score = score + 4;
        } else {
            score = score - 4;
        }
        if (d > 0) {
            score = score + 8;
        } else {
            score = score - 8;
        }
        return score;
    }
}
