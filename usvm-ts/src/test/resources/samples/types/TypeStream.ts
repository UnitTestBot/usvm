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
}

class Parent {
    virtualMethod(): number {
        return 100;
    }
}

class FirstChild extends Parent {
    override virtualMethod(): number {
        return 200;
    }
}

class SecondChild extends Parent {
    override virtualMethod(): number{
        return 300;
    }
}