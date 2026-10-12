class AnalysisFixture {
    unknownProperty(): boolean {
        return External.flag();
    }

    counterexampleBeforeUnknown(input: boolean): boolean {
        if (input) return false;

        return External.flag();
    }

    assignmentBeforeReturn(input: number): number {
        const marker = input + 1;
        return marker + 2;
    }

    assignmentBeforeLoop(input: number): number {
        const marker = input + 1;
        while (true) {}
    }

    counterexampleBeforeCyclicObject(input: boolean): any {
        if (input) return false;

        const value = new LinkedNode();
        value.next = value;
        return value;
    }

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

declare class External {
    static flag(): boolean;
}

class LinkedNode {
    next: LinkedNode;
}
