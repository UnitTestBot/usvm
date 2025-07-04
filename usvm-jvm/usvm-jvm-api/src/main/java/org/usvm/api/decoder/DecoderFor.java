package org.usvm.api.decoder;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

@Target({ElementType.TYPE})
public @interface DecoderFor {
    Class<?> value();
}
