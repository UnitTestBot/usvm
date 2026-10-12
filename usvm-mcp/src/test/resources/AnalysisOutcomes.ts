function callback(): boolean {
    return false;
}

class AnalysisFixture {
    unsupportedAccessor(): boolean {
        const object = { get value(): boolean { return true; } };
        return object.value;
    }

    counterexampleBeforeUnsupported(input: boolean): boolean {
        if (input) return false;

        const object = { get value(): boolean { return true; } };
        return object.value;
    }

    callbackValue(): () => boolean {
        return callback;
    }

    supportedTrue(): boolean {
        return true;
    }

    supportedFalse(): boolean {
        return false;
    }

    crashingProperty(): boolean {
        throw "property failure";
    }
}
