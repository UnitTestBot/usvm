class FieldContainerToInfer {
    defaultA: number = 13;
    uniqueA: number = 12;

    notUniqueFunction(): number {
        return 21
    }
}

class MethodsContainerToInfer {
    uniqueFunction() {
        console.log("Hi")
    }

    notUniqueFunction(): number {
        return 42
    }
}

class A {
    defaultA: number = 23
}

function useNonUniqueField(x : A) {
    return x.defaultA
}

function useUniqueFields(x : FieldContainerToInfer) {
    return x.uniqueA
}

function useBothA(x : FieldContainerToInfer): number {
    return x.uniqueA + x.defaultA
}

function useUniqueMethods(x : MethodsContainerToInfer) {
    x.uniqueFunction()
}

function useNotUniqueMethod(x : MethodsContainerToInfer) {
    x.notUniqueFunction()
}

function useFunctionAndField(x : FieldContainerToInfer): number {
    x.notUniqueFunction()
    return x.defaultA
}


// TODO examples with another scope of view and anonymous properties