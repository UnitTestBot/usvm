// Numeric Date semantic model. Local-time operations intentionally use UTC.

export class DateValue {
    timestamp: number = NaN;
}

class DateParts {
    year: number;
    month: number;
    date: number;
    day: number;
    hours: number;
    minutes: number;
    seconds: number;
    milliseconds: number;

    constructor(
        year: number,
        month: number,
        date: number,
        day: number,
        hours: number,
        minutes: number,
        seconds: number,
        milliseconds: number,
    ) {
        this.year = year;
        this.month = month;
        this.date = date;
        this.day = day;
        this.hours = hours;
        this.minutes = minutes;
        this.seconds = seconds;
        this.milliseconds = milliseconds;
    }
}

export class DateModels {
    private static readonly MS_PER_SECOND = 1_000;
    private static readonly MS_PER_MINUTE = 60_000;
    private static readonly MS_PER_HOUR = 3_600_000;
    private static readonly MS_PER_DAY = 86_400_000;
    private static readonly MAX_TIME = 8_640_000_000_000_000;

    static construct(
        receiver: DateValue,
        argumentCount: number,
        nowMilliseconds: number,
        yearOrTimestamp: number,
        month: number,
        date: number,
        hours: number,
        minutes: number,
        seconds: number,
        milliseconds: number,
    ): DateValue {
        if (argumentCount === 0) {
            receiver.timestamp = DateModels.timeClip(nowMilliseconds);
            return receiver;
        }

        if (argumentCount === 1) {
            receiver.timestamp = DateModels.timeClip(yearOrTimestamp);
            return receiver;
        }

        receiver.timestamp = DateModels.makeDate(
            DateModels.normalizeConstructorYear(yearOrTimestamp),
            month,
            argumentCount >= 3 ? date : 1,
            argumentCount >= 4 ? hours : 0,
            argumentCount >= 5 ? minutes : 0,
            argumentCount >= 6 ? seconds : 0,
            argumentCount >= 7 ? milliseconds : 0,
        );
        return receiver;
    }

    static utc(
        argumentCount: number,
        year: number,
        month: number,
        date: number,
        hours: number,
        minutes: number,
        seconds: number,
        milliseconds: number,
    ): number {
        if (argumentCount < 1) {
            return NaN;
        }

        return DateModels.makeDate(
            DateModels.normalizeConstructorYear(year),
            argumentCount >= 2 ? month : 0,
            argumentCount >= 3 ? date : 1,
            argumentCount >= 4 ? hours : 0,
            argumentCount >= 5 ? minutes : 0,
            argumentCount >= 6 ? seconds : 0,
            argumentCount >= 7 ? milliseconds : 0,
        );
    }

    static now(nowMilliseconds: number): number {
        return DateModels.timeClip(nowMilliseconds);
    }

    static getDate(receiver: DateValue): number {
        return DateModels.parts(receiver.timestamp).date;
    }

    static getDay(receiver: DateValue): number {
        return DateModels.parts(receiver.timestamp).day;
    }

    static getFullYear(receiver: DateValue): number {
        return DateModels.parts(receiver.timestamp).year;
    }

    static getHours(receiver: DateValue): number {
        return DateModels.parts(receiver.timestamp).hours;
    }

    static getMilliseconds(receiver: DateValue): number {
        return DateModels.parts(receiver.timestamp).milliseconds;
    }

    static getMinutes(receiver: DateValue): number {
        return DateModels.parts(receiver.timestamp).minutes;
    }

    static getMonth(receiver: DateValue): number {
        return DateModels.parts(receiver.timestamp).month;
    }

    static getSeconds(receiver: DateValue): number {
        return DateModels.parts(receiver.timestamp).seconds;
    }

    static getTime(receiver: DateValue): number {
        return receiver.timestamp;
    }

    static getTimezoneOffset(receiver: DateValue): number {
        return DateModels.isInvalid(receiver.timestamp) ? NaN : 0;
    }

    static getUTCDate(receiver: DateValue): number {
        return DateModels.getDate(receiver);
    }

    static getUTCDay(receiver: DateValue): number {
        return DateModels.getDay(receiver);
    }

    static getUTCFullYear(receiver: DateValue): number {
        return DateModels.getFullYear(receiver);
    }

    static getUTCHours(receiver: DateValue): number {
        return DateModels.getHours(receiver);
    }

    static getUTCMilliseconds(receiver: DateValue): number {
        return DateModels.getMilliseconds(receiver);
    }

    static getUTCMinutes(receiver: DateValue): number {
        return DateModels.getMinutes(receiver);
    }

    static getUTCMonth(receiver: DateValue): number {
        return DateModels.getMonth(receiver);
    }

    static getUTCSeconds(receiver: DateValue): number {
        return DateModels.getSeconds(receiver);
    }

    static setDate(receiver: DateValue, date: number): number {
        return DateModels.setDateFields(receiver, 1, date, 0, 0);
    }

    static setFullYear(
        receiver: DateValue,
        argumentCount: number,
        year: number,
        month: number,
        date: number,
    ): number {
        const current = DateModels.partsOrEpoch(receiver.timestamp);
        return DateModels.replaceDate(
            receiver,
            year,
            argumentCount >= 2 ? month : current.month,
            argumentCount >= 3 ? date : current.date,
            current,
        );
    }

    static setHours(
        receiver: DateValue,
        argumentCount: number,
        hours: number,
        minutes: number,
        seconds: number,
        milliseconds: number,
    ): number {
        return DateModels.setTimeFields(receiver, argumentCount, hours, minutes, seconds, milliseconds, 0);
    }

    static setMilliseconds(receiver: DateValue, milliseconds: number): number {
        return DateModels.setTimeFields(receiver, 4, 0, 0, 0, milliseconds, 3);
    }

    static setMinutes(
        receiver: DateValue,
        argumentCount: number,
        minutes: number,
        seconds: number,
        milliseconds: number,
    ): number {
        return DateModels.setTimeFields(receiver, argumentCount + 1, 0, minutes, seconds, milliseconds, 1);
    }

    static setMonth(receiver: DateValue, argumentCount: number, month: number, date: number): number {
        return DateModels.setDateFields(receiver, argumentCount + 1, 0, month, date);
    }

    static setSeconds(receiver: DateValue, argumentCount: number, seconds: number, milliseconds: number): number {
        return DateModels.setTimeFields(receiver, argumentCount + 2, 0, 0, seconds, milliseconds, 2);
    }

    static setTime(receiver: DateValue, timestamp: number): number {
        receiver.timestamp = DateModels.timeClip(timestamp);
        return receiver.timestamp;
    }

    static setUTCDate(receiver: DateValue, date: number): number {
        return DateModels.setDate(receiver, date);
    }

    static setUTCFullYear(
        receiver: DateValue,
        argumentCount: number,
        year: number,
        month: number,
        date: number,
    ): number {
        return DateModels.setFullYear(receiver, argumentCount, year, month, date);
    }

    static setUTCHours(
        receiver: DateValue,
        argumentCount: number,
        hours: number,
        minutes: number,
        seconds: number,
        milliseconds: number,
    ): number {
        return DateModels.setHours(receiver, argumentCount, hours, minutes, seconds, milliseconds);
    }

    static setUTCMilliseconds(receiver: DateValue, milliseconds: number): number {
        return DateModels.setMilliseconds(receiver, milliseconds);
    }

    static setUTCMinutes(
        receiver: DateValue,
        argumentCount: number,
        minutes: number,
        seconds: number,
        milliseconds: number,
    ): number {
        return DateModels.setMinutes(receiver, argumentCount, minutes, seconds, milliseconds);
    }

    static setUTCMonth(receiver: DateValue, argumentCount: number, month: number, date: number): number {
        return DateModels.setMonth(receiver, argumentCount, month, date);
    }

    static setUTCSeconds(receiver: DateValue, argumentCount: number, seconds: number, milliseconds: number): number {
        return DateModels.setSeconds(receiver, argumentCount, seconds, milliseconds);
    }

    static toISOString(receiver: DateValue): string {
        const parts = DateModels.parts(receiver.timestamp);
        if (DateModels.isInvalid(parts.year)) {
            throw new RangeError("Invalid time value");
        }

        return DateModels.formatYear(parts.year) + "-" +
            DateModels.pad2(parts.month + 1) + "-" +
            DateModels.pad2(parts.date) + "T" +
            DateModels.pad2(parts.hours) + ":" +
            DateModels.pad2(parts.minutes) + ":" +
            DateModels.pad2(parts.seconds) + "." +
            DateModels.pad3(parts.milliseconds) + "Z";
    }

    static valueOf(receiver: DateValue): number {
        return receiver.timestamp;
    }

    private static setDateFields(
        receiver: DateValue,
        argumentCount: number,
        first: number,
        second: number,
        third: number,
    ): number {
        if (DateModels.isInvalid(receiver.timestamp)) {
            return DateModels.invalidate(receiver);
        }

        const current = DateModels.parts(receiver.timestamp);
        if (argumentCount === 1) {
            return DateModels.replaceDate(receiver, current.year, current.month, first, current);
        }

        return DateModels.replaceDate(
            receiver,
            current.year,
            second,
            argumentCount >= 3 ? third : current.date,
            current,
        );
    }

    private static setTimeFields(
        receiver: DateValue,
        argumentCount: number,
        hours: number,
        minutes: number,
        seconds: number,
        milliseconds: number,
        firstField: number,
    ): number {
        if (DateModels.isInvalid(receiver.timestamp)) {
            return DateModels.invalidate(receiver);
        }

        const current = DateModels.parts(receiver.timestamp);
        const nextHours = firstField === 0 ? hours : current.hours;
        const nextMinutes = firstField <= 1 && argumentCount >= 2 ? minutes : current.minutes;
        const nextSeconds = firstField <= 2 && argumentCount >= 3 ? seconds : current.seconds;
        const nextMilliseconds = argumentCount >= 4 ? milliseconds : current.milliseconds;

        receiver.timestamp = DateModels.makeDate(
            current.year,
            current.month,
            current.date,
            nextHours,
            nextMinutes,
            nextSeconds,
            nextMilliseconds,
        );
        return receiver.timestamp;
    }

    private static replaceDate(
        receiver: DateValue,
        year: number,
        month: number,
        date: number,
        time: DateParts,
    ): number {
        receiver.timestamp = DateModels.makeDate(
            year,
            month,
            date,
            time.hours,
            time.minutes,
            time.seconds,
            time.milliseconds,
        );
        return receiver.timestamp;
    }

    private static makeDate(
        year: number,
        month: number,
        date: number,
        hours: number,
        minutes: number,
        seconds: number,
        milliseconds: number,
    ): number {
        year = DateModels.toInteger(year);
        month = DateModels.toInteger(month);
        date = DateModels.toInteger(date);
        hours = DateModels.toInteger(hours);
        minutes = DateModels.toInteger(minutes);
        seconds = DateModels.toInteger(seconds);
        milliseconds = DateModels.toInteger(milliseconds);

        if (
            DateModels.isInvalid(year) || DateModels.isInvalid(month) || DateModels.isInvalid(date) ||
            DateModels.isInvalid(hours) || DateModels.isInvalid(minutes) || DateModels.isInvalid(seconds) ||
            DateModels.isInvalid(milliseconds)
        ) {
            return NaN;
        }

        const normalizedYear = year + DateModels.floorDiv(month, 12);
        const normalizedMonth = DateModels.mod(month, 12);
        const days = DateModels.daysFromCivil(normalizedYear, normalizedMonth, date);
        const timestamp = days * DateModels.MS_PER_DAY + hours * DateModels.MS_PER_HOUR +
            minutes * DateModels.MS_PER_MINUTE + seconds * DateModels.MS_PER_SECOND + milliseconds;
        return DateModels.timeClip(timestamp);
    }

    private static parts(timestamp: number): DateParts {
        if (DateModels.isInvalid(timestamp)) {
            return new DateParts(NaN, NaN, NaN, NaN, NaN, NaN, NaN, NaN);
        }

        const normalizedTimestamp = timestamp === 0 ? 0 : timestamp;
        const days = DateModels.floorDiv(normalizedTimestamp, DateModels.MS_PER_DAY);
        let withinDay = normalizedTimestamp - days * DateModels.MS_PER_DAY;
        const hours = DateModels.floorDiv(withinDay, DateModels.MS_PER_HOUR);
        withinDay -= hours * DateModels.MS_PER_HOUR;
        const minutes = DateModels.floorDiv(withinDay, DateModels.MS_PER_MINUTE);
        withinDay -= minutes * DateModels.MS_PER_MINUTE;
        const seconds = DateModels.floorDiv(withinDay, DateModels.MS_PER_SECOND);
        const milliseconds = withinDay - seconds * DateModels.MS_PER_SECOND;

        const civil = DateModels.civilFromDays(days);
        return new DateParts(
            civil.year,
            civil.month,
            civil.date,
            DateModels.mod(days + 4, 7),
            hours,
            minutes,
            seconds,
            milliseconds,
        );
    }

    private static partsOrEpoch(timestamp: number): DateParts {
        return DateModels.isInvalid(timestamp) ? DateModels.parts(0) : DateModels.parts(timestamp);
    }

    private static daysFromCivil(year: number, month: number, date: number): number {
        const adjustedYear = year - (month <= 1 ? 1 : 0);
        const era = DateModels.floorDiv(adjustedYear, 400);
        const yearOfEra = adjustedYear - era * 400;
        const adjustedMonth = month + (month > 1 ? -2 : 10);
        const dayOfYear = DateModels.floorDiv(153 * adjustedMonth + 2, 5) + date - 1;
        const dayOfEra = yearOfEra * 365 + DateModels.floorDiv(yearOfEra, 4) -
            DateModels.floorDiv(yearOfEra, 100) + dayOfYear;
        return era * 146_097 + dayOfEra - 719_468;
    }

    private static civilFromDays(days: number): DateParts {
        const adjustedDays = days + 719_468;
        const era = DateModels.floorDiv(adjustedDays, 146_097);
        const dayOfEra = adjustedDays - era * 146_097;
        const yearOfEra = DateModels.floorDiv(
            dayOfEra - DateModels.floorDiv(dayOfEra, 1_460) +
            DateModels.floorDiv(dayOfEra, 36_524) - DateModels.floorDiv(dayOfEra, 146_096),
            365,
        );
        let year = yearOfEra + era * 400;
        const dayOfYear = dayOfEra - (
            365 * yearOfEra + DateModels.floorDiv(yearOfEra, 4) - DateModels.floorDiv(yearOfEra, 100)
        );
        const monthPrime = DateModels.floorDiv(5 * dayOfYear + 2, 153);
        const date = dayOfYear - DateModels.floorDiv(153 * monthPrime + 2, 5) + 1;
        const month = monthPrime + (monthPrime < 10 ? 2 : -10);
        year += month <= 1 ? 1 : 0;
        return new DateParts(year, month, date, 0, 0, 0, 0, 0);
    }

    private static normalizeConstructorYear(year: number): number {
        year = DateModels.toInteger(year);
        return year >= 0 && year <= 99 ? year + 1900 : year;
    }

    private static timeClip(timestamp: number): number {
        if (DateModels.isInvalid(timestamp) || timestamp > DateModels.MAX_TIME || timestamp < -DateModels.MAX_TIME) {
            return NaN;
        }

        const clipped = DateModels.toInteger(timestamp);
        return clipped === 0 ? 0 : clipped;
    }

    private static toInteger(value: number): number {
        if (value === 0 || DateModels.isInvalid(value)) {
            return value;
        }

        return value < 0 ? -Math.floor(-value) : Math.floor(value);
    }

    private static floorDiv(dividend: number, divisor: number): number {
        return Math.floor(dividend / divisor);
    }

    private static mod(dividend: number, divisor: number): number {
        const remainder = dividend % divisor;
        if (remainder === 0) {
            return 0;
        }

        return remainder < 0 ? remainder + divisor : remainder;
    }

    private static isInvalid(value: number): boolean {
        return value !== value;
    }

    private static invalidate(receiver: DateValue): number {
        receiver.timestamp = NaN;
        return receiver.timestamp;
    }

    private static digit(value: number): string {
        if (value === 0) return "0";
        if (value === 1) return "1";
        if (value === 2) return "2";
        if (value === 3) return "3";
        if (value === 4) return "4";
        if (value === 5) return "5";
        if (value === 6) return "6";
        if (value === 7) return "7";
        if (value === 8) return "8";
        return "9";
    }

    private static pad2(value: number): string {
        return DateModels.digit(DateModels.floorDiv(value, 10)) + DateModels.digit(DateModels.mod(value, 10));
    }

    private static pad3(value: number): string {
        return DateModels.digit(DateModels.floorDiv(value, 100)) +
            DateModels.pad2(DateModels.mod(value, 100));
    }

    private static pad4(value: number): string {
        return DateModels.pad2(DateModels.floorDiv(value, 100)) + DateModels.pad2(DateModels.mod(value, 100));
    }

    private static pad6(value: number): string {
        return DateModels.pad3(DateModels.floorDiv(value, 1_000)) + DateModels.pad3(DateModels.mod(value, 1_000));
    }

    private static formatYear(year: number): string {
        if (year >= 0 && year <= 9_999) {
            return DateModels.pad4(year);
        }

        const sign = year < 0 ? "-" : "+";
        const absoluteYear = year < 0 ? -year : year;
        return sign + DateModels.pad6(absoluteYear);
    }
}
