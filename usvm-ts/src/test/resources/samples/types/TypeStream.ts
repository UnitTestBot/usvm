// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

class TypeStream {
    instanceOf(ancestor: Parent): number {
        if (ancestor instanceof FirstChild) {
            return 1;
        } else if (ancestor instanceof SecondChild) {
            return 2;
        } else if (ancestor instanceof Parent) {
            return 3;
        }
        return -1; // unreachable
    }

    virtualInvokeOnAncestor(ancestor: Parent): number {
        const virtualInvokeResult = ancestor.virtualMethod();
        if (virtualInvokeResult == 100) {
            return 1;
        } else if (virtualInvokeResult == 200) {
            return 2;
        } else if (virtualInvokeResult == 300) {
            return 3;
        }
        return -1; // unreachable
    }

    useUniqueField(value: any): number {
        // noinspection JSUnusedLocalSymbols
        const _ = value.firstChildField;
        const virtualInvokeResult = value.virtualMethod();
        if (virtualInvokeResult == 100) {
            return 1;
        } else if (virtualInvokeResult == 200) {
            return 2; // unreachable
        } else if (virtualInvokeResult == 300) {
            return 3; // unreachable
        }
        return -1; // unreachable
    }

    useNonUniqueField(value: any): number {
        // noinspection JSUnusedLocalSymbols
        const _ = value.parentField;
        const virtualInvokeResult = value.virtualMethod();
        if (virtualInvokeResult == 100) {
            return 1;
        } else if (virtualInvokeResult == 200) {
            return 2;
        } else if (virtualInvokeResult == 300) {
            return 3;
        }
        return -1; // unreachable
    }
}

class Parent {
    parentField: number = 42;

    virtualMethod(): number {
        return 300;
    }
}

class FirstChild extends Parent {
    firstChildField: number = 10;

    override virtualMethod(): number {
        return 100;
    }
}

class SecondChild extends Parent {
    secondChildField: number = 20;

    override virtualMethod(): number {
        return 200;
    }
}
