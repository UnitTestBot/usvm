package org.usvm.api.internal;

public class StringConcatUtil {
    static String concat(final Object[] data) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < data.length; i++) {
            sb.append(data[i]);
        }
        return sb.toString();
    }
}
