package org.usvm.annotations;

import org.usvm.annotations.ids.SymbolicMethodId;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.SOURCE)
public @interface SymbolicMethod {
    SymbolicMethodId id();
}
