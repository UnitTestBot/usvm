// noinspection JSUnusedGlobalSymbols

class TypedStaticNumberWrite {
    static source: any = 7;
    static destination: number = 0;

    static copy(): number {
        this.destination = this.source;
        return this.destination;
    }

    static copyConditionalBranch(useNumber: boolean): number {
        const value: any = useNumber ? 9 : false;
        this.destination = value;
        return this.destination;
    }
}

class TypedStaticBooleanWrite {
    static source: any = true;
    static destination: boolean = false;

    static copy(): boolean {
        this.destination = this.source;
        return this.destination;
    }
}
