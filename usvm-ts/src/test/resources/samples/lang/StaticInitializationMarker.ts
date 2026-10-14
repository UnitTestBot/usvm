// noinspection JSUnusedGlobalSymbols

class StaticInitializationCollision {
    static __initialized__: boolean = false;
    static value: number = 41;

    static readAndWrite(): number {
        const initiallyFalse = StaticInitializationCollision.__initialized__ === false;
        StaticInitializationCollision.__initialized__ = true;
        const writtenTrue = StaticInitializationCollision.__initialized__ === true;

        if (initiallyFalse && writtenTrue) {
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
