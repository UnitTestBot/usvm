package org.usvm.ts.pbt.dsl

import org.usvm.ts.pbt.model.BooleanDomain
import org.usvm.ts.pbt.model.IntegerDomain
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.JsNumber
import org.usvm.ts.pbt.model.NumberDomain
import org.usvm.ts.pbt.model.PropertyDefinition
import org.usvm.ts.pbt.model.PropertyDomain
import org.usvm.ts.pbt.model.PropertyId
import org.usvm.ts.pbt.model.PropertyInput
import org.usvm.ts.pbt.model.StringDomain
import org.usvm.ts.pbt.model.TypeScriptEntryPoint
import org.usvm.ts.pbt.validation.requireValid
import org.usvm.ts.pbt.validation.validatePropertyDefinition
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty
import kotlin.reflect.KType
import kotlin.reflect.typeOf

/** Stable author-assigned check and its complete expression. */
data class NamedPropertyCheck(
    val name: String,
    val expression: PropertyExpressionNode,
    val source: KotlinPropertySource,
)

/** One explicitly named expression evaluated once after admission and before the checks. */
data class PropertyLocal(val name: String, val expression: PropertyExpressionNode)

/** Immutable Kotlin property program; observations and inferred hypotheses are separate artifacts. */
data class DslProperty(
    val id: PropertyId,
    val inputs: List<PropertyInput>,
    val assumptions: List<PropertyExpressionNode>,
    val locals: List<PropertyLocal>,
    val checks: List<NamedPropertyCheck>,
)

/** Scope marker prevents accidentally declaring inputs or checks in another property's scope. */
@DslMarker
annotation class TypeScriptPropertyDsl

/** Builds a typed JavaScript property without executing any user functions. */
@TypeScriptPropertyDsl
class PropertyBuilder internal constructor() {
    private val inputs = mutableListOf<PropertyInput>()
    private val assumptions = mutableListOf<PropertyExpressionNode>()
    private val locals = mutableListOf<PropertyLocal>()
    private val checks = mutableListOf<NamedPropertyCheck>()
    private val names = hashSetOf<String>()
    private var nextId = 0

    fun int(min: Int = Int.MIN_VALUE, max: Int = Int.MAX_VALUE): PropertyInputDelegate<Double> {
        require(min <= max) { "Invalid integer range" }
        return PropertyInputDelegate(this, IntegerDomain(min = min, max = max), PropertyValueType.NUMBER)
    }

    fun number(
        min: Double = Double.NEGATIVE_INFINITY,
        max: Double = Double.POSITIVE_INFINITY,
        allowNaN: Boolean = min == Double.NEGATIVE_INFINITY && max == Double.POSITIVE_INFINITY,
    ): PropertyInputDelegate<Double> {
        require(!min.isNaN() && !max.isNaN() && min <= max) { "Invalid number range" }
        val domain = NumberDomain(min = JsNumber.fromDouble(min), max = JsNumber.fromDouble(max), allowNaN = allowNaN)
        return PropertyInputDelegate(this, domain, PropertyValueType.NUMBER)
    }

    fun boolean(): PropertyInputDelegate<Boolean> =
        PropertyInputDelegate(this, BooleanDomain, PropertyValueType.BOOLEAN)

    fun string(minLength: Int = 0, maxLength: Int = 10): PropertyInputDelegate<String> {
        require(minLength >= 0 && minLength <= maxLength) { "Invalid string bounds" }
        val domain = StringDomain(minLength = minLength, maxLength = maxLength)
        return PropertyInputDelegate(this, domain, PropertyValueType.STRING)
    }

    fun number(value: Number): PropertyExpression<Double> = number(value.toDouble())

    fun number(value: Double): PropertyExpression<Double> =
        expression(PropertyValueType.NUMBER, PropertyExpressionTerm.Constant(JsConcreteValue.number(value)))

    fun boolean(value: Boolean): PropertyExpression<Boolean> =
        expression(PropertyValueType.BOOLEAN, PropertyExpressionTerm.Constant(JsConcreteValue.Boolean(value)))

    fun string(value: String): PropertyExpression<String> =
        expression(PropertyValueType.STRING, PropertyExpressionTerm.Constant(JsConcreteValue.String(value)))

    inline fun <reified F> function(module: String, exportName: String): PropertyFunction<F> =
        createFunction(module, exportName, typeOf<F>())

    @PublishedApi
    internal fun <F> createFunction(module: String, exportName: String, signature: KType): PropertyFunction<F> {
        val referenceInputs = listOf(PropertyInput(name = "argument", domain = BooleanDomain))
        val referenceDefinition = PropertyDefinition(
            id = PropertyId("dsl.function-reference"),
            inputs = referenceInputs,
            predicate = TypeScriptEntryPoint(module = module, exportName = exportName),
        )
        requireValid(validatePropertyDefinition(referenceDefinition))
        require(!signature.isMarkedNullable) { "Nullable function signatures are unsupported" }
        val functionName = (signature.classifier as? kotlin.reflect.KClass<*>)?.qualifiedName.orEmpty()
        require(functionName.startsWith("kotlin.Function")) { "Use a function type as the TypeScript signature" }
        val types = signature.arguments.map { argument ->
            val type = requireNotNull(argument.type) { "Star-projected function signatures are unsupported" }
            require(!type.isMarkedNullable) { "Nullable function types are unsupported" }
            when (type.classifier) {
                Double::class -> PropertyValueType.NUMBER
                Boolean::class -> PropertyValueType.BOOLEAN
                String::class -> PropertyValueType.STRING
                else -> error("Use Double, Boolean, or String for JavaScript function signatures")
            }
        }
        require(types.isNotEmpty() && types.size <= MAX_SIGNATURE_TYPES) {
            "Functions with zero to three inputs are supported"
        }
        val reference = PropertyFunctionReference(module, exportName, types.dropLast(1), types.last())
        return PropertyFunction(this, reference)
    }

    fun assume(condition: PropertyBuilder.() -> PropertyExpression<Boolean>) {
        require(checks.isEmpty() && locals.isEmpty()) { "Declare admission conditions before shared values and checks" }
        val expression = condition()
        requireOwned(expression)
        require(isInputExpression(expression.node)) { "Admission conditions must use only inputs and constants" }
        assumptions += expression.node
    }

    fun check(name: String, condition: PropertyBuilder.() -> PropertyExpression<Boolean>) {
        PropertyId(name)
        require(checks.none { it.name == name }) { "Duplicate check name: $name" }
        val source = propertySource()
        val expression = condition()
        requireOwned(expression)
        checks += NamedPropertyCheck(name, expression.node, source)
    }

    fun <T> value(expression: PropertyExpression<T>): PropertyLocalDelegate<T> {
        requireOwned(expression)
        require(checks.isEmpty()) { "Declare shared values before checks" }
        return PropertyLocalDelegate(this, expression)
    }

    internal fun <T> input(name: String, domain: PropertyDomain, type: PropertyValueType): PropertyExpression<T> {
        require(checks.isEmpty() && locals.isEmpty()) { "Declare inputs before shared values and checks" }
        registerName(name)
        inputs += PropertyInput(name = name, domain = domain)
        return expression(type, PropertyExpressionTerm.Input(name))
    }

    internal fun <T> local(name: String, value: PropertyExpression<T>): PropertyExpression<T> {
        registerName(name)
        locals += PropertyLocal(name, value.node)
        return expression(value.node.type, PropertyExpressionTerm.Local(name))
    }

    internal fun <T> call(
        function: PropertyFunctionReference,
        arguments: List<PropertyExpression<*>>,
    ): PropertyExpression<T> {
        arguments.forEach(::requireOwned)
        require(arguments.map { it.node.type } == function.parameters) { "TypeScript argument types do not match" }
        val term = PropertyExpressionTerm.Call(function, arguments.map { it.node })
        return expression(function.result, term)
    }

    internal fun <T> operation(
        operator: String,
        type: PropertyValueType,
        arguments: List<PropertyExpression<*>>,
    ): PropertyExpression<T> {
        arguments.forEach(::requireOwned)
        val term = PropertyExpressionTerm.Operation(operator, arguments.map { it.node })
        return expression(type, term)
    }

    internal fun build(id: PropertyId): DslProperty {
        require(inputs.isNotEmpty()) { "A property requires at least one input" }
        require(checks.isNotEmpty()) { "A property requires at least one named check" }
        return DslProperty(id, inputs.toList(), assumptions.toList(), locals.toList(), checks.toList())
    }

    private fun <T> expression(type: PropertyValueType, term: PropertyExpressionTerm): PropertyExpression<T> {
        val node = PropertyExpressionNode(nextId++, type, propertySource(), term)
        return PropertyExpression(this, node)
    }

    private fun requireOwned(expression: PropertyExpression<*>) {
        require(expression.builder === this) { "Cannot mix expressions from different properties" }
    }

    private fun registerName(name: String) {
        val valid = name.matches(Regex("[A-Za-z_$][A-Za-z0-9_$]*")) &&
            !name.startsWith("__usvm") && name !in RESERVED_NAMES
        require(valid) {
            "Invalid or reserved property variable: $name"
        }
        require(names.add(name)) { "Duplicate property variable: $name" }
    }

    private fun isInputExpression(node: PropertyExpressionNode): Boolean = when (val term = node.term) {
        is PropertyExpressionTerm.Input, is PropertyExpressionTerm.Constant -> true
        is PropertyExpressionTerm.Operation -> term.arguments.all(::isInputExpression)
        is PropertyExpressionTerm.Call, is PropertyExpressionTerm.Local -> false
    }
}

private const val MAX_SIGNATURE_TYPES = 4
private val RESERVED_NAMES = setOf(
    "break", "case", "catch", "class", "const", "continue", "debugger", "default", "delete", "do", "else",
    "enum", "export", "extends", "false", "finally", "for", "function", "if", "implements", "import", "in",
    "instanceof", "interface", "let", "new", "null", "package", "private", "protected", "public", "return",
    "static", "super", "switch", "this", "throw", "true", "try", "typeof", "var", "void", "while", "with", "yield",
)

/** Input declaration delegate: `val argument by int(...)` retains the author's variable name. */
class PropertyInputDelegate<T> internal constructor(
    private val builder: PropertyBuilder,
    private val domain: PropertyDomain,
    private val type: PropertyValueType,
) {
    operator fun provideDelegate(thisRef: Any?, property: KProperty<*>): ReadOnlyProperty<Any?, PropertyExpression<T>> {
        val expression = builder.input<T>(property.name, domain, type)
        return ReadOnlyProperty { _, _ -> expression }
    }
}

/** Shared value declaration delegate; the generated program evaluates its expression exactly once. */
class PropertyLocalDelegate<T> internal constructor(
    private val builder: PropertyBuilder,
    private val expression: PropertyExpression<T>,
) {
    operator fun provideDelegate(thisRef: Any?, property: KProperty<*>): ReadOnlyProperty<Any?, PropertyExpression<T>> {
        val local = builder.local(property.name, expression)
        return ReadOnlyProperty { _, _ -> local }
    }
}

/** Defines a reusable property without starting a backend or creating generated files. */
fun defineProperty(id: String, declaration: PropertyBuilder.() -> Unit): DslProperty {
    val builder = PropertyBuilder()
    builder.declaration()
    return builder.build(PropertyId(id))
}
