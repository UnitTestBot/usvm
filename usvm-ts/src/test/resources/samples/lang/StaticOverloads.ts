// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

function overloaded(value: number): number;
function overloaded(value: string): string;
function overloaded(value: number | string): number | string {
    return typeof value === "number" ? value + 1 : value;
}

class InstanceDeclarationFirst {
    method(): number { return 7; }
    static method(): number { return 42; }
}

class StaticDeclarationFirst {
    static method(): number { return 99; }
    method(): number { return 11; }
}

class MethodKindsChild extends StaticDeclarationFirst {
    callParentMethod(): number { return super.method(); }
}

class StaticOverloads {
    callOverloaded(): number {
        return overloaded(41);
    }

    callStaticWithSameNameInstance(): number {
        return InstanceDeclarationFirst.method();
    }

    callSuperWithSameNameStatic(): number {
        return new MethodKindsChild().callParentMethod();
    }
}
