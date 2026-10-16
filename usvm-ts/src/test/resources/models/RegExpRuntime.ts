export class RegExpRuntime {
    static dateMath(value: string): number {
        const match = value.match(/^now([+-])(\d+)([mhdMywQ])(?:\/(\w))?$/);
        if (match === null) return 0;
        if (match[4] === undefined) return 1;
        return 2;
    }

    static captures(): boolean {
        const match = "now-15m/d".match(/^now([+-])(\d+)([mhdMywQ])(?:\/(\w))?$/);
        if (match === null) return false;
        return match.length === 5 && match[0] === "now-15m/d" && match[1] === "-"
            && match[2] === "15" && match[3] === "m" && match[4] === "d"
            && match.index === 0 && match.input === "now-15m/d" && match.groups === undefined;
    }

    static missingCapture(): boolean {
        const match = "now+1h".match(/^now([+-])(\d+)([mhdMywQ])(?:\/(\w))?$/);
        if (match === null) return false;
        return match.length === 5 && match[4] === undefined;
    }

    static testLetters(value: string): boolean {
        if (/[a-zA-Z]/.test(value)) return true;
        return false;
    }

    static testRepeated(value: string): boolean {
        if (/^D+$/.test(value)) return true;
        return false;
    }

    static leftmost(): boolean {
        const match = "zabab".match(/(ab)/);
        if (match === null) return false;
        return match.index === 1 && match[0] === "ab" && match[1] === "ab" && match.input === "zabab";
    }

    static emptyCapture(): boolean {
        const match = "".match(/^()$/);
        if (match === null) return false;
        return match.length === 2 && match[0] === "" && match[1] === "";
    }

    static finalNewlines(): boolean {
        return /^D+$/.test("D") && !/^D+$/.test("D\n") && !/^D+$/.test("D\r")
            && !/^D+$/.test("D\r\n") && !/^D+$/.test("D\u2028") && !/^D+$/.test("D\u2029")
            && "now+1m\n".match(/^now([+-])(\d+)([mhdMywQ])(?:\/(\w))?$/) === null;
    }

    static utf16AndEscapes(): boolean {
        const match = "😀".match(/^(😀)$/);
        if (match === null) return false;
        return match[1] === "😀" && /[😀]/.test("\uD83D") && /[😀]/.test("\uDE00")
            && !/\w/.test("é") && /\w/.test("_") && /^a\.b\/c$/.test("a.b/c");
    }

    static freshResults(): boolean {
        const expression = /^(a)$/;
        const first = "a".match(expression);
        const second = "a".match(expression);
        if (first === null || second === null) return false;
        first[1] = "changed";
        return first !== second && second[1] === "a";
    }

    static lastIndex(): boolean {
        const expression = /^D+$/;
        expression.lastIndex = 7;
        return expression.test("DD") && expression.lastIndex === 7;
    }

    static literalProperties(): boolean {
        const expression = /^a\.b$/;
        return expression.source === "^a\\.b$" && expression.flags === "" && !expression.global
            && !expression.ignoreCase && !expression.multiline && !expression.sticky && !expression.unicode;
    }

    static bracketProperties(): boolean {
        const expression = /^D+$/;
        expression["lastIndex"] = 7;
        return expression["lastIndex"] === 7 && expression["source"] === "^D+$"
            && expression["flags"] === "" && expression.test("DD");
    }

    static overriddenTest(): boolean {
        const expression = /^D+$/;
        expression.test = (value: string): boolean => false;
        return expression.test("D");
    }

    static customMatch(): number {
        const custom = { match: (expression: RegExp): number => 7 };
        return custom.match(/x/);
    }

    static unsupportedFlags(): boolean { return /x/i.test("X"); }
    static unsupportedBacktracking(): boolean { return /^a+ab$/.test("aaab"); }
    static unsupportedCoercion(): boolean {
        const value: any = 7;
        return /7/.test(value);
    }
    static unsupportedUnanchoredRun(): boolean { return /a+/.test("aa"); }
    static unsupportedExecOverride(): boolean {
        const expression = /x/;
        expression.exec = (value: string): RegExpExecArray | null => null;
        return expression.test("x");
    }

    static unsupportedPropertyWrite(): boolean {
        const expression: any = /x/;
        expression.source = "different";
        return expression.test("x");
    }

    static unsupportedPropertyRead(): boolean {
        const expression: any = /x/;
        return expression.unknownProperty === undefined;
    }

}
