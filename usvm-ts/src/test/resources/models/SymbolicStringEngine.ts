// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

export class SymbolicStringEngine {
    inputRoundtrip(value: string): string {
        return value;
    }

    length(): number {
        return "\ud800\udc00".length;
    }

    charCodeAt(): number {
        return "\ud800".charCodeAt(0);
    }

    charAtEquals(): boolean {
        return "\ud800".charAt(0) === "\ud800";
    }

    indexedReadEquals(): boolean {
        return "\udc00"[0] === "\udc00";
    }

    outOfRangeCharAt(): string {
        return "x".charAt(1);
    }

    anyAliasLength(): number {
        const value: any = "\ud800";
        return value.length;
    }
}
