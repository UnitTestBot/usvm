// @ts-nocheck

class RuntimeInstanceof {
    dynamicConstructor(useA: boolean): number {
        const ctor = useA ? InstanceA : InstanceB;
        return new InstanceA() instanceof ctor ? 1 : 0;
    }

    directConstructor(): number {
        return new InstanceA() instanceof InstanceA ? 1 : 0;
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

    nonCallableRight(): boolean {
        const ctor: any = 42;
        return new InstanceA() instanceof ctor;
    }

    customHasInstance(): boolean {
        return new InstanceCustom() instanceof InstanceCustom;
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
