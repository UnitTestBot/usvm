package org.usvm.samples.strings;

import org.usvm.api.Engine;

public class ModelStringDomain {
    public static int lengthBoundary(String value) {
        if (value == null) {
            return -1;
        }

        return value.length() == 51 ? 1 : 0;
    }

    public static int userAssumption(String value) {
        if (value == null) {
            return -1;
        }

        Engine.assumeSymbolic(value, value.length() <= 2);
        return value.length() <= 2 ? 1 : -2;
    }

    public static int characterBeyondBoundary(String value) {
        if (value == null) {
            return -1;
        }
        if (value.length() != 51) {
            return 0;
        }

        return value.charAt(50) == 'A' ? 1 : 2;
    }
}
