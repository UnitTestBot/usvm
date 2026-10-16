// @ts-nocheck

function ordinaryFunction(): number { return 7; }
class FunctionTypeTarget {}

class RuntimeFunctionTypes {
    constructorTruthy(): number {
        const constructor = FunctionTypeTarget;
        return constructor ? 1 : 0;
    }

    namedFunctionTruthy(): number {
        const callable = ordinaryFunction;
        return callable ? 1 : 0;
    }

    closureTruthy(value: number): number {
        const callable = () => value;
        return callable ? 1 : 0;
    }

    functionInField(): number {
        const holder = { callable: ordinaryFunction };
        return holder.callable ? 1 : 0;
    }

    constructorInField(): number {
        const holder = { callable: FunctionTypeTarget };
        return holder.callable ? 1 : 0;
    }

    conditionalFunctions(useConstructor: boolean): number {
        const callable = useConstructor ? FunctionTypeTarget : ordinaryFunction;
        return callable ? 1 : 0;
    }
}
