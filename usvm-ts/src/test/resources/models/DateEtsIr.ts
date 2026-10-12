// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

export class DateEtsIr {
    fixedClock(): number {
        return Date.now() - new Date().getTime();
    }

    primitiveNamedDate(): number {
        const Date = 123;
        return Date.valueOf();
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

    utcMinuteSetters(): number {
        const date = new Date(0);
        const minutesTimestamp = date.setUTCMinutes(61, -2, 1_001);
        const millisecondsTimestamp = date.setUTCMilliseconds(-1);
        return minutesTimestamp + millisecondsTimestamp;
    }

    setter(): number {
        const date = new Date(0);
        const timestamp = date.setUTCFullYear(2000, 1, 29);
        return timestamp + date.getUTCDate();
    }

    deletePublicTimestamp(): number {
        const date: any = new Date(456);
        delete date.timestamp;
        return date.getTime();
    }

    symbolicRoundTrip(timestamp: number): number {
        if (timestamp === 123.9) {
            return new Date(timestamp).valueOf();
        }
        if (timestamp === -123.9) {
            return new Date(timestamp).valueOf();
        }

        return new Date(timestamp).valueOf();
    }

    undefinedConstructor(): number {
        return new Date(undefined).getTime();
    }

    undefinedConstructorComponent(): number {
        return new Date(2024, 0, undefined).getTime();
    }

    missingSetTime(): boolean {
        const date = new Date(0);

        const result = date.setTime();

        return Number.isNaN(result) && Number.isNaN(date.getTime());
    }

    missingSetMonth(): boolean {
        const date = new Date(0);

        const result = date.setUTCMonth();

        return Number.isNaN(result) && Number.isNaN(date.getTime());
    }

    missingSetMinutes(): boolean {
        const date = new Date(0);

        const result = date.setUTCMinutes();

        return Number.isNaN(result) && Number.isNaN(date.getTime());
    }

    undefinedSetDate(): boolean {
        const date = new Date(0);

        const result = date.setUTCDate(undefined);

        return Number.isNaN(result) && Number.isNaN(date.getTime());
    }

    undefinedOptionalSetterComponent(): boolean {
        const date = new Date(0);

        const result = date.setUTCFullYear(2024, undefined);

        return Number.isNaN(result) && Number.isNaN(date.getTime());
    }

    omittedOptionalSetterComponents(): boolean {
        const date = new Date(Date.UTC(2024, 0, 2, 3, 4, 5, 6));

        date.setUTCFullYear(2025);
        date.setUTCHours(8);

        return date.getUTCFullYear() === 2025 && date.getUTCMonth() === 0 && date.getUTCDate() === 2
            && date.getUTCHours() === 8 && date.getUTCMinutes() === 4
            && date.getUTCSeconds() === 5 && date.getUTCMilliseconds() === 6;
    }

    copiedDateKeepsItsOwnTimestamp(): number {
        const original = new Date(123);
        const copy = new Date(original);

        original.setTime(456);

        return copy.getTime();
    }

    copiedInvalidDateStaysInvalid(): number {
        return new Date(new Date(NaN)).getTime();
    }

    primitiveConstructorCoercions(): number {
        return new Date(true).getTime() * 10 + new Date(false).getTime() + new Date(null).getTime();
    }

    boxedConstructorArgument(useNumber: boolean): number {
        const values: any[] = [123, undefined];
        return new Date(values[useNumber ? 0 : 1]).getTime();
    }

    boxedSetterArgument(useNumber: boolean): number {
        const date = new Date(0);
        const values: any[] = [123, undefined];

        const result = date.setTime(values[useNumber ? 0 : 1]);

        return result + date.getTime();
    }

    boxedPrimitiveComponents(): number {
        const values: any[] = [true, null];
        const date = new Date(0);

        date.setUTCSeconds(values[0], values[1]);

        return date.getTime() + Date.UTC(1970, values[1], values[0]);
    }

    boxedUnsupportedArgument(useNumber: boolean): number {
        const values: any[] = [123, new DateImpostor()];
        return new Date(values[useNumber ? 0 : 1]).getTime();
    }

    boxedDateCopy(): number {
        const original = new Date(123);
        const values: any[] = [original, false];
        const copy = new Date(values[0]);

        original.setTime(456);

        return copy.getTime();
    }

    foreignReceiverThroughAny(): number {
        const impostor: any = new DateImpostor();
        return impostor.getTime();
    }
}

class DateImpostor {}
