// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

declare class External {
    static value(): number;
}

export class SharedAnalysis {
    identity(value: any): any {
        return value;
    }

    success(): number {
        return 7;
    }

    unknown(): number {
        const result = External.value();
        return result;
    }

    limited(value: string): string {
        return value + value;
    }

    observed(): number {
        this.callee();
        const result = 7;
        return result;
    }

    callee(): void {
        return;
    }
}
