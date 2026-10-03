class StringHolder {
    value: string;
}

class EmptyLiteralStringHolder {
    value: "" = "";
}

class NonemptyLiteralStringHolder {
    value: "A\u0000\uD83D\uDE00" = "A\u0000\uD83D\uDE00";
}

class SymbolicStringConcat {
    append(value: string): string {
        return value + "!";
    }

    prepend(value: string): string {
        return "\u03a9" + value;
    }

    combine(left: string, right: string): string {
        return left + right;
    }

    combineTwo(left: string, right: string): string {
        if (left.length !== 1) return "";
        if (right.length !== 1) return "";

        return left + right;
    }

    appendLength(value: string): number {
        const result = value + "!";
        return result.length === 1 ? 1 : 0;
    }

    appendEquals(value: string): number {
        if (value.length !== 1) return 3;

        return value + "!" === "a!" ? 1 : 0;
    }

    utf16(value: string): string {
        return value + "\u0000\uD83D\uDE00";
    }

    primitives(value: string): string {
        return value + true + null + undefined + 1.5;
    }

    fromField(holder: StringHolder): string {
        if (holder.value.length === 0) return holder.value + "!";

        return holder.value + "!";
    }

    fromEmptyLiteralField(holder: EmptyLiteralStringHolder): string {
        return holder.value + "!";
    }

    fromNonemptyLiteralField(holder: NonemptyLiteralStringHolder): string {
        return holder.value + "!";
    }
}
