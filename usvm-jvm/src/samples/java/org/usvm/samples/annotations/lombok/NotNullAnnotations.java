package org.usvm.samples.annotations.lombok;

import lombok.NonNull;

public class NotNullAnnotations {

    public int lombokNonNull(@NonNull Integer value) {
        return value;
    }
}
