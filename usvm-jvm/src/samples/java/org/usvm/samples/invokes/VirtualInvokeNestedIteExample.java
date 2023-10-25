package org.usvm.samples.invokes;

interface InterfaceExample {
    int getConstant();
}

class A implements InterfaceExample {
    @Override
    public int getConstant() {
        return 1;
    }
}

class B implements InterfaceExample {
    @Override
    public int getConstant() {
        return 2;
    }
}

class C implements InterfaceExample {
    @Override
    public int getConstant() {
        return 3;
    }
}

class D implements InterfaceExample {
    @Override
    public int getConstant() {
        return 4;
    }
}

public class VirtualInvokeNestedIteExample {
    @SuppressWarnings("IfStatementWithIdenticalBranches")
    public int virtualInvokeBySymbolicIndex(int index) {
        if (index < 0 || index >= 4) {
            throw new IllegalArgumentException();
        }

        InterfaceExample[] array = new InterfaceExample[]{new A(), new B(), new C(), new D()};

        if (index == 0) {
            return array[index].getConstant();
        }

        if (index == 1) {
            return array[index].getConstant();
        }

        if (index == 2) {
            return array[index].getConstant();
        }

        return array[index].getConstant();
    }
}
