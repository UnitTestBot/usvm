// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

function overloaded(value: number): number;
function overloaded(value: string): string;
function overloaded(value: number | string): number | string {
    return typeof value === "number" ? value + 1 : value;
}

class StaticOverloads {
    callOverloaded(): number {
        return overloaded(41);
    }
}
