export class BuiltinOwnerBoundary {
    shadowedMath(): number {
        const Math = { abs: function (value: number): number { return value + 79; } };

        return Math.abs(-2);
    }

    shadowedNumber(): boolean {
        const Number = { isInteger: function (_value: number): boolean { return false; } };

        return Number.isInteger(2);
    }

    aliasedMath(): number {
        const numeric = Math;

        return numeric.abs(-2);
    }

    aliasedNumber(): boolean {
        const numeric = Number;

        return numeric.isInteger(1.5);
    }

    declaredToString(): number {
        const receiver = new DeclaredStringifier();

        return receiver.toString() === "declared" ? 1 : 0;
    }

    inheritedToString(): number {
        const receiver = new InheritedStringifier();

        return receiver.toString() === "declared" ? 1 : 0;
    }

    overriddenToString(): number {
        const receiver = new OverrideStringifier();

        return receiver.toString() === "override" ? 1 : 0;
    }

    anyReferenceToString(): number {
        const receiver: any = new DeclaredStringifier();

        return receiver.toString() === "declared" ? 1 : 0;
    }

    inheritedOrdinaryMethod(): number {
        const receiver = new InheritedStringifier();

        return receiver.baseValue();
    }

    explicitSuperCall(): number {
        return new SuperDispatchChild().value();
    }

    baseTypedVirtualCall(): number {
        const receiver: SuperDispatchBase = new SuperDispatchChild();

        return receiver.value();
    }
}

class DeclaredStringifier {
    toString(): string {
        return "declared";
    }

    baseValue(): number {
        return 7;
    }
}

class InheritedStringifier extends DeclaredStringifier {
}

class OverrideStringifier extends DeclaredStringifier {
    toString(): string {
        return "override";
    }
}

class SuperDispatchBase {
    value(): number {
        return 5;
    }
}

class SuperDispatchChild extends SuperDispatchBase {
    value(): number {
        return super.value() + 7;
    }
}
