package org.usvm.samples.exceptions;

import sun.misc.Unsafe;

import java.lang.reflect.Field;

public class JvmCrashExamples {
    public int exit(int i) {
        if (i == 0) {
            System.exit(-1);
            throw new RuntimeException("Exit");
        }

        return i;
    }

    public int crash(int i) throws Exception {
        Field f = Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        Unsafe unsafe = (Unsafe) f.get(null);
        unsafe.putAddress(0, 0);

        if (i == 0) {
            return i;
        }

        return 1;
    }
}
