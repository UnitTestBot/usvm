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

class NamedBuiltinStatics {
    static Number(value: number): number { return value + 40; }
    static Boolean(value: number): number { return value + 50; }
    static $r(value: number): number { return value + 60; }
}

namespace SdkNamespaceCollision {
    export function missingNamespaceCall(): number { return 135; }
}

export function missingModuleCall(): number { return 137; }

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

    callStaticNamedNumber(): number {
        return NamedBuiltinStatics.Number(2);
    }

    callStaticNamedBoolean(): number {
        return NamedBuiltinStatics.Boolean(3);
    }

    callStaticNamedResourceLookup(): number {
        return NamedBuiltinStatics.$r(4);
    }
}
