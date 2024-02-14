package org.usvm.fuzzer.test;


import org.usvm.fuzzer.test.data.Recursive;
import org.usvm.fuzzer.test.data.UserDefinedClass;

@SuppressWarnings({"unused", "RedundantIfStatement"})
public class Objects {

    public int test(UserDefinedClass userDefinedClass) {
        if (userDefinedClass.getX() > 5 && userDefinedClass.getY() < 10) {
            return 1;
        } else if (userDefinedClass.getZ() < 20) {
            return 2;
        } else {
            return 3;
        }
    }

    public boolean testMe(Recursive r) {
        if (r.val() > 10) {
            return true;
        }
        return false;
    }

    private int data;

    public static void foo(Objects a, Objects b) {
        a.data = 1;
        b.data = 2;
        //noinspection ConstantValue
        if (a.data == b.data) {
            throw new IllegalArgumentException();
        }
    }
}
