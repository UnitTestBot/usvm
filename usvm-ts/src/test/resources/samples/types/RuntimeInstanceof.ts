// @ts-nocheck

class RuntimeInstanceof {
    dynamicConstructor(useA: boolean): number {
        const ctor = useA ? InstanceA : InstanceB;
        return new InstanceA() instanceof ctor ? 1 : 0;
    }

    constructorValue(): typeof InstanceA {
        return InstanceA;
    }

    returnedConstructor(): number {
        const ctor = this.constructorValue();
        return new InstanceA() instanceof ctor && typeof ctor === "function" ? 1 : 0;
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

    booleanRight(): boolean {
        const ctor: any = true;
        return new InstanceA() instanceof ctor;
    }

    stringRight(): boolean {
        const ctor: any = "not a constructor";
        return new InstanceA() instanceof ctor;
    }

    nullRight(): boolean {
        const ctor: any = null;
        return new InstanceA() instanceof ctor;
    }

    undefinedRight(): boolean {
        const ctor: any = undefined;
        return new InstanceA() instanceof ctor;
    }

    objectRight(): boolean {
        const ctor: any = {};
        return new InstanceA() instanceof ctor;
    }

    classInstanceRight(): boolean {
        const ctor: any = new InstanceB();
        return new InstanceA() instanceof ctor;
    }

    unresolvedRight(ctor: any): boolean {
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

    constructorUnionParameter(ctor: typeof InstanceA | typeof InstanceB): boolean {
        return typeof ctor === "function";
    }

    nullableConstructorParameter(ctor: typeof InstanceA | null): boolean {
        return ctor === null || typeof ctor === "function";
    }

    constructorAliasParameter(ctor: InstanceConstructorAlias): boolean {
        return typeof ctor === "function";
    }

    constructorIntersectionParameter(ctor: typeof InstanceA & { marker?: number }): boolean {
        return typeof ctor === "function";
    }

    genericConstructorParameter<T extends typeof InstanceA>(ctor: T): boolean {
        return typeof ctor === "function";
    }

    constructorTupleParameter(ctors: [typeof InstanceA]): boolean {
        return new InstanceA() instanceof ctors[0];
    }

    constructorArrayParameter(ctors: (typeof InstanceA)[]): boolean {
        return ctors.length === 1 && new InstanceA() instanceof ctors[0];
    }

    constructorNestedArrayParameter(ctors: (typeof InstanceA)[][]): boolean {
        return new InstanceA() instanceof ctors[0][0];
    }

    constructorBoxParameter(box: InstanceConstructorBox<typeof InstanceA>): boolean {
        return typeof box.value === "function";
    }

    structuralConstructorParameter(holder: { ctor: typeof InstanceA }): boolean {
        return typeof holder.ctor === "function";
    }

    recursiveConstructorParameter(holder: InstanceRecursiveHolder): boolean {
        return typeof holder.ctor === "function";
    }

    inheritedConstructorParameter(holder: InstanceInheritedHolder): boolean {
        return typeof holder.ctor === "function";
    }

    inheritedGenericConstructorParameter(holder: InstanceInheritedGenericConstructor): boolean {
        return typeof holder.ctor === "function";
    }

    inheritedGenericNumberParameter(holder: InstanceInheritedGenericNumber): boolean {
        return typeof holder.ctor === "number";
    }

    inheritedConcreteNumberParameter(holder: InstanceInheritedConcreteNumber): boolean {
        return holder.value === 1;
    }
}

type InstanceConstructorAlias = typeof InstanceA | typeof InstanceB;

class InstanceA {}
class InstanceB {}
class InstanceConstructorBox<T> { value!: T; }
class InstanceRecursiveHolder {
    next?: InstanceRecursiveHolder;
    ctor!: typeof InstanceA;
}
class InstanceConstructorBase { ctor!: typeof InstanceA; }
class InstanceInheritedHolder extends InstanceConstructorBase {}
class InstanceGenericConstructorBase<T> { ctor!: T; }
class InstanceInheritedGenericConstructor extends InstanceGenericConstructorBase<typeof InstanceA> {}
class InstanceInheritedGenericNumber extends InstanceGenericConstructorBase<number> {}
class InstanceConcreteNumberBase { value!: number; }
class InstanceInheritedConcreteNumber extends InstanceConcreteNumberBase {}
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
