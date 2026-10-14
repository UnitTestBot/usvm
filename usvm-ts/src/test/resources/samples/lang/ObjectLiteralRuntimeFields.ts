// @ts-nocheck

class ObjectLiteralRuntimeFields {
    forwardOptionalBoolean(present: boolean, include: boolean): number {
        let options: { alwaysIncludeMinute?: boolean } | undefined = undefined;
        if (present) {
            options = { alwaysIncludeMinute: include };
        }

        return this.readForwarded({ alwaysIncludeMinute: options?.alwaysIncludeMinute });
    }

    private readForwarded(options: { alwaysIncludeMinute?: boolean }): number {
        if (options.alwaysIncludeMinute === undefined) return -1;
        if (options.alwaysIncludeMinute) return 1;
        return 0;
    }

    branchWrites(change: boolean): number {
        const literal = { flag: true };
        if (change) {
            literal.flag = undefined;
        } else {
            literal.flag = false;
        }

        if (literal.flag === undefined) return 1;
        return literal.flag === false ? 2 : 3;
    }

    declaredLiteralFieldsKeepRuntimeKinds(): number {
        const literal = { flag: true, amount: 5, text: "old" };

        literal.flag = undefined;
        if (literal.flag !== undefined) return -1;

        literal.amount = false;
        if (literal.amount !== false) return -2;

        literal.text = 9;
        if (typeof literal.text !== "number" || literal.text !== 9) return -3;

        literal.flag = true;
        if (literal.flag !== true) return -4;
        return 1;
    }
}
