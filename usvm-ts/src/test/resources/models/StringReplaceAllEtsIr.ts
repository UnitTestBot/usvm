// @ts-nocheck
export class StringReplaceAllEtsIr {
    literalMatches(): string {
        return "aaaa/aba".replaceAll("aa", "X");
    }

    emptySearch(): string {
        return "A😀".replaceAll("", "-");
    }

    substitutions(): string {
        return "aba".replaceAll("a", "$$:$&:$`:$':$1");
    }

    noMatches(): string {
        return "abc".replaceAll("x", "$&");
    }

    emptyReceiver(): string {
        return "".replaceAll("", "$$");
    }

    replacementIsNotSearchedAgain(): string {
        return "aa".replaceAll("a", "aa");
    }

    numericSearchIsResidual(): string {
        return "123".replaceAll(1, "x");
    }

    numericReplacementIsResidual(): string {
        return "abc".replaceAll("a", 1);
    }
}
