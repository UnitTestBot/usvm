import org.usvm.instrumentation.util.`try`
import sun.misc.Unsafe
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier


object ReflectionUtils {

    var UNSAFE: Unsafe

    init {
        try {
            val uns = Unsafe::class.java.getDeclaredField("theUnsafe")
            uns.isAccessible = true
            UNSAFE = uns[null] as Unsafe
        } catch (e: Throwable) {
            throw RuntimeException()
        }
    }

}

fun Field.getFieldValue(instance: Any?): Any? {
    try {
        val fixedInstance =
            if (this.isStatic()) {
                null
            } else instance
        return withAccessibility {
            when (this.type) {
                Boolean::class.javaPrimitiveType -> this.getBoolean(fixedInstance)
                Byte::class.javaPrimitiveType -> this.getByte(fixedInstance)
                Char::class.javaPrimitiveType -> this.getChar(fixedInstance)
                Short::class.javaPrimitiveType -> this.getShort(fixedInstance)
                Int::class.javaPrimitiveType -> this.getInt(fixedInstance)
                Long::class.javaPrimitiveType -> this.getLong(fixedInstance)
                Float::class.javaPrimitiveType -> this.getFloat(fixedInstance)
                Double::class.javaPrimitiveType -> this.getDouble(fixedInstance)
                else -> this.get(fixedInstance)
            }
        }
    } catch (_: Throwable) {
        return null
    }
}

fun Method.invokeWithAccessibility(instance: Any?, vararg args: Any?): Any? =
    withAccessibility {
        invoke(instance, args)
    }

fun Field.setFieldValue(instance: Any?, fieldValue: Any?) {
    withAccessibility {
        val fixedInstance =
            if (this.isStatic()) {
                null
            } else instance
        when (this.type) {
            Boolean::class.javaPrimitiveType -> this.setBoolean(fixedInstance, fieldValue as Boolean)
            Byte::class.javaPrimitiveType -> this.setByte(fixedInstance, fieldValue as Byte)
            Char::class.javaPrimitiveType -> this.setChar(fixedInstance, fieldValue as Char)
            Short::class.javaPrimitiveType -> this.setShort(fixedInstance, fieldValue as Short)
            Int::class.javaPrimitiveType -> this.setInt(fixedInstance, fieldValue as Int)
            Long::class.javaPrimitiveType -> this.setLong(fixedInstance, fieldValue as Long)
            Float::class.javaPrimitiveType -> this.setFloat(fixedInstance, fieldValue as Float)
            Double::class.javaPrimitiveType -> this.setDouble(fixedInstance, fieldValue as Double)
            else -> this.set(fixedInstance, fieldValue)
        }
    }
}

val Class<*>.allFields
    get(): List<Field> {
        val result = mutableListOf<Field>()
        var current: Class<*>? = this
        do {
            result += current!!.declaredFields
            current = current!!.superclass
        } while (current != null)
        return result
    }

fun Class<*>.getFieldByName(name: String): Field {
    var result: Field?
    var current: Class<*> = this
    do {
        result = `try` { current.getDeclaredField(name) }.getOrNull()
        current = current.superclass ?: break
    } while (result == null)
    return result
        ?: throw NoSuchFieldException()
}

inline fun <reified R> Field.withAccessibility(block: () -> R): R {
    val prevIsFinal = isFinal
    val prevAccessibility = isAccessible

    isAccessible = true
    isFinal = false

    try {
        return block()
    } finally {
        isAccessible = prevAccessibility
        isFinal = prevIsFinal
    }
}

inline fun <reified R> Method.withAccessibility(block: () -> R): R {
    val prevAccessibility = isAccessible

    isAccessible = true

    try {
        return block()
    } finally {
        isAccessible = prevAccessibility
    }
}

fun Field.isStatic() = modifiers.and(Modifier.STATIC) > 0

var Field.isFinal: Boolean
    get() = (this.modifiers and Modifier.FINAL) == Modifier.FINAL
    set(value) {
        if (value == this.isFinal) return
        // In java 9+ use varhandles
        val modifiersField = this.javaClass.getDeclaredField("modifiers")
        modifiersField.isAccessible = true
        modifiersField.setInt(this, this.modifiers and if (value) Modifier.FINAL else Modifier.FINAL.inv())
    }
