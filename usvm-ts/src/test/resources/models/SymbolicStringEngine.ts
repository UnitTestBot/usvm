// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

export class SymbolicStringEngine {
    inputRoundtrip(value: string): string {
        return value;
    }

    length(): number {
        return "\ud800\udc00".length;
    }

    anyAliasLength(): number {
        const value: any = "\ud800";
        return value.length;
    }
}
