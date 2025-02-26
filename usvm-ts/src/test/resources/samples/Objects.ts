// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

class SimpleClass {
    x: any = 5;
}

class Example {
    createClassInstance() {
        let x = new SimpleClass();
        return x.x;
    }

    createClassInstanceAndWriteField() {
        let x = new SimpleClass();
        x.x = 14;

        return x;
    }

    createClassInstanceAndWriteValueOfAnotherType() {
        let x = new SimpleClass();
        x.x = true;

        if (x.x === true) {
            x.x = null
        }

        return x;
    }

    createAnonymousClass() {
        return {a: 15};
    }

    readFieldValue(x: SimpleClass) {
        if (x.x === 1.1) {
            return 14
        }

        return 10
    }
}
