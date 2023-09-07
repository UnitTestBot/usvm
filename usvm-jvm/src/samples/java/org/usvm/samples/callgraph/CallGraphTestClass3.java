package org.usvm.samples.callgraph;

public class CallGraphTestClass3 {

    public int C(CallGraphTestClass1 callGraphTestClass1) {
        return callGraphTestClass1.A();
    }

    public int D(CallGraphTestInterface callGraphTestInterface) {
        return callGraphTestInterface.A();
    }

    public int E(CallGraphTestClass1 callGraphTestClass1) {
        return callGraphTestClass1.B();
    }
}
