let environmentCounter = 17;
let environmentBox = { value: 23 };

class InitialEnvironmentBox {
    value: number = 31;
}

class InitialEnvironment {
    static counter: number = 11;
    static box: InitialEnvironmentBox = new InitialEnvironmentBox();
    static items: number[] = [13, 19];
    static aliasBox: InitialEnvironmentBox = InitialEnvironment.box;
    static aliasItems: number[] = InitialEnvironment.items;

    mutate(): number {
        const result = InitialEnvironment.counter + InitialEnvironment.box.value + InitialEnvironment.items[0];
        InitialEnvironment.counter = 91;
        InitialEnvironment.box.value = 92;
        InitialEnvironment.items[0] = 93;
        return result;
    }

    moduleData(): number {
        const result = environmentCounter + environmentBox.value;
        environmentCounter = 94;
        environmentBox.value = 95;
        return result;
    }

    aliases(): number {
        return InitialEnvironment.box === InitialEnvironment.aliasBox &&
            InitialEnvironment.items === InitialEnvironment.aliasItems ? 1 : 0;
    }

    lazy(): number {
        return InitialEnvironment.counter;
    }
}

class EarlierEnvironment {
    static box: InitialEnvironmentBox = new InitialEnvironmentBox();
}

class LaterEnvironment {
    static result: number = LaterEnvironment.initialize();

    static initialize(): number {
        EarlierEnvironment.box.value = 41;
        return 1;
    }

    run(): number {
        return LaterEnvironment.result + EarlierEnvironment.box.value;
    }
}

class LateEnvironment {
    static box: InitialEnvironmentBox = EarlierEnvironment.box;
}

class InterleavedEnvironment {
    run(): number {
        const alias = EarlierEnvironment.box;
        alias.value = 51;
        return LateEnvironment.box.value;
    }
}

class SparseEnvironment {
    static items: number[] = new Array<number>(2);

    run(): number {
        return SparseEnvironment.items[0] === undefined ? 1 : 2;
    }
}

declare class InitialEnvironmentExternal {
    static operation(): number;
}

class PendingInitialEnvironment {
    static counter: number = InitialEnvironmentExternal.operation();

    run(): number {
        return PendingInitialEnvironment.counter;
    }
}
