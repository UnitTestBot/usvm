// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

class UnrelatedPropertyOwner {
    fresh: string;
}

class PropertyBase {
    inherited: number;
    redeclared?: string;
}

class PropertyChild extends PropertyBase {
    redeclared: string;
}

class SymbolicProperties {
    inheritedFields(obj: PropertyChild): number {
        return "inherited" in obj && "redeclared" in obj && obj.redeclared !== undefined ? 1 : -1;
    }

    required(obj: { x: number }): number {
        return "x" in obj ? 1 : 0;
    }

    optional(obj: { x?: number }): number {
        return "x" in obj ? 1 : 0;
    }

    unknown(obj: Object): number {
        return "fresh" in obj ? 1 : 0;
    }

    repeatedPresence(obj: Object): number {
        const before = "fresh" in obj;
        return before === ("fresh" in obj) ? 1 : -1;
    }

    absentRead(obj: { x?: number }): number {
        if ("x" in obj) return 2;
        return obj.x === undefined ? 1 : -1;
    }

    readsUnknown(obj: Object): number {
        if (!("fresh" in obj)) return obj.fresh === undefined ? 0 : -1;
        if (typeof obj.fresh === "number") return 1;
        if (typeof obj.fresh === "boolean") return 2;
        return 3;
    }

    writesNumber(obj: Object): number {
        obj.fresh = 42;
        return "fresh" in obj && obj.fresh === 42 ? 1 : -1;
    }

    writesBoolean(obj: Object): number {
        obj.fresh = false;
        return "fresh" in obj && obj.fresh === false ? 1 : -1;
    }

    writesString(obj: Object): number {
        obj.fresh = "hello";
        return "fresh" in obj && obj.fresh === "hello" && obj.fresh.length === 5 ? 1 : -1;
    }

    rewritesLongLiteral(obj: { text: "initial value exceeds the length bound" }): number {
        obj.text = "short";
        return "text" in obj && obj.text === "short" ? 1 : -1;
    }

    deletesLongLiteral(obj: { text: "initial value exceeds the length bound" }): number {
        delete obj.text;
        return !("text" in obj) && obj.text === undefined ? 1 : -1;
    }

    writesUndefined(obj: Object): number {
        obj.fresh = undefined;
        return "fresh" in obj && obj.fresh === undefined ? 1 : -1;
    }

    writesNull(obj: Object): number {
        obj.fresh = null;
        return "fresh" in obj && obj.fresh === null ? 1 : -1;
    }

    writesObject(obj: Object): number {
        obj.fresh = { nested: 7 };
        return "fresh" in obj && obj.fresh.nested === 7 ? 1 : -1;
    }

    writesArray(obj: Object): number {
        obj.fresh = [4, 0];
        obj.fresh[1] = 8;
        return "fresh" in obj && obj.fresh[1] === 8 ? 1 : -1;
    }

    changesDeclaredKind(obj: { x: number }): number {
        obj.x = "changed";
        return "x" in obj && obj.x === "changed" ? 1 : -1;
    }

    changesKinds(obj: Object): number {
        obj.fresh = 1;
        if (obj.fresh !== 1) return -1;
        obj.fresh = true;
        if (obj.fresh !== true) return -2;
        obj.fresh = "a";
        if (obj.fresh !== "a") return -3;
        obj.fresh = undefined;
        return "fresh" in obj && obj.fresh === undefined ? 1 : -4;
    }

    deletes(obj: { x: number }): number {
        delete obj.x;
        return !("x" in obj) && obj.x === undefined ? 1 : -1;
    }

    deletesUnknown(obj: Object): number {
        delete obj.fresh;
        return !("fresh" in obj) && obj.fresh === undefined ? 1 : -1;
    }

    restores(obj: Object): number {
        obj.fresh = 1;
        delete obj.fresh;
        if ("fresh" in obj || obj.fresh !== undefined) return -1;
        obj.fresh = "restored";
        return "fresh" in obj && obj.fresh === "restored" ? 1 : -2;
    }

    conditionalWrite(obj: Object, write: boolean): number {
        delete obj.fresh;
        if (write) obj.fresh = 7;
        if ("fresh" in obj) return obj.fresh === 7 ? 1 : -1;
        return obj.fresh === undefined ? 0 : -2;
    }

    conditionalDelete(obj: { x: number }, remove: boolean): number {
        if (remove) delete obj.x;
        if ("x" in obj) return 1;
        return obj.x === undefined ? 0 : -1;
    }

    conditionalKinds(obj: Object, flag: boolean): number {
        if (flag) obj.fresh = 3;
        else obj.fresh = "s";
        if (!("fresh" in obj)) return -1;
        return flag ? (obj.fresh === 3 ? 1 : -2) : (obj.fresh === "s" ? 0 : -3);
    }

    localAlias(obj: Object): number {
        const alias = obj;
        alias.fresh = 9;
        if (!("fresh" in obj) || obj.fresh !== 9) return -1;
        delete obj.fresh;
        return !("fresh" in alias) && alias.fresh === undefined ? 1 : -2;
    }

    aliasWrite(a: Object, b: Object): number {
        delete b.fresh;
        a.fresh = 13;
        if (a === b) return "fresh" in b && b.fresh === 13 ? 1 : -1;
        return !("fresh" in b) && b.fresh === undefined ? 0 : -2;
    }

    aliasStringChange(a: Object, b: Object): number {
        delete b.fresh;
        a.fresh = "alias";
        if (a !== b) return 0;
        return "fresh" in b && b.fresh === "alias" && b.fresh.length === 5 ? 1 : -1;
    }

    aliasObjectChange(a: Object, b: Object): number {
        delete b.fresh;
        a.fresh = { nested: 7, length: 2 };
        if (a !== b) return 0;
        if (!("nested" in b.fresh) || b.fresh["nested"] !== 7 || b.fresh.length !== 2) return -1;
        b.fresh.nested = 8;
        if (a.fresh.nested !== 8) return -2;
        delete b.fresh["nested"];
        if ("nested" in a.fresh || a.fresh.nested !== undefined) return -3;
        a.fresh.nested = 9;
        return "nested" in b.fresh && b.fresh.nested === 9 ? 1 : -4;
    }

    aliasArrayChange(a: Object, b: Object): number {
        delete b.fresh;
        a.fresh = [4, 0];
        if (a !== b) return 0;
        b.fresh[1] = 8;
        return "fresh" in b && a.fresh[1] === 8 ? 1 : -1;
    }

    lengthProperty(obj: Object): number {
        obj.length = 7;
        if (!("length" in obj) || obj.length !== 7) return -1;
        delete obj.length;
        return !("length" in obj) && obj.length === undefined ? 1 : -2;
    }

    unusualKeys(obj: Object): number {
        obj[""] = 1;
        obj["поле😀"] = false;
        return "" in obj && "поле😀" in obj && obj[""] === 1 && obj["поле😀"] === false ? 1 : -1;
    }

    aliasKindChange(a: { x: number }, b: { x: number }): number {
        if (a !== b) return 0;
        a.x = false;
        return "x" in b && b.x === false ? 1 : -1;
    }

    aliasDelete(a: { x: number }, b: { x: number }): number {
        delete a.x;
        if (a === b) return !("x" in b) && b.x === undefined ? 1 : -1;
        return "x" in b ? 0 : -2;
    }

    writesSymbolic(obj: Object, value: number): number {
        obj.fresh = value;
        return "fresh" in obj && obj.fresh === value ? 1 : -1;
    }

    writesSymbolicBoolean(obj: Object, value: boolean): number {
        obj.fresh = value;
        return "fresh" in obj && obj.fresh === value ? 1 : -1;
    }

    writesSymbolicString(obj: Object, value: string): number {
        obj.fresh = value;
        return "fresh" in obj && obj.fresh === value && obj.fresh.length === value.length ? 1 : -1;
    }

    writesSymbolicObject(obj: Object, value: { x: number }): number {
        obj.fresh = value;
        obj.fresh.x = 8;
        return "fresh" in obj && obj.fresh === value && value.x === 8 ? 1 : -1;
    }

    copiesUnknown(a: Object, b: Object): number {
        a.copy = b.fresh;
        return "copy" in a && (a.copy === b.fresh || a.copy !== a.copy) ? 1 : -1;
    }

    bracketLiteral(obj: Object): number {
        obj["a-b"] = 21;
        return "a-b" in obj && obj["a-b"] === 21 ? 1 : -1;
    }

    deletesBracketLiteral(obj: Object): number {
        obj["a-b"] = undefined;
        if (!("a-b" in obj)) return -1;
        delete obj["a-b"];
        return !("a-b" in obj) && obj["a-b"] === undefined ? 1 : -2;
    }

    numericLiteralKey(obj: Object): number {
        obj[12] = "v";
        if (!(12 in obj) || obj[12] !== "v") return -1;
        delete obj[12];
        return !("12" in obj) ? 1 : -2;
    }

    comparesUnknownValues(obj: Object): number {
        const value = obj.fresh;
        if (typeof value === "number") return value === obj.fresh ? 1 : 2;
        return value === obj.fresh ? 0 : -1;
    }

    nestedInput(obj: { inner: { x: number } }): number {
        obj.inner.fresh = 5;
        return "fresh" in obj.inner && obj.inner.fresh === 5 ? 1 : -1;
    }

    optionalUndefinedPresence(obj: { x?: number }): number {
        if (!("x" in obj)) return obj.x === undefined ? 0 : -1;
        return obj.x === undefined ? 1 : 2;
    }

    optionalString(obj: { text?: string }): number {
        if (!("text" in obj)) return obj.text === undefined ? 0 : -1;
        if (obj.text === undefined) return 3;
        return obj.text === "hi" ? 1 : 2;
    }

    returnsWrittenObject(obj: Object): Object {
        obj.fresh = { x: 7 };
        return obj;
    }

    returnsDeletedObject(obj: { x: number }): Object {
        delete obj.x;
        return obj;
    }

    prototypeName(obj: Object): number {
        return "toString" in obj ? 1 : 0;
    }

    symbolicKey(obj: Object, key: string): number {
        return key in obj ? 1 : 0;
    }
}
