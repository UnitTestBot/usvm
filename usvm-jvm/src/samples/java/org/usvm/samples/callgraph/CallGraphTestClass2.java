package org.usvm.samples.callgraph;

public class CallGraphTestClass2 extends CallGraphTestClass1 {

    private CallGraphTestInterface callGraphTestInterface;

    @Override
    public int A() {
        return callGraphTestInterface.A() + 3;
    }
}
