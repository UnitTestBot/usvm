package org.usvm.examples.mixed;

import java.io.File;

public class SerializableExample {
    @SuppressWarnings({"unchecked", "ResultOfMethodCallIgnored"})
    public void example() {
        join("string", File.separator, System.currentTimeMillis());
    }

    @SuppressWarnings({"unchecked", "UnusedReturnValue"})
    public static <T> String join(T... elements) {
        return null;
    }
}
