// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

export class DateEtsIr {
    fixedClock(): number {
        return Date.now() - new Date().getTime();
    }

    epochYear(): number {
        return new Date(0).getUTCFullYear();
    }

    isoEpoch(): string {
        return new Date(0).toISOString();
    }

    leapDay(): number {
        const date = new Date(Date.UTC(2000, 1, 29, 12, 34, 56, 789));
        return date.getUTCMonth() * 100 + date.getUTCDate();
    }

    overflow(): number {
        const date = new Date(Date.UTC(2024, -1, 0, 25, -1, 0, 0));
        return date.getUTCFullYear() * 10_000 + (date.getUTCMonth() + 1) * 100 + date.getUTCDate();
    }

    utcNoArguments(): number {
        return Date.UTC();
    }

    utcUndefinedYear(): number {
        return Date.UTC(undefined);
    }

    utcYearOnly(): number {
        return Date.UTC(2020);
    }

    utcExplicitUndefined(): number {
        return Date.UTC(2020, undefined);
    }

    invalidTimezoneOffset(): number {
        return new Date(NaN).getTimezoneOffset();
    }

    fractionalTimestamps(): number {
        return new Date(1.9).getTime() * 10 + new Date(-1.9).getTime();
    }

    fractionalUtcDay(): number {
        return new Date(Date.UTC(2024, 0, 1.9)).getUTCDate();
    }

    anyAliasValueOf(): number {
        const date: any = new Date(123);
        return date.valueOf();
    }

    anyAliasGetTime(): number {
        const date: any = new Date(456);
        return date.getTime();
    }

    setter(): number {
        const date = new Date(0);
        const timestamp = date.setUTCFullYear(2000, 1, 29);
        return timestamp + date.getUTCDate();
    }

    symbolicRoundTrip(timestamp: number): number {
        return new Date(timestamp).valueOf();
    }
}
