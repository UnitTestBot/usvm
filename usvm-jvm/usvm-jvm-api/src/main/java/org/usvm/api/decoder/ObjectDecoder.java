package org.usvm.api.decoder;

import org.jacodb.api.jvm.JcClassOrInterface;

public interface ObjectDecoder {
    <T> T createInstance(
            final JcClassOrInterface type,
            final ObjectData<T> objectData,
            final DecoderApi<T> decoder
    );

    <T> void initializeInstance(
            final JcClassOrInterface type,
            final ObjectData<T> objectData,
            final T instance,
            final DecoderApi<T> decoder
    );
}
