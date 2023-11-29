package org.usvm.util

import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.JcClassType
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcRefType
import org.jacodb.api.JcType
import org.jacodb.api.JcTypedField
import org.jacodb.api.TypeName
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.ext.findFieldOrNull
import org.jacodb.api.ext.findTypeOrNull
import org.jacodb.api.ext.toType
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.machine.JcContext
import org.usvm.machine.JcTransparentInstruction
import org.usvm.memory.ULValue
import org.usvm.memory.UWritableMemory
import org.usvm.uctx
import kotlin.reflect.KClass

fun JcContext.extractJcType(clazz: KClass<*>): JcType = cp.findTypeOrNull(clazz.qualifiedName!!)!!

fun JcContext.extractJcRefType(clazz: KClass<*>): JcRefType = extractJcType(clazz) as JcRefType

val JcClassOrInterface.enumValuesField: JcTypedField
    get() = toType().findFieldOrNull("\$VALUES") ?: error("No \$VALUES field found for the enum type $this")

@Suppress("UNCHECKED_CAST")
fun UWritableMemory<*>.write(ref: ULValue<*, *>, value: UExpr<*>) {
    write(ref as ULValue<*, USort>, value as UExpr<USort>, value.uctx.trueExpr)
}

internal fun UWritableMemory<JcType>.allocHeapRef(type: JcType, useStaticAddress: Boolean): UConcreteHeapRef =
    if (useStaticAddress) allocStatic(type) else allocConcrete(type)

tailrec fun JcInst.originalInst(): JcInst = if (this is JcTransparentInstruction) originalInst.originalInst() else this

fun TypeName.toType(cp: JcClasspath): JcType? = cp.findTypeOrNull(this)

val JcRefType.name: String
    get() = jcClass.name

val JcClassType.outerClassField: JcTypedField?
    get() = fields.singleOrNull { it.name == "this\$0" }
