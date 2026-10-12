class BuiltinProjectClass {}

class RuntimeBuiltinInstanceof {
    constructorObject(): number { return BuiltinProjectClass instanceof Object ? 1 : 0; }
    constructorFunction(): number { return BuiltinProjectClass instanceof Function ? 1 : 0; }
    constructorArray(): number { return BuiltinProjectClass instanceof Array ? 1 : 0; }
    sdkConstructorFunction(): number { return Array instanceof Function ? 1 : 0; }
    sdkConstructorObject(): number { return Function instanceof Object ? 1 : 0; }
    sdkConstructorArray(): number { return Object instanceof Array ? 1 : 0; }
    functionObject(): number { const fn = () => 1; return fn instanceof Object ? 1 : 0; }
    functionFunction(): number { const fn = () => 1; return fn instanceof Function ? 1 : 0; }
    functionProjectClass(): number { const fn = () => 1; return fn instanceof BuiltinProjectClass ? 1 : 0; }
    instanceObject(): number { return new BuiltinProjectClass() instanceof Object ? 1 : 0; }
    instanceFunction(): number { return new BuiltinProjectClass() instanceof Function ? 1 : 0; }
    arrayObject(): number { return [] instanceof Object ? 1 : 0; }
    arrayArray(): number { return [] instanceof Array ? 1 : 0; }
    primitiveObject(): number { return 42 instanceof Object ? 1 : 0; }
    nullObject(): number { return null instanceof Object ? 1 : 0; }
    stringObject(): number { return "text" instanceof Object ? 1 : 0; }

    conditionalBuiltin(useFunction: boolean): number {
        const ctor = useFunction ? Function : Object;
        return BuiltinProjectClass instanceof ctor ? 1 : 0;
    }

    localShadow(): number {
        const Object = BuiltinProjectClass;
        return BuiltinProjectClass instanceof Object ? 1 : 0;
    }

    parameterShadow(Object: any): number {
        return BuiltinProjectClass instanceof Object ? 1 : 0;
    }

    callParameterShadow(): number {
        return this.parameterShadow(BuiltinProjectClass);
    }
}
