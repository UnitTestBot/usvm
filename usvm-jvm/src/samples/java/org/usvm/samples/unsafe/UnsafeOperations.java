package org.usvm.samples.unsafe;

import sun.misc.Unsafe;

import java.lang.reflect.Field;

public class UnsafeOperations {
    public int getAddressSizeOrZero() {
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            Unsafe unsafe = (Unsafe) f.get(null);
            return unsafe.addressSize();
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Reflection failed");
        }
    }
}
