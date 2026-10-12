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

    nonterminatingProperty(): boolean {
        while (true) {}
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

class CyclicWitnessFixture {
    value: any[];

    cyclicProperty(): boolean {
        const value: any[] = [null];
        value[0] = value;
        this.value = value;

        return false;
    }
}
