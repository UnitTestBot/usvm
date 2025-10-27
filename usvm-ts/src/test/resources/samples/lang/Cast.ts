// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

class Cast {
    castAnyToNumber(x: any): number {
        const y = x as number;
        if (y === 1) {
            return 1;
        }
        return 0;
    }
}
