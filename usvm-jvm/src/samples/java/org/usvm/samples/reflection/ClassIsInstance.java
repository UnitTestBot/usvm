package org.usvm.samples.reflection;

public class ClassIsInstance {
    public boolean inputClass(Class<?> type, Object value) {
        return type.isInstance(value);
    }

    public boolean matchingReference() {
        return String.class.isInstance("value");
    }

    public boolean unrelatedReference() {
        return String.class.isInstance(new Object());
    }

    public boolean nullObject() {
        return String.class.isInstance(null);
    }

    public boolean primitiveClass() {
        return int.class.isInstance(new Object());
    }

    public boolean voidClass() {
        return void.class.isInstance(new Object());
    }

    public boolean matchingArray() {
        return Object[].class.isInstance(new String[1]);
    }

    public boolean matchingInterface() {
        return Cloneable.class.isInstance(new int[1]);
    }
}
