package org.usvm.samples.collections;

public class CustomClass {
    int value;

    CustomClass(int value) {
        this.value = value;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof CustomClass) {
            CustomClass that = (CustomClass) o;
            return value == that.value;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return value;
    }

    @Override
    public String toString() {
        return Integer.toString(value);
    }
}