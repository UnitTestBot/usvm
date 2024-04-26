package org.usvm.api.decoder;

import org.jacodb.api.jvm.JcClassOrInterface;
import org.jacodb.api.jvm.JcField;
import org.jacodb.api.jvm.JcMethod;
import org.jacodb.api.jvm.JcType;

import java.util.List;

public interface DecoderApi<T> {
    T invokeMethod(final JcMethod method, final List<T> args);

    T getField(final JcField field, final T instance);

    void setField(final JcField field, final T instance, final T value);

    T createBoolConst(boolean value);

    T createByteConst(byte value);

    T createShortConst(short value);

    T createIntConst(int value);

    T createLongConst(long value);

    T createFloatConst(float value);

    T createDoubleConst(double value);

    T createCharConst(char value);

    T createStringConst(final String value);

    T createClassConst(final JcType type);

    T createNullConst(final JcType type);

    T castClass(final JcClassOrInterface type, final T obj);

    T createArray(final JcType elementType, final T size);

    T getArrayIndex(final T array, final T index);

    T getArrayLength(final T array);

    void setArrayIndex(final T array, final T index, final T value);
}
