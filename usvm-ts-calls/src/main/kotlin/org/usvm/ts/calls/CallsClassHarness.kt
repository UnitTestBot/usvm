package org.usvm.ts.calls

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import org.usvm.ts.pbt.model.ArrayDomain
import org.usvm.ts.pbt.model.BooleanDomain
import org.usvm.ts.pbt.model.ConstantDomain
import org.usvm.ts.pbt.model.IntegerDomain
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.NumberDomain
import org.usvm.ts.pbt.model.ObjectDomain
import org.usvm.ts.pbt.model.OptionalDomain
import org.usvm.ts.pbt.model.PropertyDomain
import org.usvm.ts.pbt.model.PropertyInput
import org.usvm.ts.pbt.model.StringDomain
import org.usvm.ts.pbt.model.TupleDomain
import org.usvm.ts.pbt.model.TypeScriptEntryPoint
import org.usvm.ts.pbt.model.encodeToUtf8SafeString
import java.nio.file.Files
import java.nio.file.Path

@Serializable
internal enum class CallsClassMemberKind { METHOD, GETTER, SETTER, FUNCTION_FIELD }

@Serializable
internal data class CallsClassMember(
    val id: String,
    val name: String,
    val kind: CallsClassMemberKind,
    val inputs: List<PropertyInput>,
    val resultDomain: PropertyDomain,
    val isStatic: Boolean = false,
    val resultClassName: String? = null,
)

/** A complete source-inventoried callable surface and receiver-state shape, frozen before comparing policies. */
@Serializable
internal data class CallsClassSurface(
    val sourceFile: String,
    val exportName: String,
    val constructorInputs: List<PropertyInput>,
    val members: List<CallsClassMember>,
    val stateFields: Map<String, PropertyDomain>,
) {
    init {
        CallsSymbolicCoverageClass(sourceFile = sourceFile, className = exportName)
        require(exportName.matches(CLASS_IDENTIFIER)) { "Class export must be an identifier" }
        require(members.map(CallsClassMember::id).distinct().size == members.size) { "Member IDs must be unique" }
        require(members.all { it.id.isNotBlank() && it.name.matches(CLASS_IDENTIFIER) }) {
            "Every callable member needs a stable ID and an identifier name"
        }
        require(stateFields.keys.all { it.matches(CLASS_IDENTIFIER) }) { "State fields must be identifiers" }
        for (member in members) {
            require(member.resultClassName == null || member.resultClassName == exportName) {
                "Only the selected concrete class can be an explicitly declared class result"
            }
            when (member.kind) {
                CallsClassMemberKind.GETTER -> require(member.inputs.isEmpty()) { "Getters have no inputs" }
                CallsClassMemberKind.SETTER -> require(member.inputs.size == 1) { "Setters have one input" }
                else -> Unit
            }
        }
        require(constructorInputs.map(PropertyInput::name).distinct().size == constructorInputs.size)
        require(callsSymbolicInputPreflight(constructorInputs) == null) { "Unsupported constructor input domain" }
        require(members.all { callsSymbolicInputPreflight(it.inputs) == null }) { "Unsupported callable input domain" }
        require(
            stateFields.values.all { it.isSupportedCallsSymbolicDomain() }
        ) { "Unsupported receiver observation domain" }
        require(
            members.all { it.resultDomain.isSupportedCallsSymbolicDomain() }
        ) { "Unsupported returned-value observation domain" }
    }
}

@Serializable
internal data class CallsClassScenario(val id: String, val memberIds: List<String>)

internal data class CallsClassPreparedHarness(
    val surface: CallsClassSurface,
    val scenarios: List<CallsClassScenario>,
    val sourceRoot: Path,
    val project: CallsProjectCase,
    val functions: List<CallsFunctionCase>,
    val observations: List<Map<String, PropertyDomain>>,
)

/** Each scenario constructs a fresh receiver; every invocation inside that scenario uses the same receiver. */
internal fun prepareCallsClassHarness(
    sourceRoot: Path,
    project: CallsProjectCase,
    surface: CallsClassSurface,
    maxSequenceLength: Int = 2,
): CallsClassPreparedHarness {
    require(maxSequenceLength in 1..2) { "Class scenarios support a frozen sequence bound of one or two" }
    val root = sourceRoot.toRealPath()
    require(Files.isRegularFile(root.resolve(surface.sourceFile))) { "Class source is missing" }
    val sequences = mutableListOf(emptyList<String>())
    sequences += surface.members.map { member -> listOf(member.id) }
    if (maxSequenceLength == 2) {
        sequences += surface.members.flatMap { first -> surface.members.map { second -> listOf(first.id, second.id) } }
    }
    val scenarios = sequences.mapIndexed { index, ids -> CallsClassScenario(id = "scenario-$index", memberIds = ids) }
    val fileName = ".usvm-calls-class-harness.ts"
    require(!Files.exists(root.resolve(fileName))) { "A generated class harness already exists in the overlay" }
    val source = StringBuilder()
    val moduleName = CallsExperimentJson.json.encodeToUtf8SafeString("./${surface.sourceFile}")
    source.appendLine("import { ${surface.exportName} as CallsTargetClass } from $moduleName;")
    val functions = mutableListOf<CallsFunctionCase>()
    val observations = mutableListOf<Map<String, PropertyDomain>>()
    for ((index, scenario) in scenarios.withIndex()) {
        val generated = ClassScenarioSource(surface, scenario, index).generate()
        source.appendLine(generated.source)
        functions += CallsFunctionCase(
            functionId = scenario.id,
            sourceFile = fileName,
            entryPoint = TypeScriptEntryPoint(module = fileName, exportName = generated.exportName),
            inputs = generated.inputs,
            targets = emptyList(),
            symbolicCoverageClass = CallsSymbolicCoverageClass(surface.sourceFile, surface.exportName),
        )
        observations += generated.observationFields
    }
    Files.writeString(root.resolve(fileName), source)

    return CallsClassPreparedHarness(
        surface = surface,
        scenarios = scenarios,
        sourceRoot = root,
        project = project.copy(functions = functions),
        functions = functions,
        observations = observations,
    )
}

@Serializable
internal data class CallsClassRawShapeCheck(
    val expression: String,
    val domain: PropertyDomain,
    val skipCallableProperties: Boolean,
    val allowTargetClass: Boolean = false,
    val callablePropertyNames: Set<String> = emptySet(),
)

private data class GeneratedClassScenario(
    val source: String,
    val exportName: String,
    val inputs: List<PropertyInput>,
    val observationFields: Map<String, PropertyDomain>,
)

private class ClassScenarioSource(
    private val surface: CallsClassSurface,
    private val scenario: CallsClassScenario,
    private val index: Int,
) {
    private val inputs = mutableListOf<PropertyInput>()
    private val fields = linkedMapOf<String, PropertyDomain>()
    private val declarations = StringBuilder()
    private var snapshotIndex = 0

    fun generate(): GeneratedClassScenario {
        val constructorArgs = addInputs(surface.constructorInputs)
        declarations.appendLine("  const receiver = new CallsTargetClass($constructorArgs);")
        observeState(step = 0)
        val membersById = surface.members.associateBy(CallsClassMember::id)
        scenario.memberIds.forEachIndexed { callIndex, id ->
            val member = membersById.getValue(id)
            val args = addInputs(member.inputs)
            val owner = if (member.isStatic) "CallsTargetClass" else "receiver"
            val access = "$owner.${member.name}"
            val expression = when (member.kind) {
                CallsClassMemberKind.GETTER -> { access }
                CallsClassMemberKind.SETTER -> {
                    declarations.appendLine("  $access = $args;")
                    "undefined"
                }
                CallsClassMemberKind.METHOD, CallsClassMemberKind.FUNCTION_FIELD -> { "$access($args)" }
            }
            declarations.appendLine("  const value_result_$callIndex = $expression;")
            observe(
                name = "result_$callIndex",
                expression = "value_result_$callIndex",
                domain = member.resultDomain,
                allowTargetClass = member.resultClassName == surface.exportName,
            )
            observeState(step = callIndex + 1)
        }
        val observationType = fields.entries.joinToString(separator = "; ") { (name, domain) ->
            "$name: ${domain.classTypeScriptType()}"
        }
        val parameters = inputs.joinToString { input -> "${input.name}: ${input.domain.classTypeScriptType()}" }
        val exportName = "callsClassScenario$index"
        val source = """
            interface CallsObservation$index { $observationType }
            export function $exportName($parameters): CallsObservation$index {
            $declarations
              return { ${fields.keys.joinToString()} };
            }
        """.trimIndent()

        return GeneratedClassScenario(source, exportName, inputs.toList(), fields.toMap())
    }

    private fun addInputs(declared: List<PropertyInput>): String = declared.joinToString { input ->
        val name = "input_${inputs.size}"
        inputs += input.copy(name = name)
        name
    }

    private fun observeState(step: Int) {
        rawShapeCheck(
            expression = "receiver",
            domain = ObjectDomain(surface.stateFields),
            skipCallableProperties = true,
            allowTargetClass = true,
        )
        surface.stateFields.forEach { (name, domain) ->
            val type = domain.classTypeScriptType()
            observe(
                name = "state_${step}_$name",
                expression = "(receiver as unknown as { $name: $type }).$name",
                domain = domain,
            )
        }
    }

    private fun observe(
        name: String,
        expression: String,
        domain: PropertyDomain,
        allowTargetClass: Boolean = false,
    ) {
        rawShapeCheck(
            expression = expression,
            domain = domain,
            skipCallableProperties = true,
            allowTargetClass = allowTargetClass,
        )
        val snapshot = snapshot(expression, domain)
        declarations.appendLine("  const $name = $snapshot;")
        fields[name] = domain
    }

    private fun rawShapeCheck(
        expression: String,
        domain: PropertyDomain,
        skipCallableProperties: Boolean,
        allowTargetClass: Boolean = false,
    ) {
        val callablePropertyNames = surface.members.filter { member ->
            member.kind == CallsClassMemberKind.FUNCTION_FIELD && !member.isStatic
        }.mapTo(linkedSetOf()) { it.name }
        val check = CallsClassRawShapeCheck(
            expression = expression,
            domain = domain,
            skipCallableProperties = skipCallableProperties,
            allowTargetClass = allowTargetClass,
            callablePropertyNames = callablePropertyNames,
        )
        val encoded = CallsExperimentJson.json.encodeToString(check)
        declarations.appendLine("  // __usvm_class_observe $encoded")
    }

    private fun snapshot(expression: String, domain: PropertyDomain): String = when (domain) {
        is ObjectDomain -> {
            domain.fields.entries.joinToString(prefix = "{ ", postfix = " }") { (name, nested) ->
                require(name.matches(CLASS_IDENTIFIER)) { "Observed object fields must be identifiers" }
                "$name: ${snapshot("($expression).$name", nested)}"
            }
        }
        is OptionalDomain -> {
            val nil = if (domain.nil == JsConcreteValue.Null) "null" else "undefined"
            val temporary = "snapshot_${snapshotIndex++}"
            val original = "snapshot_${snapshotIndex++}"
            declarations.appendLine("  const $original = $expression;")
            declarations.appendLine("  let $temporary: ${domain.classTypeScriptType()};")
            declarations.appendLine("  if ($original === $nil) {")
            declarations.appendLine("    $temporary = $nil;")
            declarations.appendLine("  } else {")
            val nested = snapshot(original, domain.value)
            declarations.appendLine("    $temporary = $nested;")
            declarations.appendLine("  }")
            temporary
        }
        is ArrayDomain -> {
            val temporary = "snapshot_${snapshotIndex++}"
            val variable = "snapshot_index_${snapshotIndex++}"
            declarations.appendLine("  const $temporary: ${domain.classTypeScriptType()} = [];")
            declarations.appendLine("  for (let $variable = 0; $variable < ($expression).length; $variable++) {")
            val element = snapshot("($expression)[$variable]", domain.element)
            declarations.appendLine("    $temporary[$variable] = $element;")
            declarations.appendLine("  }")
            temporary
        }
        is TupleDomain -> { error("Tuple class observations are unsupported") }
        else -> { expression }
    }
}

internal fun PropertyDomain.classTypeScriptType(): String = when (this) {
    BooleanDomain -> "boolean"
    is IntegerDomain, is NumberDomain -> "number"
    is StringDomain -> "string"
    is ConstantDomain -> when (value) {
        JsConcreteValue.Null -> "null"
        JsConcreteValue.Undefined -> "undefined"
        is JsConcreteValue.Boolean -> "boolean"
        is JsConcreteValue.Number -> "number"
        is JsConcreteValue.String -> "string"
        else -> error("Structured constants are unsupported class domains")
    }
    is OptionalDomain -> "(${value.classTypeScriptType()} | ${ConstantDomain(nil).classTypeScriptType()})"
    is ArrayDomain -> "(${element.classTypeScriptType()})[]"
    is TupleDomain -> elements.joinToString(prefix = "[", postfix = "]") { it.classTypeScriptType() }
    is ObjectDomain -> fields.entries.joinToString(prefix = "{ ", postfix = " }") { (name, domain) ->
        val key = CallsExperimentJson.json.encodeToUtf8SafeString(name)
        "$key: ${domain.classTypeScriptType()}"
    }
}

private val CLASS_IDENTIFIER = Regex("[A-Za-z_$][A-Za-z0-9_$]*")
