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

    ignoredExtraArgumentIsEvaluated(): boolean {
        let evaluations = 0;

        const result = "aab".replaceAll("a", "X", (evaluations = evaluations + 1));

        return result === "XXb" && evaluations === 1;
    }

    missingReplacementIsResidual(): string {
        return "abc".replaceAll("a");
    }

    numericSearchIsResidual(): string {
        return "123".replaceAll(1, "x");
    }

    numericReplacementIsResidual(): string {
        return "abc".replaceAll("a", 1);
    }

    callbackReplacementIsResidual(): string {
        return "aa".replaceAll("a", (match: string) => match);
    }

    callableSearchIsResidual(): string {
        return "aa".replaceAll((match: string) => match, "x");
    }
}
