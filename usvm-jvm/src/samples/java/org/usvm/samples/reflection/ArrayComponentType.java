package org.usvm.samples.reflection;

import java.lang.reflect.Array;

public class ArrayComponentType {
    public Class<?> primitiveArray() {
        return boolean[].class.getComponentType();
    }

    public Class<?> referenceArray() {
        return Object[].class.getComponentType();
    }

    public Class<?> nestedArray() {
        return int[][].class.getComponentType();
    }

    public Class<?> referenceClass() {
        return ArrayComponentType.class.getComponentType();
    }

    public Class<?> primitiveClass() {
        return int.class.getComponentType();
    }

    public Class<?> voidClass() {
        return void.class.getComponentType();
    }

    public Class<?> interfaceClass() {
        return Runnable.class.getComponentType();
    }

    public boolean[] growBooleanArray() {
        boolean[] original = {true, false};
        Class<?> componentType = original.getClass().getComponentType();
        boolean[] grown = (boolean[]) Array.newInstance(componentType, original.length + 1);
        System.arraycopy(original, 0, grown, 0, original.length);
        return grown;
    }
}
