package org.usvm.samples.approximations

import org.jacodb.api.jvm.JcClassOrInterface
import org.usvm.api.decoder.DecoderApi
import org.usvm.api.decoder.DecoderFor
import org.usvm.api.decoder.ObjectData
import org.usvm.api.decoder.ObjectDecoder

@DecoderFor(TestList::class)
class TestListDecoder : ObjectDecoder {
    override fun <T> createInstance(
        type: JcClassOrInterface,
        objectData: ObjectData<T>,
        decoder: DecoderApi<T>,
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
        val addElement = type.declaredMethods.single { it.name == "add" }
        val approximatedStorage = type.declaredFields.single { it.name == "storage" }

        val storage = objectData.decodeSymbolicListField(approximatedStorage) ?: return

        for (i in 0 until storage.size()) {
            val element = storage.get(i)
            decoder.invokeMethod(addElement, listOf(instance, element))
        }
    }
}
