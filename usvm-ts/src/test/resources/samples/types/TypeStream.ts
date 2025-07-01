// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

class TypeStream {
    ancestorId(ancestor: Parent): Parent {
        return ancestor;
    }

    virtualInvokeForAncestor(ancestor: Parent): number {
        const virtualInvokeResult = ancestor.virtualMethod();
        if (virtualInvokeResult == 100) {
            return 1;
        } else if (virtualInvokeResult == 200) {
            return 2;
        } else {
            return 3;
        }
    }

    useUniqueField(value: any): number {
        // noinspection JSUnusedLocalSymbols
        const _ = value.firstChildField;
        const virtualInvokeResult = value.virtualMethod();
        if (virtualInvokeResult == 200) {
            return 1;
        } else if (virtualInvokeResult == 100) {
            return -1; // unreachable
        } else {
            return -1; // unreachable
        }
    }

    useNonUniqueField(value: any): number {
        // noinspection JSUnusedLocalSymbols
        const _ = value.parentField;
        const virtualInvokeResult = value.virtualMethod();
        if (virtualInvokeResult == 100) {
            return 1;
        } else if (virtualInvokeResult == 200) {
            return 2;
        } else {
            return 3;
        }
    }
}

class Parent {
    parentField: number = -10;

    virtualMethod(): number {
        return 100;
    }
}

class FirstChild extends Parent {
    firstChildField: number = 10;

    override virtualMethod(): number {
        return 200;
    }
}

class SecondChild extends Parent {
    secondChildField: number = 20;

    override virtualMethod(): number {
        return 300;
    }
}
