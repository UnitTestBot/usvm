package org.usvm.api.decoder;

import org.jacodb.api.JcClassOrInterface;

public interface ObjectDecoder {
    <T> T decode(final DecoderApi<T> decoder, final JcClassOrInterface cls);
}
