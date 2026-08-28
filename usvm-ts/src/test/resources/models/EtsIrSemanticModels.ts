export class EtsIrSemanticModels {
    static absolute(value: number): number {
        if (value < 0) {
            return -value;
        }

        return value;
    }

    static increment(receiver: number[], delta: number): number[] {
        receiver[0] = receiver[0] + delta;
        return receiver;
    }

    static fail(value: number): number {
        throw value;
    }

    static positiveIdentity(value: number): number {
        return value;
    }

    static outer(value: number): number {
        return ExternalModels.double(value);
    }

    static double(value: number): number {
        return value * 2;
    }

    static recurse(value: number): number {
        return ExternalModels.recursive(value);
    }
}

declare class ExternalModels {
    static double(value: number): number;
    static recursive(value: number): number;
}
