// noinspection JSUnusedGlobalSymbols

class StaticInitializationCollision {
    static __initialized__: boolean = false;
    static __initializing__: boolean = true;
    static value: number = 41;

    static readAndWrite(): number {
        const initiallyFalse = StaticInitializationCollision.__initialized__ === false;
        StaticInitializationCollision.__initialized__ = true;
        const writtenTrue = StaticInitializationCollision.__initialized__ === true;

        const initiallyInitializing = StaticInitializationCollision.__initializing__ === true;
        StaticInitializationCollision.__initializing__ = false;
        const writtenNotInitializing = StaticInitializationCollision.__initializing__ === false;

        const initializedFieldWorks = initiallyFalse && writtenTrue;
        const initializingFieldWorks = initiallyInitializing && writtenNotInitializing;
        if (initializedFieldWorks && initializingFieldWorks) {
            return StaticInitializationCollision.value + 1;
        }

        return -1;
    }
}

class StaticInitializationControl {
    static value: number = 7;

    static read(): number {
        return StaticInitializationControl.value;
    }
}
