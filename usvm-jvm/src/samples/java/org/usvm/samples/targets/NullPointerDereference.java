package org.usvm.samples.targets;

class ClassWithValue {
    public int value;
}

public class NullPointerDereference {

    public static int twoPathsToNPE(ClassWithValue obj, int n) {
        int m = 0;

        if (n > 100) {
            m += n;
        } else {
            m -= n;
        }

        return obj.value + m;
    }
}
