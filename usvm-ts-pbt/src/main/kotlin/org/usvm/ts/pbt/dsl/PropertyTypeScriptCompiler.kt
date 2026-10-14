package org.usvm.ts.pbt.dsl

import kotlinx.serialization.json.Json
import org.usvm.ts.pbt.model.BooleanDomain
import org.usvm.ts.pbt.model.IntegerDomain
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.NumberDomain
import org.usvm.ts.pbt.model.PropertyAssertion
import org.usvm.ts.pbt.model.PropertyDefinition
import org.usvm.ts.pbt.model.PropertyOperand
import org.usvm.ts.pbt.model.PropertySourcePoint
import org.usvm.ts.pbt.model.StringDomain
import org.usvm.ts.pbt.model.TypeScriptEntryPoint
import org.usvm.ts.pbt.model.encodeToUtf8SafeString

/** Generated source and existing executable property, with the original Kotlin expression tree retained. */
data class CompiledDslProperty(
    val program: DslProperty,
    val definition: PropertyDefinition,
    val typeScript: String,
    val expressionLocations: Map<Int, List<GeneratedPropertyRange>>,
)

/** One expression occurrence in generated TypeScript; columns are one-based UTF-16 and end-exclusive. */
data class GeneratedPropertyRange(
    val start: PropertySourcePoint,
    val endColumn: Int,
)

/** Lowers one author program into boolean entry points and a concrete diagnostic companion. */
class PropertyTypeScriptCompiler {
    fun compile(
        program: DslProperty,
        generatedModule: String,
        resolveModule: (String) -> String,
    ): CompiledDslProperty {
        val emitter = PropertyEmitter(program, generatedModule, resolveModule)
        return emitter.compile()
    }
}

private class PropertyEmitter(
    private val program: DslProperty,
    private val module: String,
    private val resolveModule: (String) -> String,
) {
    private val source = StringBuilder()
    private val functions = linkedMapOf<PropertyFunctionReference, String>()
    private val expressionLocations = mutableMapOf<Int, MutableList<GeneratedPropertyRange>>()
    private val assertions = mutableListOf<PropertyAssertion>()
    private var line = 1

    fun compile(): CompiledDslProperty {
        allExpressions().forEach(::collectFunctions)
        functions.forEach { (function, alias) ->
            val path = resolveModule(function.module)
            appendLine("import { ${function.exportName} as $alias } from ${quote(path)};")
        }
        val parameters = program.inputs.joinToString { input ->
            val type = programInputType(input.name)
            "${input.name}: ${type.typeScriptName}"
        }
        appendLine("export function __usvmPrecondition($parameters): boolean {")
        var admissionColumn = "  return ".length + 1
        val admission = program.assumptions.joinToString(separator = " && ") { node ->
            val rendered = expression(node, admissionColumn)
            admissionColumn += rendered.length + " && ".length
            rendered
        }.ifEmpty { "true" }
        appendLine("  return $admission;")
        appendLine("}")
        emitPredicate(parameters, diagnostic = false)
        emitPredicate(parameters, diagnostic = true)

        val definition = PropertyDefinition(
            id = program.id,
            inputs = program.inputs,
            predicate = TypeScriptEntryPoint(
                module = module,
                exportName = "__usvmPredicate",
                checkResultExportName = "__usvmCheckResult",
            ),
            precondition = TypeScriptEntryPoint(module = module, exportName = "__usvmPrecondition"),
            assertions = assertions,
        )
        val locations = expressionLocations.mapValues { (_, ranges) -> ranges.toList() }
        return CompiledDslProperty(program, definition, source.toString(), locations)
    }

    private fun emitPredicate(parameters: String, diagnostic: Boolean) {
        val name = if (diagnostic) "__usvmCheckResult" else "__usvmPredicate"
        val resultType = if (diagnostic) {
            "{ passed: boolean; checkId: string; actual: unknown; expected: unknown; exception?: { value: unknown } }"
        } else {
            "boolean"
        }
        appendLine("export function $name($parameters): $resultType {")
        program.locals.forEach { local -> appendValue(local.name, local.expression) }
        program.checks.forEachIndexed { index, check -> emitCheck(check, index, diagnostic) }
        val success = if (diagnostic) "{ passed: true, checkId: '', actual: true, expected: true }" else "true"
        appendLine("  return $success;")
        appendLine("}")
    }

    private fun emitCheck(check: NamedPropertyCheck, index: Int, diagnostic: Boolean) {
        val indentation = if (diagnostic) "    " else "  "
        if (diagnostic) appendLine("  try {")
        val term = check.expression.term
        val isEquality = term is PropertyExpressionTerm.Operation && term.operator in setOf("===", "!==")
        val actual = "__usvmActual$index"
        val expected = "__usvmExpected$index"
        val condition: String
        val operands: List<PropertyOperand>
        if (isEquality) {
            term as PropertyExpressionTerm.Operation
            appendValue(actual, term.arguments[0], indentation)
            appendValue(expected, term.arguments[1], indentation)
            condition = "$actual ${term.operator} $expected"
            operands = term.arguments.mapIndexed { operandIndex, node ->
                val location = expressionLocations.getValue(node.id).last().start
                PropertyOperand(id = "operand-$operandIndex", source = location)
            }
        } else {
            appendValue(actual, check.expression, indentation)
            appendLine("${indentation}const $expected = true;")
            condition = actual
            val location = expressionLocations.getValue(check.expression.id).last().start
            operands = listOf(PropertyOperand(id = "condition", source = location))
        }
        val failure = if (diagnostic) {
            "{ passed: false, checkId: ${quote(check.name)}, actual: $actual, expected: $expected }"
        } else {
            "false"
        }
        val point = PropertySourcePoint(module = module, line = line, column = indentation.length + 1)
        record(check.expression, column = indentation.length + "if (!(".length + 1, length = condition.length)
        appendLine("${indentation}if (!($condition)) return $failure;")
        if (!diagnostic) {
            assertions += PropertyAssertion(id = check.name, source = point, operands = operands)
        } else {
            appendLine("  } catch (__usvmError) {")
            val identity = quote(check.name)
            appendLine("    return { passed: false, checkId: $identity, actual: undefined, expected: undefined,")
            appendLine("      exception: { value: __usvmError } };")
            appendLine("  }")
        }
    }

    private fun expression(node: PropertyExpressionNode, column: Int): String {
        val rendered = when (val term = node.term) {
            is PropertyExpressionTerm.Input -> term.name
            is PropertyExpressionTerm.Local -> term.name
            is PropertyExpressionTerm.Constant -> literal(term.value)
            is PropertyExpressionTerm.Call -> {
                val function = functions.getValue(term.function)
                var argumentColumn = column + function.length + 1
                val arguments = term.arguments.joinToString { argument ->
                    val text = expression(argument, argumentColumn)
                    argumentColumn += text.length + ", ".length
                    text
                }
                "$function($arguments)"
            }
            is PropertyExpressionTerm.Operation -> {
                if (term.arguments.size == 1) {
                    val argument = expression(term.arguments.single(), column + 1 + term.operator.length)
                    "(${term.operator}$argument)"
                } else {
                    val left = expression(term.arguments[0], column + 1)
                    val separator = " ${term.operator} "
                    val right = expression(term.arguments[1], column + 1 + left.length + separator.length)
                    "($left$separator$right)"
                }
            }
        }
        record(node, column, rendered.length)
        return rendered
    }

    private fun appendValue(name: String, node: PropertyExpressionNode, indentation: String = "  ") {
        val prefix = "${indentation}const $name = "
        val value = expression(node, prefix.length + 1)
        appendLine("$prefix$value;")
    }

    private fun record(node: PropertyExpressionNode, column: Int, length: Int) {
        val start = PropertySourcePoint(module = module, line = line, column = column)
        val range = GeneratedPropertyRange(start, endColumn = column + length)
        expressionLocations.getOrPut(node.id) { mutableListOf() }.add(range)
    }

    private fun literal(value: JsConcreteValue): String = when (value) {
        is JsConcreteValue.Number -> {
            val number = value.toDouble()
            when {
                number.isNaN() -> "NaN"
                number == Double.POSITIVE_INFINITY -> "Infinity"
                number == Double.NEGATIVE_INFINITY -> "-Infinity"
                else -> number.toString()
            }
        }
        is JsConcreteValue.Boolean -> value.value.toString()
        is JsConcreteValue.String -> quote(value.value)
        else -> error("Unsupported DSL literal")
    }

    private fun collectFunctions(node: PropertyExpressionNode) {
        when (val term = node.term) {
            is PropertyExpressionTerm.Call -> {
                functions.getOrPut(term.function) { "__usvmFunction${functions.size}" }
                term.arguments.forEach(::collectFunctions)
            }
            is PropertyExpressionTerm.Operation -> term.arguments.forEach(::collectFunctions)
            else -> Unit
        }
    }

    private fun allExpressions(): List<PropertyExpressionNode> =
        program.assumptions + program.locals.map { it.expression } + program.checks.map { it.expression }

    private fun programInputType(name: String): PropertyValueType {
        val input = program.inputs.single { it.name == name }
        return when (input.domain) {
            BooleanDomain -> PropertyValueType.BOOLEAN
            is StringDomain -> PropertyValueType.STRING
            is IntegerDomain, is NumberDomain -> PropertyValueType.NUMBER
            else -> error("Unsupported Kotlin DSL input domain: ${input.domain}")
        }
    }

    private fun appendLine(text: String) {
        source.appendLine(text)
        line++
    }

    private fun quote(text: String): String = Json.encodeToUtf8SafeString(text)
}
