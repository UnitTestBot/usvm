function sloppyWrite(value: string, index: number): boolean {
    const before = value.charAt(0);
    const beforeLength = value.length;
    const alias = value;
    const target: any = value;
    let effects = 0;
    const assigned = (target[index] = (effects++, "x"));
    return effects === 1 && assigned === "x" && value === alias &&
        value.length === beforeLength && value.charAt(0) === before;
}

function sloppyKeys(): boolean {
    const value: any = "abc";
    value["0"] = "x";
    value["01"] = "x";
    value[-1] = "x";
    value[100] = "x";
    value[0.5] = "x";
    value[NaN] = "x";
    value[Infinity] = "x";
    value["length"] = 1;
    const extra = (value["extra"] = "x");
    const typedValue = "abc";
    typedValue.length = 1;
    return value === "abc" && value.length === 3 && value[0] === "a" && extra === "x" && typedValue.length === 3;
}

function sloppyUpdates(): boolean {
    const value: any = "4";
    const oldValue = value[0]++;
    const newValue = ++value[0];
    return oldValue === 4 && newValue === 5 && value[0] === "4";
}

function sloppyCompound(): boolean {
    let value = "a";
    const assigned = (value[0] += "b");
    return assigned === "ab" && value[0] === "a";
}

function sloppySymbolicUpdate(value: string): number {
    if (value.length !== 1) {
        return NaN;
    }

    const before = value.charAt(0);
    const target: any = value;
    const oldValue = target[0]++;
    if (value.charAt(0) !== before) {
        return Infinity;
    }

    return oldValue;
}

function sloppyWhitespaceUpdate(): boolean {
    const value: any = "\u00a0";
    const oldValue = value[0]++;
    return oldValue === 0 && value[0] === "\u00a0";
}

function ownStrict(value: string): number {
    "use strict";
    const target: any = value;
    try { target[0] = "x"; } catch { return 1; }
    return -1;
}

function inheritedStrict(value: string): number {
    "use strict";
    function nested(): number {
        const target: any = value;
        try { target[0] = "x"; } catch { return 1; }
        return -1;
    }
    return nested();
}

// Strictness follows the callee's lexical source even when the caller uses the other mode.
function sloppyCallee(value: string): number {
    const target: any = value;
    target[0] = "x";
    return 1;
}

function strictCaller(value: string): number {
    "use strict";
    return sloppyCallee(value);
}

function sloppyCaller(value: string): number {
    return ownStrict(value);
}

function inheritedArrow(value: string): number {
    "use strict";
    const nested = () => {
        const target: any = value;
        try { target[0] = "x"; } catch { return 1; }
        return -1;
    };
    return nested();
}

class ScriptClass {
    write(value: string): number {
        const target: any = value;
        try { target[0] = "x"; } catch { return 1; }
        return -1;
    }
}

function classInScript(value: string): number {
    return new ScriptClass().write(value);
}
