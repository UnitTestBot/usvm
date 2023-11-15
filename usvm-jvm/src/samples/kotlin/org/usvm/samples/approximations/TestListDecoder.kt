package org.usvm.samples.approximations

import org.jacodb.api.JcClassOrInterface
import org.usvm.api.decoder.DecoderApi
import org.usvm.api.decoder.DecoderFor
import org.usvm.api.decoder.ObjectDecoder

@DecoderFor(TestList::class)
class TestListDecoder : ObjectDecoder {
    override fun <T> decode(decoder: DecoderApi<T>, cls: JcClassOrInterface): T {
        val ctor = cls.declaredMethods.single { it.isConstructor }
        val addElement = cls.declaredMethods.single { it.name == "add" }
        val approximatedStorage = cls.declaredFields.single { it.name == "storage" }

        val result = decoder.invokeMethod(ctor, emptyList())

        val storage = decoder.decodeSymbolicListField(approximatedStorage)
            ?: return result

        for (i in 0 until storage.size()) {
            val element = storage.get(i)
            decoder.invokeMethod(addElement, listOf(result, element))
        }

        return result
    }
}
