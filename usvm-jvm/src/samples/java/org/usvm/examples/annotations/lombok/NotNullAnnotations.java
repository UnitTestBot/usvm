package org.usvm.examples.annotations.lombok;

import lombok.NonNull;

public class NotNullAnnotations {

    public int lombokNonNull(@NonNull Integer value) {
        return value;
    }
}
