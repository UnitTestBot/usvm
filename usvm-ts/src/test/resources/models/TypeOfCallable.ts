// @ts-nocheck

function namedCallable(): number {
    return 1;
}

class TypeOfCallable {
    arrow(): string {
        return typeof (() => 1);
    }

    functionExpression(): string {
        return typeof function () { return 1; };
    }

    storedArrow(flag: boolean): string {
        const callable = () => flag ? 1 : 0;
        return typeof callable;
    }

    storedInObject(): string {
        const holder = { callable: () => 1 };
        return typeof holder.callable;
    }

    namedFunction(): string {
        return typeof namedCallable;
    }

    conditionalValue(flag: boolean): string {
        const value = flag ? (() => 1) : {};
        return typeof value;
    }

    ordinaryObject(): string {
        return typeof {};
    }

    nullValue(): string {
        return typeof null;
    }

    stringValue(): string {
        return typeof "value";
    }

    numberValue(): string {
        return typeof 1;
    }

    booleanValue(): string {
        return typeof true;
    }

    undefinedValue(): string {
        return typeof undefined;
    }
}
