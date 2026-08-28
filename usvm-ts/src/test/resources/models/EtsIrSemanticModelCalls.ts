// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

declare class ExternalModels {
    static absolute(value: number): number;
    static fail(value: number): number;
    static positiveIdentity(value: number): number;
    static exactPositiveIdentity(value: number): number;
    static arityMismatch(first: number, second: number): number;
    static outer(value: number): number;
    static recursive(value: number): number;
}

export class EtsIrSemanticModelCalls {
    pureArgumentAndReturn(): number {
        return ExternalModels.absolute(-42);
    }

    receiverStateArgumentAndAlias(): number {
        const receiver = [40];
        const alias = receiver.modeledIncrement(2);
        if (alias === receiver) {
            return receiver[0];
        }

        return 0;
    }

    exception(): number {
        return ExternalModels.fail(7);
    }

    unsupportedInput(): number {
        return ExternalModels.positiveIdentity(-1);
    }

    exactGuardRejectsInput(): number {
        return ExternalModels.exactPositiveIdentity(-1);
    }

    exactArityMismatch(): number {
        return ExternalModels.arityMismatch(1, 2);
    }

    exactUnresolvedArgument(): number {
        return MissingModels.unresolvedExactInput(1);
    }

    nestedUnknownCall(): number {
        return ExternalModels.outer(21);
    }

    recursiveRedirection(): number {
        return ExternalModels.recursive(1);
    }
}
