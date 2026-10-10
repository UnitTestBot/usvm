// @ts-nocheck
const beforeDeclaration = named(2);
let moduleValue = 1;

function named(value: number): number { return value + 1; }
function replacement(value: number): number { return value + 10; }

function invokeParameter(named: () => number): number { return named(); }

function factory(seed: number): () => number {
    let captured = seed;
    return () => { captured++; return captured + moduleValue; };
}

function overloaded(value: number): number;
function overloaded(value: string): string;
function overloaded(value: number | string): number | string { return value; }

class NamedFunctionBindings {
    hoistedCall(): number {
        return beforeDeclaration;
    }

    identity(): boolean {
        const alias = named;
        return typeof named === "function" && named === named && alias === named;
    }

    aliasAfterReassignment(): number {
        const alias = named;
        named = replacement;
        return alias(2) * 10 + named(2);
    }

    selectedBeforeArguments(): number {
        const selected = named((named = replacement, 2));
        return selected * 100 + named(2);
    }

    shadows(): number {
        const before = named(2);
        { const named = () => 2; return before + named() + invokeParameter(() => 5); }
    }

    liveStateAndNestedClosure(): number {
        const alias = factory;
        moduleValue = 10;
        const callback = alias(2);
        return callback();
    }

    repeatedCapturedUpdates(): number {
        const callback = factory(2);
        moduleValue = 10;
        return callback() * 100 + callback();
    }

    unknownCapturedValue(): string {
        let captured: unknown = 2;
        const callback = () => { captured = 4; return typeof captured; };
        return callback();
    }

    unknownCapturedBoolean(): string {
        let captured: unknown = false;
        const callback = () => { captured = true; return typeof captured; };
        return callback();
    }

    unknownCapturedCallable(): string {
        let captured: unknown = named;
        const callback = () => { captured = replacement; return typeof captured; };
        return callback();
    }

    unknownCapturedChoice(flag: boolean): string {
        let captured: unknown = flag ? 2 : true;
        const callback = () => typeof captured;
        return callback();
    }

    overloadImplementation(): number {
        const alias = overloaded;
        return alias(2);
    }
}
