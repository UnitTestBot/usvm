// @ts-nocheck

class RuntimeInstanceof {
    dynamicConstructor(useA: boolean): number {
        const ctor = useA ? InstanceA : InstanceB;
        return new InstanceA() instanceof ctor ? 1 : 0;
    }

    directConstructor(): number {
        return new InstanceA() instanceof InstanceA ? 1 : 0;
    }

    castConstructor(): number {
        return new InstanceA() instanceof (InstanceA as typeof InstanceA) ? 1 : 0;
    }

    nonNullConstructor(): number {
        return new InstanceA() instanceof InstanceA! ? 1 : 0;
    }

    anyConstructor(): number {
        return new InstanceA() instanceof (InstanceA as any) ? 1 : 0;
    }

    unknownConstructor(): number {
        return new InstanceA() instanceof (InstanceA as unknown as typeof InstanceA) ? 1 : 0;
    }

    objectConstructor(): number {
        return new InstanceA() instanceof (InstanceA as object as typeof InstanceA) ? 1 : 0;
    }

    inheritedConstructor(instance: InstanceChild): number {
        return instance instanceof InstanceParent ? 1 : 0;
    }

    unrelatedConstructor(): number {
        return new InstanceA() instanceof InstanceB ? 1 : 0;
    }

    classTypeof(): number {
        return typeof InstanceA === "function" ? 1 : 0;
    }

    aliasedClassTypeof(useA: boolean): number {
        const ctor = useA ? InstanceA : InstanceB;
        return typeof ctor === "function" ? 1 : 0;
    }

    primitiveLeft(): number {
        return 42 instanceof InstanceA ? 1 : 0;
    }

    anyLeft(value: any): number {
        return value instanceof InstanceA ? 1 : 0;
    }

    constructorLeft(): number {
        return InstanceA instanceof InstanceA ? 1 : 0;
    }

    constructorAliasLeft(useA: boolean): number {
        const ctor = useA ? InstanceA : InstanceB;
        return ctor instanceof InstanceA ? 1 : 0;
    }

    anyConstructorLeft(): number {
        const value: any = InstanceA;
        return value instanceof InstanceA ? 1 : 0;
    }

    undefinedLeft(): number {
        const value: any = undefined;
        return value instanceof InstanceA ? 1 : 0;
    }

    nullLeft(): number {
        const value: any = null;
        return value instanceof InstanceA ? 1 : 0;
    }

    nonCallableRight(): boolean {
        const ctor: any = 42;
        return new InstanceA() instanceof ctor;
    }

    customHasInstance(): boolean {
        return new InstanceCustom() instanceof InstanceCustom;
    }

    inheritedHasInstance(instance: InstanceHasInstanceChild): boolean {
        return instance instanceof InstanceHasInstanceChild;
    }

    constructorParameter(ctor: typeof InstanceA): boolean {
        return new InstanceA() instanceof ctor;
    }
}

class InstanceA {}
class InstanceB {}
class InstanceParent {}
class InstanceChild extends InstanceParent { childMarker: number = 1; }
class InstanceCustom {
    static [Symbol.hasInstance](_value: unknown): boolean {
        return false;
    }
}
class InstanceHasInstanceParent {
    static [Symbol.hasInstance](_value: unknown): boolean {
        return false;
    }
}
class InstanceHasInstanceChild extends InstanceHasInstanceParent { hasInstanceChildMarker: number = 1; }
