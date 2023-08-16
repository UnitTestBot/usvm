package org.usvm.fuzzer.util

import org.usvm.fuzzer.generator.DataGenerator
import java.util.*

object FuzzingContext {
    var seed: Long = 0
    val random = Random(seed)


    fun nextBoolean() = random.nextBoolean()
    fun nextByte() = random.nextInt(Byte.MAX_VALUE + 1).toByte()
    fun nextShort() = random.nextInt(Short.MAX_VALUE + 1).toShort()
    fun nextInt() = random.nextInt()
    fun nextInt(bound: Int) = random.nextInt(bound)
    fun nextLong() = random.nextLong()
    fun nextFloat() = random.nextFloat()
    fun nextDouble() = random.nextDouble()
    fun nextChar() = ('a' + nextInt(26))
    fun nextString() = UUID.randomUUID().toString()

    fun nextBooleanArray(arraySize: Int) = BooleanArray(arraySize) { nextBoolean() }
    fun nextByteArray(arraySize: Int) = ByteArray(arraySize) { nextByte() }
    fun nextShortArray(arraySize: Int) = ShortArray(arraySize) { nextShort() }
    fun nextIntArray(arraySize: Int) = IntArray(arraySize) { nextInt() }
    fun nextLongArray(arraySize: Int) = LongArray(arraySize) { nextLong() }
    fun nextFloatArray(arraySize: Int) = FloatArray(arraySize) { nextFloat() }
    fun nextDoubleArray(arraySize: Int) = DoubleArray(arraySize) { nextDouble() }
    fun nextCharArray(arraySize: Int) = CharArray(arraySize) { nextChar() }
    fun nextStringArray(arraySize: Int) = Array(arraySize) { nextString() }

}