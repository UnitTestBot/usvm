// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

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

    handleNumericEdges(): number {
        const obj = new SimpleClass();
        obj.x = NaN;
        if (Number.isNaN(obj.x)) {
            obj.x = Infinity;
        }
        return (obj.x === Infinity) ? 1 : 0;
    }

    createWithField(): number {
        return { a: 15 }.a;
    }

    factoryCreatedObject(): number {
        return this.createObject().x;
    }

    conditionalFieldAccess(a: SimpleClass): number {
        if (a.x === 1.1) return 1;
        return 2;
    }

    nestedFieldAccess(): number {
        const obj = { inner: new SimpleClass() };
        obj.inner.x = 7;
        return obj.inner.x;
    }

    arrayFieldAccess(): number {
        const obj = { arr: [7, 3, 6] };
        obj.arr[1] = 5;
        return obj.arr[1];
    }

    multipleFieldInteraction(): number {
        const obj = new MultiFieldClass();
        obj.a = obj.b * 2;
        obj.b = obj.a + 1;
        return obj.a + obj.b;
    }

    circularTypeChanges(): number {
        const obj = new SimpleClass();
        obj.x = { value: 5 };
        obj.x = obj.x.value;
        obj.x = [obj.x];
        return obj.x.length;
    }

    private createObject(): { x: number } {
        return { x: 42 };
    }

    readFromAny(x: any): number {
        if (x === undefined) return 0;
        if (x === null) return 0;
        if (x.a === 10) return 1;
        return 2;
    }

    writeToAny(x: any): number {
        if (x === undefined) return 0;
        if (x === null) return 0;
        x.a = 20;
        x.b = 30;
        return x.a + x.b;
    }
}

class SimpleClass {
    x: any = 5;
}

class MultiFieldClass {
    a = 1;
    b = 2;
}
