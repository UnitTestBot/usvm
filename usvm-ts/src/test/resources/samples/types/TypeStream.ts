class TypeStream {
    ancestorId(ancestor: Parent): Parent {
        return ancestor
    }

    virtualInvokeForAncestor(ancestor: Parent): number {
        var number = ancestor.virtualMethod();
        if (number == 100) {
            return 1
        } else if (number == 200) {
            return 2
        } else {
            return 3
        }
    }

    useUniqueField(value): number {
        var number = value.firstChildField

        var virtualInvokeResult = value.virtualMethod()
        if (virtualInvokeResult == 100) {
            return -1 // unreachable
        } else if (virtualInvokeResult == 200) {
            return 1
        } else {
            return -2 // unreachable
        }
    }

    useNonUniqueField(value): number {
        var number = value.parentField
         var virtualInvokeResult = value.virtualMethod()
        if (virtualInvokeResult == 100) {
            return 1
        } else if (virtualInvokeResult == 200) {
            return 2
        } else {
            return 3
        }
    }
}

class Parent {
    virtualMethod(): number {
        return 100;
    }

    parentField: number = -10
}

class FirstChild extends Parent {
    override virtualMethod(): number {
        return 200;
    }

    firstChildField: number = 10
}

class SecondChild extends Parent {
    override virtualMethod(): number{
        return 300;
    }

    secondChildField: number = 20
}