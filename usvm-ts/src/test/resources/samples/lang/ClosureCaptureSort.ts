class CapturedValue {
    value: number = 42;
}

class ClosureCaptureSort {
    private active = 0;

    numberFromInputField(): number {
        const captured = this.active;
        const read = () => captured;

        return read();
    }

    numberFromAny(): number {
        return this.captureNumber(42);
    }

    booleanFromAny(): number {
        return this.captureBoolean(true);
    }

    referenceFromAny(): number {
        return this.captureReference(new CapturedValue());
    }

    private captureNumber(value: any): number {
        const captured: number = value;
        const read = () => captured;

        return read();
    }

    private captureBoolean(value: any): number {
        const captured: boolean = value;
        const read = () => captured;

        return read() ? 1 : 0;
    }

    private captureReference(value: any): number {
        const captured: CapturedValue = value;
        const read = () => captured.value;

        return read();
    }
}
