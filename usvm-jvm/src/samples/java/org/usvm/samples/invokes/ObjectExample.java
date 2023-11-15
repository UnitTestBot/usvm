package org.usvm.samples.invokes;

public class ObjectExample {
    public String objectToStringVirtualInvokeExample(Object x) {
        if (x == null) {
            return null;
        }

        return x.toString();
    }
}
