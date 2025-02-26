// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

class SimpleClass {
    x: any = 5;
}

class FieldAccess {
    readDefaultField(): number {
        const obj = new SimpleClass();
        return obj.x;
    }

    writeAndReadNumeric(): number {
        const obj = new SimpleClass();
        obj.x = 14;
        return obj.x;
    }

    writeDifferentTypes(): number {
        const obj = new SimpleClass();
        obj.x = true;
        if (obj.x === true) {
            obj.x = undefined;
        }
        if (obj.x === undefined) {
            obj.x = null;
        }
        if (obj.x === null) {
            obj.x = 5;
        }
        return obj.x;
    }

    createWithField(): number {
        return {a: 15}.a;
    }

    conditionalFieldAccess(a: SimpleClass): number {
        if (a.x === 1.1) return 14;
        return 10;
    }

    nestedFieldAccess(): number {
        const obj = {inner: new SimpleClass()};
        obj.inner.x = 7;
        return obj.inner.x;
    }

    arrayFieldAccess(): number {
        const obj = {arr: [7, 3, 6]};
        obj.arr[1] = 5;
        return obj.arr[1];
    }
}
