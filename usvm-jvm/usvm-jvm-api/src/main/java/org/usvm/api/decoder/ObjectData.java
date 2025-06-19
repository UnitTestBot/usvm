package org.usvm.api.decoder;

import org.jacodb.api.jvm.JcField;
import org.usvm.api.SymbolicIdentityMap;
import org.usvm.api.SymbolicList;
import org.usvm.api.SymbolicMap;

public interface ObjectData<T> {

    T decodeField(final JcField field);

    // Nullable
    SymbolicList<T> decodeSymbolicListField(final JcField field);

    // Nullable
    SymbolicMap<T, T> decodeSymbolicMapField(final JcField field);

    // Nullable
    SymbolicIdentityMap<T, T> decodeSymbolicIdentityMapField(final JcField field);

    // Nullable
    ObjectData<T> getObjectField(final JcField field);

    boolean getBooleanField(final JcField field);

    byte getByteField(final JcField field);

    short getShortField(final JcField field);

    int getIntField(final JcField field);

    long getLongField(final JcField field);

    float getFloatField(final JcField field);

    double getDoubleField(final JcField field);

    char getCharField(final JcField field);

    int getArrayFieldLength(final JcField field);
}
