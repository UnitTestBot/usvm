package org.usvm.ts.pbt.gen

import org.jacodb.ets.model.EtsAnyType
import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsBooleanType
import org.jacodb.ets.model.EtsClass
import org.jacodb.ets.model.EtsClassType
import org.jacodb.ets.model.EtsFunctionType
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsNullType
import org.jacodb.ets.model.EtsNumberType
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsStringType
import org.jacodb.ets.model.EtsType
import org.jacodb.ets.model.EtsUnclearRefType
import org.jacodb.ets.model.EtsUndefinedType
import org.jacodb.ets.model.EtsUnionType
import org.jacodb.ets.model.EtsUnknownType
import org.usvm.ts.pbt.interpreter.VArray
import org.usvm.ts.pbt.interpreter.VBool
import org.usvm.ts.pbt.interpreter.VFunction
import org.usvm.ts.pbt.interpreter.VMap
import org.usvm.ts.pbt.interpreter.VNull
import org.usvm.ts.pbt.interpreter.VNumber
import org.usvm.ts.pbt.interpreter.VObject
import org.usvm.ts.pbt.interpreter.VSet
import org.usvm.ts.pbt.interpreter.VString
import org.usvm.ts.pbt.interpreter.VUndefined
import org.usvm.ts.pbt.interpreter.VValue
import kotlin.random.Random

/**
 * Type-driven random input generation for an [EtsMethod], biased with
 * constants mined from the method body.
 */
class InputGenerator(
    private val scene: EtsScene,
    private val method: EtsMethod,
    private val random: Random,
) {
    private val mined = MinedConstants.of(method)

    private val interestingNumbers: List<Double> = buildList {
        add(0.0); add(-0.0); add(1.0); add(-1.0)
        add(Double.NaN); add(Double.POSITIVE_INFINITY); add(Double.NEGATIVE_INFINITY)
        add(Double.MAX_VALUE); add(Double.MIN_VALUE)
        for (c in mined.numbers) {
            add(c); add(c + 1); add(c - 1)
        }
    }

    private val interestingStrings: List<String> = buildList {
        add(""); add("0"); add("a"); add(" ")
        addAll(mined.strings)
    }

    fun generateArgs(): List<VValue> =
        method.parameters.map { generate(it.type, depth = 0) }

    /** `this` instance for the enclosing class (instance methods). */
    fun generateThis(): VValue {
        val cls = method.enclosingClass ?: return VUndefined
        return instantiate(cls, depth = 0)
    }

    fun generate(type: EtsType, depth: Int): VValue = when (type) {
        is EtsNumberType -> genNumber()
        is EtsBooleanType -> VBool(random.nextBoolean())
        is EtsStringType -> genString()
        is EtsNullType -> VNull
        is EtsUndefinedType -> VUndefined

        is EtsArrayType -> genArray(type.elementType, depth)

        is EtsClassType -> genRefByName(type.signature.name, depth)
            ?: substantialClassByName(type.signature.name)?.let { instantiate(it, depth) }
            ?: VObject(null)

        is EtsUnclearRefType -> genRefByName(type.name, depth)
            ?: substantialClassByName(type.name)?.let { instantiate(it, depth) }
            ?: VObject(null)

        is EtsFunctionType -> genFunction(type)

        is EtsUnionType ->
            if (type.types.isEmpty()) genAny(depth)
            else generate(type.types[random.nextInt(type.types.size)], depth)

        is EtsAnyType, is EtsUnknownType -> genAny(depth)

        else -> genAny(depth) // unclear refs, generics, aliases: fall back to the full mix
    }

    private fun genAny(depth: Int): VValue = when (random.nextInt(if (depth < 2) 8 else 6)) {
        0 -> genNumber()
        1 -> VBool(random.nextBoolean())
        2 -> genString()
        3 -> VNull
        4 -> VUndefined
        5 -> genNumber()  // numbers are twice as likely: the dominant type in practice
        6 -> genArray(EtsUnknownType, depth + 1)
        else -> VObject(null, mutableMapOf())
    }

    fun genNumber(): VNumber = when (random.nextInt(4)) {
        0 -> VNumber(interestingNumbers[random.nextInt(interestingNumbers.size)])
        1 -> VNumber(random.nextInt(-10, 11).toDouble())
        2 -> VNumber(random.nextInt(-1000, 1001).toDouble())
        else -> VNumber(
            // Random double, occasionally non-integral
            if (random.nextBoolean()) random.nextDouble(-1e6, 1e6)
            else random.nextInt(-100, 101) + random.nextDouble()
        )
    }

    fun genString(): VString = when (random.nextInt(3)) {
        0 -> VString(interestingStrings[random.nextInt(interestingStrings.size)])
        1 -> VString(random.nextInt(-100, 101).toString()) // numeric strings matter for coercions
        else -> VString(
            (1..random.nextInt(1, 6))
                .map { "abcxyz01"[random.nextInt(8)] }
                .joinToString("")
        )
    }

    /** Built-in container types are not scene classes. */
    private fun genRefByName(name: String, depth: Int): VValue? = when (name) {
        "Array" -> genArray(EtsUnknownType, depth)
        "Map" -> {
            val map = VMap()
            repeat(random.nextInt(0, 4)) {
                map.entries[genPrimitiveKey()] = generate(EtsUnknownType, depth + 1)
            }
            map
        }

        "Set" -> {
            val set = VSet()
            repeat(random.nextInt(0, 4)) { set.elements.add(genPrimitiveKey()) }
            set
        }

        else -> null
    }

    private fun genPrimitiveKey(): VValue = when (random.nextInt(3)) {
        0 -> genNumber()
        1 -> genString()
        else -> VBool(random.nextBoolean())
    }

    /**
     * A function-typed input (a callback, a comparator field): pick a scene
     * method with a matching arity — real projects usually have a suitable one
     * (e.g. `defaultCompare`) — falling back to any method of that arity.
     */
    private fun genFunction(type: EtsFunctionType): VValue {
        val sig = type.signature
        val declared = scene.projectAndSdkClasses.asSequence()
            .flatMap { it.methods }
            .filter { it.cfg.stmts.isNotEmpty() }
            .firstOrNull { it.name == sig.name && sig.name.isNotBlank() }
        if (declared != null) return VFunction(declared)

        val arity = sig.parameters.size
        val candidates = scene.projectClasses
            .flatMap { it.methods }
            .filter {
                it.cfg.stmts.isNotEmpty() && it.parameters.size == arity &&
                    !it.name.startsWith("%") && it.name != "constructor"
            }
        if (candidates.isEmpty()) return VUndefined
        return VFunction(candidates[random.nextInt(candidates.size)])
    }

    private fun genArray(elementType: EtsType, depth: Int): VArray {
        var size = 0
        while (size < 8 && random.nextDouble() < 0.6) size++ // geometric
        return VArray(MutableList(size) { generate(elementType, depth + 1) })
    }

    /** Prefer definitions over phantom declarations produced by path-alias imports. */
    private fun substantialClassByName(name: String): EtsClass? =
        scene.projectAndSdkClasses
            .filter { it.name == name }
            .maxByOrNull { c -> c.methods.count { it.cfg.stmts.isNotEmpty() } * 2 + c.fields.size }

    private fun instantiate(cls: EtsClass, depth: Int): VObject {
        val fields = mutableMapOf<String, VValue>()
        if (depth < 3) {
            for (field in cls.fields) {
                fields[field.name] = generate(field.type, depth + 1)
            }
        }
        return VObject(cls, fields)
    }
}
