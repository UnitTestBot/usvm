package org.usvm.samples.enums;

public enum SimpleEnumExample {
    SUCCESS(10), ERROR(-10);

    final int x;

    SimpleEnumExample(int x) {
        this.x = x;
    }

    @Override
    public String toString() {
        return String.format("Enum: {name: %s, x: %d, ordinal: %d}", name(), x, ordinal());
    }
}
