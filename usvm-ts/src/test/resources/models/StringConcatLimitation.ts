class StringConcatLimitation {
    negativeOnly(value: any): string {
        if (typeof value !== "number") return "skip";

        return "!" + value;
    }

    sibling(flag: boolean, value: any): string {
        if (flag) return "ok";

        return this.negativeOnly(value);
    }
}
