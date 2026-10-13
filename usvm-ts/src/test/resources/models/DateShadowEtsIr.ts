// @ts-nocheck
declare class Date {
    static UTC(year: number): number;
}

export class DateShadowEtsIr {
    call(): number {
        return Date.UTC(2020);
    }
}
