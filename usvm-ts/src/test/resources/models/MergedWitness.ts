export interface MergedFirst { value: number; }
export class MergedFirst {
    value: number = 17;
    static first(): MergedFirst { return new MergedFirst(); }
}

export class MergedLast {
    value: number = 19;
    static last(): MergedLast { return new MergedLast(); }
}
export interface MergedLast { value: number; }
