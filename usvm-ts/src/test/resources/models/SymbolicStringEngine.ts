// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

export class SymbolicStringEngine {
    inputRoundtrip(value: string): string {
        return value;
    }

    length(): number {
        return "\ud800\udc00".length;
    }

    indexedReadEquals(): boolean {
        return "\udc00"[0] === "\udc00";
    }

    outOfRangeIndex(): boolean {
        return "x"[1] === undefined;
    }

    anyAliasLength(): number {
        const value: any = "\ud800";
        return value.length;
    }
}
