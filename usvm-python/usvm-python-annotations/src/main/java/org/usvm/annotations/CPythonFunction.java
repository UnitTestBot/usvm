package org.usvm.annotations;

import org.usvm.annotations.codegeneration.CType;
import org.usvm.annotations.codegeneration.ObjectConverter;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.SOURCE)
public @interface CPythonFunction {
    CType[] argCTypes();
    ObjectConverter[] argConverters();
    boolean addToSymbolicAdapter() default true;
}
