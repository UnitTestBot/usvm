package org.usvm.ts.pbt.dsl

import org.usvm.ts.pbt.model.JsConcreteValue

/** Kotlin call-site identity. Columns are unavailable without compiler source metadata. */
data class KotlinPropertySource(
    val className: String,
    val methodName: String,
    val fileName: String?,
    val line: Int,
)

/** Runtime JavaScript value type, independent of a generator's narrower input domain. */
enum class PropertyValueType(val typeScriptName: String) {
    NUMBER("number"),
    BOOLEAN("boolean"),
    STRING("string"),
}

/** Structured author expression retained before TypeScript lowering. */
data class PropertyExpressionNode(
    val id: Int,
    val type: PropertyValueType,
    val source: KotlinPropertySource,
    val term: PropertyExpressionTerm,
)

/** Expressions describe execution; constructing them never invokes TypeScript. */
sealed interface PropertyExpressionTerm {
    data class Input(val name: String) : PropertyExpressionTerm
    data class Constant(val value: JsConcreteValue) : PropertyExpressionTerm
    data class Operation(val operator: String, val arguments: List<PropertyExpressionNode>) : PropertyExpressionTerm
    data class Call(
        val function: PropertyFunctionReference,
        val arguments: List<PropertyExpressionNode>,
    ) : PropertyExpressionTerm
    data class Local(val name: String) : PropertyExpressionTerm
}

/** Module-qualified callable with a primitive TypeScript signature. */
data class PropertyFunctionReference(
    val module: String,
    val exportName: String,
    val parameters: List<PropertyValueType>,
    val result: PropertyValueType,
)

/** Typed handle to one expression in a property; Kotlin equality is deliberately unavailable. */
class PropertyExpression<T> internal constructor(
    internal val builder: PropertyBuilder,
    val node: PropertyExpressionNode,
) {
    override fun equals(other: Any?): Boolean = error("Use equalTo or notEqualTo inside a property")

    override fun hashCode(): Int = System.identityHashCode(this)

    override fun toString(): String = "PropertyExpression(${node.id}, ${node.type})"
}

/** Typed imported function handle. [F] is its Kotlin-shaped JavaScript signature. */
class PropertyFunction<F> internal constructor(
    internal val builder: PropertyBuilder,
    val reference: PropertyFunctionReference,
)

operator fun <R> PropertyFunction<() -> R>.invoke(): PropertyExpression<R> =
    builder.call(reference, emptyList())

operator fun <A, R> PropertyFunction<(A) -> R>.invoke(a: PropertyExpression<A>): PropertyExpression<R> =
    builder.call(reference, listOf(a))

operator fun <A, B, R> PropertyFunction<(A, B) -> R>.invoke(
    a: PropertyExpression<A>,
    b: PropertyExpression<B>,
): PropertyExpression<R> = builder.call(reference, listOf(a, b))

operator fun <A, B, C, R> PropertyFunction<(A, B, C) -> R>.invoke(
    a: PropertyExpression<A>,
    b: PropertyExpression<B>,
    c: PropertyExpression<C>,
): PropertyExpression<R> = builder.call(reference, listOf(a, b, c))

private fun <T> PropertyExpression<*>.operation(
    operator: String,
    result: PropertyValueType,
    other: PropertyExpression<*>? = null,
): PropertyExpression<T> {
    val arguments = if (other === null) listOf(this) else listOf(this, other)
    return builder.operation(operator, result, arguments)
}

operator fun PropertyExpression<Double>.plus(other: PropertyExpression<Double>): PropertyExpression<Double> =
    operation("+", PropertyValueType.NUMBER, other)

operator fun PropertyExpression<Double>.minus(other: PropertyExpression<Double>): PropertyExpression<Double> =
    operation("-", PropertyValueType.NUMBER, other)

operator fun PropertyExpression<Double>.times(other: PropertyExpression<Double>): PropertyExpression<Double> =
    operation("*", PropertyValueType.NUMBER, other)

operator fun PropertyExpression<Double>.div(other: PropertyExpression<Double>): PropertyExpression<Double> =
    operation("/", PropertyValueType.NUMBER, other)

operator fun PropertyExpression<Double>.rem(other: PropertyExpression<Double>): PropertyExpression<Double> =
    operation("%", PropertyValueType.NUMBER, other)

operator fun PropertyExpression<Double>.unaryMinus(): PropertyExpression<Double> =
    operation("-", PropertyValueType.NUMBER)

operator fun PropertyExpression<Double>.plus(other: Number): PropertyExpression<Double> = this + builder.number(other)
operator fun PropertyExpression<Double>.minus(other: Number): PropertyExpression<Double> = this - builder.number(other)
operator fun PropertyExpression<Double>.times(other: Number): PropertyExpression<Double> = this * builder.number(other)
operator fun PropertyExpression<Double>.div(other: Number): PropertyExpression<Double> = this / builder.number(other)
operator fun PropertyExpression<Double>.rem(other: Number): PropertyExpression<Double> = this % builder.number(other)

infix fun <T> PropertyExpression<T>.equalTo(other: PropertyExpression<T>): PropertyExpression<Boolean> =
    operation("===", PropertyValueType.BOOLEAN, other)

infix fun <T> PropertyExpression<T>.notEqualTo(other: PropertyExpression<T>): PropertyExpression<Boolean> =
    operation("!==", PropertyValueType.BOOLEAN, other)

infix fun PropertyExpression<Double>.equalTo(other: Number): PropertyExpression<Boolean> =
    this equalTo builder.number(other)

infix fun PropertyExpression<Double>.notEqualTo(other: Number): PropertyExpression<Boolean> =
    this notEqualTo builder.number(other)

infix fun PropertyExpression<Boolean>.equalTo(other: Boolean): PropertyExpression<Boolean> =
    this equalTo builder.boolean(other)

infix fun PropertyExpression<String>.equalTo(other: String): PropertyExpression<Boolean> =
    this equalTo builder.string(other)

infix fun PropertyExpression<Double>.lessThan(other: PropertyExpression<Double>): PropertyExpression<Boolean> =
    operation("<", PropertyValueType.BOOLEAN, other)

infix fun PropertyExpression<Double>.lessOrEqual(other: PropertyExpression<Double>): PropertyExpression<Boolean> =
    operation("<=", PropertyValueType.BOOLEAN, other)

infix fun PropertyExpression<Double>.greaterThan(other: PropertyExpression<Double>): PropertyExpression<Boolean> =
    operation(">", PropertyValueType.BOOLEAN, other)

infix fun PropertyExpression<Double>.greaterOrEqual(other: PropertyExpression<Double>): PropertyExpression<Boolean> =
    operation(">=", PropertyValueType.BOOLEAN, other)

infix fun PropertyExpression<Double>.lessThan(other: Number): PropertyExpression<Boolean> =
    this lessThan builder.number(other)

infix fun PropertyExpression<Double>.lessOrEqual(other: Number): PropertyExpression<Boolean> =
    this lessOrEqual builder.number(other)

infix fun PropertyExpression<Double>.greaterThan(other: Number): PropertyExpression<Boolean> =
    this greaterThan builder.number(other)

infix fun PropertyExpression<Double>.greaterOrEqual(other: Number): PropertyExpression<Boolean> =
    this greaterOrEqual builder.number(other)

infix fun PropertyExpression<Boolean>.and(other: PropertyExpression<Boolean>): PropertyExpression<Boolean> =
    operation("&&", PropertyValueType.BOOLEAN, other)

infix fun PropertyExpression<Boolean>.or(other: PropertyExpression<Boolean>): PropertyExpression<Boolean> =
    operation("||", PropertyValueType.BOOLEAN, other)

operator fun PropertyExpression<Boolean>.not(): PropertyExpression<Boolean> =
    operation("!", PropertyValueType.BOOLEAN)

internal fun propertySource(): KotlinPropertySource {
    val frame = Thread.currentThread().stackTrace.first { frame ->
        frame.fileName != null && frame.className != Thread::class.java.name && frame.fileName !in DSL_FILES
    }
    return KotlinPropertySource(frame.className, frame.methodName, frame.fileName, frame.lineNumber)
}

private val DSL_FILES = setOf("PropertyExpression.kt", "PropertyBuilder.kt", "TsPropertyTest.kt")
