package org.usvm.machine.expr

import org.jacodb.ets.model.EtsFile
import org.usvm.UConcreteHeapRef
import org.usvm.UHeapRef
import org.usvm.machine.interpreter.getGlobals
import org.usvm.machine.state.TsState
import org.usvm.util.mkFieldLValue

private const val MATH_E = 2.718281828459045
private const val MATH_LN10 = 2.302585092994046
private const val MATH_LN2 = 0.6931471805599453
private const val MATH_LOG10E = 0.4342944819032518
private const val MATH_LOG2E = 1.4426950408889634
private const val MATH_PI = 3.141592653589793
private const val MATH_SQRT1_2 = 0.7071067811865476
private const val MATH_SQRT2 = 1.4142135623730951

private val nativeMathConstants = mapOf(
    "E" to MATH_E,
    "LN10" to MATH_LN10,
    "LN2" to MATH_LN2,
    "LOG10E" to MATH_LOG10E,
    "LOG2E" to MATH_LOG2E,
    "PI" to MATH_PI,
    "SQRT1_2" to MATH_SQRT1_2,
    "SQRT2" to MATH_SQRT2,
)

/** The implicit Math global is one ordinary SDK object, rather than a callable constructor. */
internal fun TsState.nativeMathGlobal(file: EtsFile, name: String): UConcreteHeapRef? {
    if (name != "Math") return null
    val sourceBinding = file.getGlobals().any { it.name == name }
    val importedBinding = file.importInfos.any { it.name == name }
    if (sourceBinding || importedBinding) return null

    val owner = ctx.scene.sdkClasses.singleOrNull { it.name == "Math" } ?: return null
    val existing = staticStorage[owner]
    if (existing != null) return existing

    val instance = getStaticInstance(owner)
    with(ctx) {
        for ((field, value) in nativeMathConstants) {
            memory.write(mkFieldLValue(fp64Sort, instance, field), mkFp64(value), guard = trueExpr)
        }
    }
    return instance
}

/** Property descriptors of the native constants are not mutable ordinary field storage. */
internal fun TsState.isNativeMathConstant(instance: UHeapRef, field: String): Boolean {
    if (field !in nativeMathConstants) return false
    val owner = ctx.scene.sdkClasses.singleOrNull { it.name == "Math" } ?: return false
    return staticStorage[owner] == instance
}
