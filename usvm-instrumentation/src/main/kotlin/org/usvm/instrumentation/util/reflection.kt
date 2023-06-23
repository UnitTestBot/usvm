import org.usvm.instrumentation.util.`try`
import sun.misc.Unsafe
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
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

fun Method.invokeWithAccessibility(instance: Any?, args: List<Any?>): Any? =
    try {
        withAccessibility {
            invoke(instance, *args.toTypedArray())
        }
    } catch (e: InvocationTargetException) {
        throw e.cause ?: e
    }

fun Constructor<*>.newInstanceWithAccessibility(args: List<Any?>): Any =
    try {
        withAccessibility {
            newInstance(*args.toTypedArray())
        }
    } catch (e: InvocationTargetException) {
        throw e.cause ?: e
    }

fun Field.setFieldValue(instance: Any?, fieldValue: Any?) {
    withAccessibility {
        val fixedInstance =
            if (this.isStatic()) {
                null
            } else instance
        when (this.type) {
            Boolean::class.javaPrimitiveType -> this.setBoolean(fixedInstance, fieldValue as? Boolean ?: false)
            Byte::class.javaPrimitiveType -> this.setByte(fixedInstance, fieldValue as? Byte ?: 0)
            Char::class.javaPrimitiveType -> this.setChar(fixedInstance, fieldValue as? Char ?: '\u0000')
            Short::class.javaPrimitiveType -> this.setShort(fixedInstance, fieldValue as? Short ?: 0)
            Int::class.javaPrimitiveType -> this.setInt(fixedInstance, fieldValue as? Int ?: 0)
            Long::class.javaPrimitiveType -> this.setLong(fixedInstance, fieldValue as? Long ?: 0L)
            Float::class.javaPrimitiveType -> this.setFloat(fixedInstance, fieldValue as? Float ?: 0.0f)
            Double::class.javaPrimitiveType -> this.setDouble(fixedInstance, fieldValue as? Double ?: 0.0)
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

fun Class<*>.getFieldByName(name: String): Field? {
    var result: Field?
    var current: Class<*> = this
    do {
        result = `try` { current.getDeclaredField(name) }.getOrNull()
        current = current.superclass ?: break
    } while (result == null)
    return result
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

inline fun <reified R> Constructor<*>.withAccessibility(block: () -> R): R {
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
