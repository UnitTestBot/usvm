// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

class DeleteOperator {
    testDeleteProperty(): number {
        let obj = { x: 42, y: 100 };
        let res = delete obj.x;

        if (res === true && obj.x === undefined && obj.y === 100) return 1;
        return 0;
    }

    testDeleteNonExistentProperty(): number {
        let obj = { x: 42 };
        let res = delete obj.foo; // property 'foo' doesn't exist, delete returns true

        if (res === true && obj.x === 42) return 1;
        return 0;
    }

    testDeleteLocalVariable(): number {
        let x = 42;
        let res = delete x;

        if (res === false && x === 42) return 1; // welcome to JavaScript
        return 0;
    }

    testDeleteNonExistentVariable(): number {
        let res = delete nonExistentVar;

        if (res === true) return 1; // nonExistentVar is not defined, delete returns true
        return 0;
    }
}
