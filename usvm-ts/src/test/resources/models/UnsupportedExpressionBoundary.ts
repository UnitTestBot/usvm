export function regularExpressionLiteral(value: string): string {
    const whitespace = /\s+/g;
    return value.replaceAll(whitespace, "-");
}

export function caughtExceptionValue(): unknown {
    try {
        return 1;
    } catch (error) {
        return error;
    }
}
