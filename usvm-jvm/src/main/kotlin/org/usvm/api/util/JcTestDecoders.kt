package org.usvm.api.util

import kotlinx.coroutines.runBlocking
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.ext.annotation
import org.jacodb.impl.features.hierarchyExt
import org.usvm.api.decoder.DecoderFor
import org.usvm.api.decoder.ObjectDecoder

class JcTestDecoders(private val cp: JcClasspath) {
    private val decoders by lazy { loadDecoders() }

    fun findDecoder(cls: JcClassOrInterface): ObjectDecoder? = decoders[cls]

    private fun loadDecoders(): Map<JcClassOrInterface, ObjectDecoder> {
        val objectDecoder = cp.findClassOrNull(ObjectDecoder::class.java.name)
            ?: return emptyMap()

        return runBlocking {
            cp.hierarchyExt()
                .findSubClasses(objectDecoder, entireHierarchy = true, includeOwn = false)
                .mapNotNull { loadDecoder(it) }
                .toMap()
        }
    }

    private fun loadDecoder(decoder: JcClassOrInterface): Pair<JcClassOrInterface, ObjectDecoder>? {
        val target = decoder.annotation(DecoderFor::class.java.name) ?: return null
        val targetCls = target.values["value"] as? JcClassOrInterface ?: return null

        val decoderCls = JcClassLoader.loadClass(decoder)
        val decoderInstance = decoderCls.getConstructor().newInstance() as? ObjectDecoder ?: return null

        return targetCls to decoderInstance
    }
}
