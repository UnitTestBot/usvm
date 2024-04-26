package org.usvm.samples.approximations

import org.jacodb.api.jvm.JcClassOrInterface
import org.usvm.api.decoder.DecoderApi
import org.usvm.api.decoder.DecoderFor
import org.usvm.api.decoder.ObjectData
import org.usvm.api.decoder.ObjectDecoder

@DecoderFor(TestMap::class)
class TestMapDecoder : ObjectDecoder {
    override fun <T> createInstance(
        type: JcClassOrInterface,
        objectData: ObjectData<T>,
        decoder: DecoderApi<T>
    ): T {
        val ctor = type.declaredMethods.single { it.isConstructor }
        return decoder.invokeMethod(ctor, emptyList())
    }

    override fun <T> initializeInstance(
        type: JcClassOrInterface,
        objectData: ObjectData<T>,
        instance: T,
        decoder: DecoderApi<T>
    ) {
        val putEntry = type.declaredMethods.single { it.name == "put" }
        val approximatedStorage = type.declaredFields.single { it.name == "storage" }

        val storage = objectData.decodeSymbolicMapField(approximatedStorage) ?: return

        while (storage.size() > 0) {
            val key = storage.anyKey()
            val value = storage.get(key)
            decoder.invokeMethod(putEntry, listOf(instance, key, value))
            storage.remove(key)
        }
    }
}
