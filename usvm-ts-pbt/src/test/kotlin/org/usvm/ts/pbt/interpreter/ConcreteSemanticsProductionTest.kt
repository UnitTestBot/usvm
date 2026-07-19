package org.usvm.ts.pbt.interpreter

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jacodb.ets.model.EtsIfStmt
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsStmt
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.usvm.ts.pbt.external.ExternalCallableReference
import org.usvm.ts.pbt.external.ExternalConstructorPlan
import org.usvm.ts.pbt.external.ExternalProperty
import org.usvm.ts.pbt.external.ExternalTestCase
import org.usvm.ts.pbt.external.ExternalValue
import org.usvm.ts.pbt.external.ExternalValueCodec
import org.usvm.ts.pbt.external.stableMethodId
import org.usvm.ts.pbt.replay.EtsIrReplayArguments
import org.usvm.ts.pbt.replay.EtsIrReplayCaseExecutor
import org.usvm.ts.pbt.replay.EtsIrReplayValueDecoder
import org.usvm.ts.pbt.replay.ReplayCaseExecution
import org.usvm.ts.pbt.replay.ReplayInputRejectionException
import org.usvm.ts.pbt.replay.ReplayReasonCode
import org.usvm.ts.pbt.replay.ReplayRuntime
import org.usvm.ts.pbt.util.getResourcePath
import java.security.MessageDigest

@EnabledIfEnvironmentVariable(named = "ETS_FRONTEND_DIR", matches = ".+")
class ConcreteSemanticsProductionTest {
    private fun jsonResource(path: String): JsonObject =
        Json.parseToJsonElement(getResourcePath(path).toFile().readText()).jsonObject

    private fun JsonObject.string(name: String): String = getValue(name).jsonPrimitive.content

    private fun JsonObject.int(name: String): Int = getValue(name).jsonPrimitive.int

    private fun scene(vararg resources: String): EtsScene = EtsScene(
        resources.map { loadEtsFileAutoConvert(getResourcePath(it)) },
    )

    private fun method(scene: EtsScene, name: String): EtsMethod = scene.projectAndSdkClasses
        .flatMap { it.methods }
        .filter { it.name == name && it.cfg.stmts.isNotEmpty() }
        .single()

    private fun execute(scene: EtsScene, name: String, vararg args: VValue): ExecutionResult =
        EtsConcreteInterpreter(scene).execute(method(scene, name), args = args.toList())

    private fun returned(scene: EtsScene, name: String, vararg args: VValue): VValue {
        val result = execute(scene, name, *args)
        assertTrue(result is ExecutionResult.Returned, "$name returned $result")
        return (result as ExecutionResult.Returned).value
    }

    @Test
    fun `frozen builtin fixture executes through the concrete EtsIR interpreter`() {
        val scene = scene("/semantics/builtins/BuiltinSemanticsFixture.ts")
        assertEquals(VBool(true), returned(scene, "arrayIsArray", VArray()))
        assertEquals(VBool(false), returned(scene, "arrayIsArray", VObject(null)))
        assertEquals(VString("[object Array]"), returned(scene, "objectToStringTag", VArray()))
        assertEquals(VString("[object Map]"), returned(scene, "objectToStringTag", VMap()))

        val inherited = VObject(null, mutableMapOf("inherited" to VNumber(1.0)))
        val subject = VObject(
            cls = null,
            fields = mutableMapOf("own" to VUndefined),
            prototype = inherited,
        )
        assertEquals(VBool(true), returned(scene, "objectHasOwn", subject, VString("own")))
        assertEquals(VBool(false), returned(scene, "objectHasOwn", subject, VString("inherited")))
        assertTrue(execute(scene, "objectHasOwn", VNull, VString("missing")) is ExecutionResult.Threw)
        assertEquals(VBool(true), returned(scene, "propertyIn", subject, VString("own")))
        assertEquals(VBool(true), returned(scene, "propertyIn", subject, VString("inherited")))
        assertEquals(VBool(false), returned(scene, "propertyIn", subject, VString("missing")))
        assertTrue(execute(scene, "propertyIn", VString("abc"), VString("length")) is ExecutionResult.Threw)

        val map = VMap()
        assertSame(map, returned(scene, "mapSet", map, VNumber(Double.NaN), VUndefined))
        assertEquals(VBool(true), returned(scene, "mapHas", map, VNumber(Double.NaN)))
        assertEquals(VUndefined, returned(scene, "mapGet", map, VNumber(Double.NaN)))
        assertEquals(VBool(false), returned(scene, "mapHas", map, VString("missing")))
        assertEquals(VNumber(1.0), returned(scene, "mapSize", map))
        assertEquals(VBool(false), returned(scene, "objectHasOwn", map, VString("size")))
        assertEquals(VBool(true), returned(scene, "propertyIn", map, VString("size")))

        val signedZeroMap = VMap(
            linkedMapOf(
                VNumber(-0.0) to VString("first"),
                VNumber(0.0) to VString("second"),
            ),
        )
        assertEquals(1, signedZeroMap.entries.size)
        assertEquals(VString("second"), signedZeroMap.entries.values.single())
        val signedZeroSet = VSet(linkedSetOf(VNumber(-0.0), VNumber(0.0)))
        assertEquals(1, signedZeroSet.elements.size)
        signedZeroMap.entries[VNumber(-0.0)] = VString("third")
        signedZeroMap.entries[VNumber(0.0)] = VString("fourth")
        assertEquals(1, signedZeroMap.entries.size)
        assertEquals(VString("fourth"), signedZeroMap.entries[VNumber(-0.0)])
        signedZeroSet.elements.add(VNumber(-0.0))
        signedZeroSet.elements.add(VNumber(0.0))
        assertEquals(1, signedZeroSet.elements.size)
        assertEquals(VBool(false), returned(scene, "truthy", VNumber(Double.NaN)))
        assertEquals(VBool(true), returned(scene, "truthy", VObject(null)))

        assertEquals(VBool(true), returned(scene, "flattenReduceArrayDecision", VArray()))
        assertEquals(VBool(false), returned(scene, "flattenReduceArrayDecision", VNumber(1.0)))
        assertEquals(VBool(true), returned(scene, "factorizeTailDecision", VMap(), VNumber(2.0)))
    }

    @Test
    fun `module callable materialization dispatch receiver recursion and arity are production exact`() {
        val commonJsScene = scene(
            "/semantics/callable/CallableSemanticsLibrary.ts",
            "/semantics/callable/CallableSemanticsFixture.ts",
        )
        val commonJsMaterializer = ConcreteSceneMaterializer(commonJsScene)
        val commonJsModule = "semantics/callable/CallableSemanticsFixture.ts"
        val commonJsLibrary = "semantics/callable/CallableSemanticsLibrary.ts"
        fun runCommon(fn: VFunction, vararg args: VValue): VValue {
            val result = EtsConcreteInterpreter(commonJsScene).execute(fn.method, fn.thisValue, args.toList())
            assertTrue(result is ExecutionResult.Returned, result.toString())
            return (result as ExecutionResult.Returned).value
        }
        assertEquals(
            VNumber(5.0),
            runCommon(
                commonJsMaterializer.materializeCallable(commonJsModule, "directAdd", "function"),
                VNumber(2.0),
                VNumber(3.0),
            ),
        )
        assertEquals(
            VNumber(8.0),
            runCommon(
                commonJsMaterializer.materializeCallable(commonJsModule, "topLevelArrow", "arrow"),
                VNumber(4.0),
            ),
        )
        assertEquals(
            VNumber(42.0),
            runCommon(
                commonJsMaterializer.materializeCallable(commonJsLibrary, "importedOffset", "function"),
                VNumber(2.0),
            ),
        )
        val frozenPrivate = assertThrows(ConcreteMaterializationException::class.java) {
            commonJsMaterializer.materializeCallable(commonJsModule, "notExported", "function")
        }
        assertEquals("unresolved_callable_reference", frozenPrivate.reasonCode)
        listOf(
            "/interpreter/CommonJsNonFreezeFixture.ts" to "directAdd",
            "/interpreter/CommonJsDuplicateExportFixture.ts" to "second",
            "/interpreter/CommonJsShadowedObjectFixture.ts" to "directAdd",
            "/interpreter/CommonJsConstructedExportFixture.ts" to "directAdd",
            "/interpreter/CommonJsPostFreezeWriteFixture.ts" to "directAdd",
        ).forEach { (resource, exportName) ->
            val rejectedScene = scene(resource)
            val rejectedMaterializer = ConcreteSceneMaterializer(rejectedScene)
            val rejected = assertThrows(ConcreteMaterializationException::class.java) {
                rejectedMaterializer.materializeCallable(resource.removePrefix("/"), exportName, "function")
            }
            assertEquals("unresolved_callable_reference", rejected.reasonCode)
        }
        val scene = scene(
            "/interpreter/ConcreteModuleUtil.ts",
            "/interpreter/ConcreteSemanticProductionFixture.ts",
        )
        val interpreter = EtsConcreteInterpreter(scene)
        val materializer = ConcreteSceneMaterializer(scene)
        val resolver = CallResolver(scene)
        val module = "interpreter/ConcreteSemanticProductionFixture.ts"
        val util = "interpreter/ConcreteModuleUtil.ts"

        val importingMethod = method(scene, "moduleDefaultEquals")
        val namespaceLocal = importingMethod.locals.single { it.name == "util" }
        assertEquals(
            "./ConcreteModuleUtil",
            resolver.modulePathOf(namespaceLocal, importingMethod.signature.enclosingClass.file.fileName),
        )
        val literalMethod = method(scene, "literalTypedParameter")
        val literalLocal = literalMethod.locals.single { it.name == "value" }
        assertEquals(
            null,
            resolver.modulePathOf(literalLocal, literalMethod.signature.enclosingClass.file.fileName),
        )

        fun runCallable(fn: VFunction, vararg args: VValue): VValue {
            val result = interpreter.execute(fn.method, fn.thisValue, args.toList())
            assertTrue(result is ExecutionResult.Returned, result.toString())
            return (result as ExecutionResult.Returned).value
        }

        val direct = materializer.materializeCallable(module, "directAdd", "function")
        assertEquals(VNumber(5.0), runCallable(direct, VNumber(2.0), VNumber(3.0)))
        val arrow = materializer.materializeCallable(module, "topLevelArrow", "arrow")
        assertEquals(VNumber(42.0), runCallable(arrow, VNumber(21.0)))
        val imported = materializer.materializeCallable(util, "importedMultiply", "arrow")
        assertEquals(VNumber(42.0), runCallable(imported, VNumber(6.0), VNumber(7.0)))

        val invokeDirect = method(scene, "invokeDirect")
        assertEquals(
            ExecutionResult.Returned(VNumber(5.0)),
            interpreter.execute(invokeDirect, args = listOf(direct, VNumber(2.0), VNumber(3.0))),
        )
        val fieldReceiver = VObject(
            null,
            mutableMapOf(
                "operation" to materializer.materializeCallable(module, "fieldMultiply", "function"),
            ),
        )
        assertEquals(VNumber(42.0), returned(scene, "invokeField", fieldReceiver, VNumber(6.0), VNumber(7.0)))

        val receiverBoundField = VObject(
            cls = null,
            fields = mutableMapOf(
                "base" to VNumber(37.0),
                "operation" to materializer.materializeCallable(module, "readBase", "function"),
            ),
        )
        assertEquals(
            VNumber(42.0),
            returned(scene, "invokeUnaryField", receiverBoundField, VNumber(5.0)),
        )
        assertTrue(
            execute(scene, "extractAndInvoke", receiverBoundField, VNumber(5.0)) is ExecutionResult.Threw,
        )
        assertTrue(
            execute(scene, "extractComputedAndInvoke", receiverBoundField, VNumber(5.0)) is ExecutionResult.Threw,
        )
        assertEquals(
            VNumber(42.0),
            returned(scene, "invokeComputedUnaryField", receiverBoundField, VNumber(5.0)),
        )
        val lexicalReceiver = VObject(null, mutableMapOf("base" to VNumber(10.0)))
        receiverBoundField.fields["operation"] = VFunction(
            method(scene, "readBase"),
            lexicalReceiver,
            VFunctionThisMode.LEXICAL,
        )
        assertEquals(
            VNumber(15.0),
            returned(scene, "invokeUnaryField", receiverBoundField, VNumber(5.0)),
        )

        val readBase = materializer.materializeCallable(module, "readBase", "function")
        val explicitReceiver = VObject(null, mutableMapOf("base" to VNumber(38.0)))
        assertEquals(
            VNumber(42.0),
            returned(scene, "invokeWithCall", readBase, explicitReceiver, VNumber(4.0)),
        )
        assertEquals(VNumber(720.0), returned(scene, "recursiveFactorial", VNumber(6.0)))
        assertEquals(VNumber(140.0), returned(scene, "arityLength", VNumber(4.0)))
        assertEquals(
            VNumber(345.0),
            returned(scene, "arityLength", VNumber(4.0), VNumber(5.0), VNumber(99.0)),
        )

        val box = materializer.construct(module, "ReceiverBox", listOf(VNumber(37.0)))
        val add = materializer.materializeCallable(
            module,
            "ReceiverBox.prototype.add",
            "instanceMethod",
        )
        assertEquals(VFunctionThisMode.DYNAMIC, add.thisMode)
        assertTrue(interpreter.execute(add.method, add.thisValue, listOf(VNumber(5.0))) is ExecutionResult.Threw)
        assertEquals(
            ExecutionResult.Returned(VNumber(42.0)),
            interpreter.execute(add.method, box, listOf(VNumber(5.0))),
        )
        assertEquals(VBool(true), returned(scene, "instanceMethodIdentity", box))
        val descriptorReceiver = VObject(
            null,
            mutableMapOf("base" to VNumber(37.0), "operation" to add),
        )
        assertEquals(VNumber(42.0), returned(scene, "invokeUnaryField", descriptorReceiver, VNumber(5.0)))
        box.fields["add"] = VUndefined
        assertTrue(execute(scene, "invokeBoxAdd", box, VNumber(5.0)) is ExecutionResult.Threw)
        box.fields["add"] = materializer.materializeCallable(module, "readBase", "function")
        assertEquals(VNumber(42.0), returned(scene, "invokeBoxAdd", box, VNumber(5.0)))
        val overriddenBox = materializer.construct(
            module,
            "ReceiverBox",
            listOf(VNumber(37.0)),
            properties = mapOf("base" to VNumber(40.0)),
        )
        val overriddenAdd = materializer.materializeCallable(
            module,
            "ReceiverBox.prototype.add",
            "instanceMethod",
        )
        assertEquals(
            ExecutionResult.Returned(VNumber(42.0)),
            interpreter.execute(overriddenAdd.method, overriddenBox, listOf(VNumber(2.0))),
        )
        val staticSum = materializer.materializeCallable(module, "ReceiverBox.staticSum", "staticMethod")
        assertEquals(VNumber(42.0), runCallable(staticSum, VNumber(19.0), VNumber(23.0)))
        val dualScene = scene("/interpreter/ConcreteDualDispatchFixture.ts")
        val dual = ConcreteSceneMaterializer(dualScene).construct(
            "interpreter/ConcreteDualDispatchFixture.ts",
            "DualDispatch",
            emptyList(),
        )
        assertEquals(VNumber(103.0), returned(dualScene, "invokeDualInstance", dual, VNumber(3.0)))
        assertEquals(VNumber(203.0), returned(dualScene, "invokeDualStatic", VNumber(3.0)))
        assertEquals(VNumber(203.0), returned(dualScene, "invokeDualStaticWithShadow", VNumber(3.0)))
        assertEquals(VNumber(303.0), returned(dualScene, "invokeStaticNamedLikeConversion", VNumber(3.0)))

        assertEquals(VBool(true), returned(scene, "moduleDefaultEquals", VNumber(4.0), VNumber(4.0)))
        assertEquals(VBool(false), returned(scene, "moduleDefaultEquals", VNumber(4.0), VNumber(5.0)))
        assertEquals(VBool(true), returned(scene, "moduleExportIdentity"))
        assertEquals(
            VUndefined,
            returned(
                scene,
                "readInherited",
                VObject(
                    cls = null,
                    fields = mutableMapOf("inherited" to VUndefined),
                    prototype = VObject(null, mutableMapOf("inherited" to VNumber(42.0))),
                ),
            ),
        )
        assertEquals(
            VNumber(3.0),
            returned(
                scene,
                "spliceWithoutArguments",
                VArray(mutableListOf(VNumber(1.0), VNumber(2.0), VNumber(3.0))),
            ),
        )
        assertEquals(
            VNumber(201.0),
            returned(
                scene,
                "spliceWithOnlyStart",
                VArray(mutableListOf(VNumber(1.0), VNumber(2.0), VNumber(3.0))),
                VNumber(1.0),
            ),
        )
        listOf("freezeObject", "sealObject", "preventObjectExtensions").forEach { methodName ->
            val result = execute(scene, methodName, VObject(null, mutableMapOf("value" to VNumber(1.0))))
            assertTrue(result is ExecutionResult.Unsupported, "$methodName returned $result")
        }
        assertEquals(VUndefined, materializer.materializeModuleExport(util, "explicitlyAbsent"))
        val unresolvedValueExport = assertThrows(ConcreteMaterializationException::class.java) {
            materializer.materializeModuleExport(util, "exportedNumber")
        }
        assertEquals("unresolved_module_export", unresolvedValueExport.reasonCode)
        val rejected = assertThrows(ConcreteMaterializationException::class.java) {
            materializer.materializeCallable(module, "asyncIdentity", "async")
        }
        assertEquals("callable_kind_not_exact", rejected.reasonCode)
        val misclassifiedArrow = assertThrows(ConcreteMaterializationException::class.java) {
            materializer.materializeCallable(module, "directAdd", "arrow")
        }
        assertEquals("unresolved_callable_reference", misclassifiedArrow.reasonCode)
        val misclassifiedFunction = assertThrows(ConcreteMaterializationException::class.java) {
            materializer.materializeCallable(module, "topLevelArrow", "function")
        }
        assertEquals("unresolved_callable_reference", misclassifiedFunction.reasonCode)
        val instanceAsStatic = assertThrows(ConcreteMaterializationException::class.java) {
            materializer.materializeCallable(module, "ReceiverBox.add", "staticMethod")
        }
        assertEquals("unresolved_callable_reference", instanceAsStatic.reasonCode)
        val staticAsInstance = assertThrows(ConcreteMaterializationException::class.java) {
            materializer.materializeCallable(module, "ReceiverBox.prototype.staticSum", "instanceMethod")
        }
        assertEquals("unresolved_callable_reference", staticAsInstance.reasonCode)
        val privateBinding = assertThrows(ConcreteMaterializationException::class.java) {
            materializer.materializeCallable(module, "notExported", "function")
        }
        assertEquals("unresolved_callable_reference", privateBinding.reasonCode)

        val ambiguousScene = scene("/interpreter/ConcreteAmbiguousCallable.ts")
        val ambiguous = assertThrows(ConcreteMaterializationException::class.java) {
            ConcreteSceneMaterializer(ambiguousScene).materializeCallable(
                "interpreter/ConcreteAmbiguousCallable.ts",
                "reassigned",
                "arrow",
            )
        }
        assertEquals("unresolved_callable_reference", ambiguous.reasonCode)

        val barrelScene = scene(
            "/interpreter/ConcreteReExportSource.ts",
            "/interpreter/ConcreteReExportBarrel.ts",
        )
        val unavailableIndex = assertThrows(ConcreteMaterializationException::class.java) {
            ConcreteSceneMaterializer(barrelScene).materializeModuleExport(
                "interpreter/ConcreteReExportBarrel.ts",
                "notPresent",
            )
        }
        assertEquals("module_export_index_unavailable", unavailableIndex.reasonCode)
    }

    @Test
    fun `array string map and set iterators execute exact native protocol`() {
        val scene = scene(
            "/interpreter/ConcreteModuleUtil.ts",
            "/interpreter/ConcreteSemanticProductionFixture.ts",
        )
        assertEquals(
            VNumber(6.0),
            returned(scene, "iteratorSum", VArray(mutableListOf(VNumber(1.0), VNumber(2.0), VNumber(3.0)))),
        )
        assertEquals(VBool(true), returned(scene, "iteratorSelf", VArray(mutableListOf(VNumber(1.0)))))
        assertEquals(
            VBool(true),
            returned(scene, "iteratorReturnIsAbsent", VArray(mutableListOf(VNumber(1.0)))),
        )
        assertEquals(
            VBool(true),
            returned(scene, "iteratorFunctionsAreObservable", VArray(mutableListOf(VNumber(1.0)))),
        )
        assertEquals(
            VBool(true),
            returned(scene, "iteratorFunctionIdentity", VArray(mutableListOf(VNumber(1.0)))),
        )
        assertEquals(
            VNumber(42.0),
            returned(scene, "arrayIteratorObservesAppend", VArray(mutableListOf(VNumber(1.0)))),
        )
        assertEquals(
            VNumber(42.0),
            returned(
                scene,
                "mapIteratorObservesOverwrite",
                VMap(linkedMapOf(VNumber(1.0) to VNumber(10.0))),
            ),
        )
        assertEquals(
            VNumber(42.0),
            returned(scene, "setIteratorObservesAppend", VSet(linkedSetOf(VNumber(1.0)))),
        )
        assertEquals(
            VBool(true),
            returned(scene, "arrayIteratorDoneIsSticky", VArray(mutableListOf(VNumber(1.0)))),
        )
        assertEquals(
            VBool(true),
            returned(
                scene,
                "mapIteratorDoneIsSticky",
                VMap(linkedMapOf(VNumber(1.0) to VNumber(10.0))),
            ),
        )
        assertEquals(
            VBool(true),
            returned(scene, "setIteratorDoneIsSticky", VSet(linkedSetOf(VNumber(1.0)))),
        )
        val customIterable = VObject(
            cls = null,
            fields = mutableMapOf(
                "values" to VArray(mutableListOf(VNumber(42.0))),
                "Symbol.iterator" to VFunction(method(scene, "iteratorStoredInField")),
            ),
        )
        assertEquals(VNumber(42.0), returned(scene, "customIterableFirst", customIterable))
        assertEquals(
            VNumber(3.0),
            returned(
                scene,
                "mapKeySum",
                VMap(linkedMapOf(VNumber(1.0) to VNumber(10.0), VNumber(2.0) to VNumber(20.0))),
            ),
        )
        assertEquals(
            VNumber(33.0),
            returned(
                scene,
                "mapDefaultEntrySum",
                VMap(linkedMapOf(VNumber(1.0) to VNumber(10.0), VNumber(2.0) to VNumber(20.0))),
            ),
        )
        assertEquals(
            VNumber(3.0),
            returned(scene, "setValueSum", VSet(linkedSetOf(VNumber(1.0), VNumber(2.0)))),
        )
        assertEquals(
            VNumber(3.0),
            returned(scene, "setDefaultValueSum", VSet(linkedSetOf(VNumber(1.0), VNumber(2.0)))),
        )
    }

    @Test
    fun `scene materializer feeds an injected EtsIR replay decoder for ETC v2 values`() {
        val scene = scene(
            "/interpreter/ConcreteModuleUtil.ts",
            "/interpreter/ConcreteSemanticProductionFixture.ts",
        )
        val materializer = ConcreteSceneMaterializer(scene)
        val module = "interpreter/ConcreteSemanticProductionFixture.ts"
        val decoder = object : EtsIrReplayValueDecoder {
            override fun decode(case: ExternalTestCase, scene: EtsScene): EtsIrReplayArguments {
                fun rejected(message: String): Nothing = throw ReplayInputRejectionException(
                    ReplayReasonCode.INPUT_UNREPRESENTABLE,
                    message,
                )

                fun value(external: ExternalValue): VValue = when (external.kind) {
                    "callable" -> {
                        val reference = external.callableReference ?: rejected("callable reference is absent")
                        materializer.materializeCallable(
                            reference.modulePath,
                            reference.exportName,
                            reference.callableKind,
                        )
                    }

                    "object" -> {
                        val properties = external.properties.associate { it.key to value(it.value) }
                        external.constructorPlan?.let { plan ->
                            materializer.construct(
                                plan.callable.modulePath,
                                plan.callable.exportName,
                                plan.arguments.map(::value),
                                properties,
                            )
                        } ?: VObject(null, properties.toMutableMap())
                    }

                    "array" -> {
                        if (external.elements.any { it.kind == "hole" }) {
                            rejected("sparse array needs an exact hole model")
                        }
                        VArray(external.elements.map(::value).toMutableList())
                    }

                    "alias", "hole" -> {
                        rejected("${external.kind} requires identity-aware decoding")
                    }

                    else -> {
                        ExternalValueCodec.toVValue(external)
                    }
                }.also { decoded ->
                    if (external.aliasId != null || external.aliasReference != null) {
                        rejected("aliases are rejected unless the identity table owns the whole case")
                    }
                    check(decoded !== VUndefined || external.kind == "undefined")
                }

                return try {
                    EtsIrReplayArguments(value(case.receiver), case.arguments.map(::value))
                } catch (cause: ConcreteMaterializationException) {
                    rejected("${cause.reasonCode}: ${cause.message}")
                }
            }
        }
        val allMethods = scene.projectAndSdkClasses.flatMap { it.methods }.filter { it.cfg.stmts.isNotEmpty() }
        val runtime = ReplayRuntime(EtsIrReplayCaseExecutor(scene, allMethods, decoder))

        fun callable(exportName: String, kind: String = "function") = ExternalValue(
            kind = "callable",
            callableReference = ExternalCallableReference(module, exportName, kind),
        )
        fun number(value: Int) = ExternalValue(kind = "number", value = value.toString())
        fun returned(case: ExternalTestCase) {
            val result = runtime.executor.execute(case)
            assertTrue(result is ReplayCaseExecution.Executed, result.toString())
            assertEquals(ReplayReasonCode.REPLAY_RETURNED, (result as ReplayCaseExecution.Executed).reasonCode)
        }

        returned(
            ExternalTestCase(
                id = "replay-direct-callable",
                methodId = stableMethodId(method(scene, "invokeDirect")),
                path = "concrete-semantics:direct",
                arguments = listOf(callable("directAdd"), number(2), number(3)),
            ),
        )
        returned(
            ExternalTestCase(
                id = "replay-unbound-instance-descriptor",
                methodId = stableMethodId(method(scene, "invokeUnaryField")),
                path = "concrete-semantics:instance-descriptor",
                arguments = listOf(
                    ExternalValue(
                        kind = "object",
                        properties = listOf(
                            ExternalProperty("base", number(37)),
                            ExternalProperty(
                                "operation",
                                callable("ReceiverBox.prototype.add", "instanceMethod"),
                            ),
                        ),
                    ),
                    number(5),
                ),
            ),
        )
        returned(
            ExternalTestCase(
                id = "replay-constructor-instance",
                methodId = stableMethodId(method(scene, "add")),
                path = "concrete-semantics:constructor",
                receiver = ExternalValue(
                    kind = "object",
                    className = "ReceiverBox",
                    constructorPlan = ExternalConstructorPlan(
                        callable = ExternalCallableReference(module, "ReceiverBox", "class"),
                        arguments = listOf(number(37)),
                    ),
                ),
                arguments = listOf(number(5)),
            ),
        )

        fun assertUnrepresentable(id: String, value: ExternalValue) {
            val rejected = runtime.executor.execute(
                ExternalTestCase(
                    id = id,
                    methodId = stableMethodId(method(scene, "directAdd")),
                    path = "concrete-semantics:$id",
                    arguments = listOf(value, number(1)),
                ),
            )
            assertTrue(rejected is ReplayCaseExecution.Rejected, rejected.toString())
            assertEquals(
                ReplayReasonCode.INPUT_UNREPRESENTABLE,
                (rejected as ReplayCaseExecution.Rejected).reasonCode,
            )
        }

        assertUnrepresentable(
            "replay-alias-reject",
            ExternalValue(kind = "alias", aliasReference = "missing"),
        )
        assertUnrepresentable(
            "replay-sparse-reject",
            ExternalValue(kind = "array", elements = listOf(ExternalValue(kind = "hole"))),
        )
    }

    @Test
    fun `machine readable production evidence is aligned with all frozen semantic partitions`() {
        val evidence = jsonResource("/interpreter/concrete-semantics-production-v1.json")
        val partitions = evidence.getValue("semanticPartitions").jsonObject

        fun evidenceTargets(name: String): List<Pair<String, String>> =
            partitions.getValue(name).jsonArray.map(JsonElement::jsonObject).map { target ->
                target.string("id") to target.string("branchId")
            }

        fun frozenTargets(path: String, collection: String): List<Pair<String, String>> =
            jsonResource(path).getValue(collection).jsonArray.map(JsonElement::jsonObject).map { target ->
                target.string("id") to target.getValue("etsIr").jsonObject.string("branchId")
            }

        assertEquals(
            frozenTargets("/semantics/callable/callable-semantics-v1.json", "residualBlockers"),
            evidenceTargets("moduleCallable"),
        )
        assertEquals(
            frozenTargets("/semantics/iterator/iterator-semantics-v1.json", "realTargets"),
            evidenceTargets("iterator"),
        )
        assertEquals(
            frozenTargets("/semantics/builtins/builtin-semantics-v1.json", "residualBlockers"),
            evidenceTargets("builtins"),
        )
        assertEquals(11, evidenceTargets("moduleCallable").size)
        assertEquals(9, evidenceTargets("iterator").size)
        assertEquals(3, evidenceTargets("builtins").size)

        val acceptance = evidence.getValue("acceptancePartition").jsonArray.map { it.jsonPrimitive.content }
        assertEquals(14, acceptance.size)
        assertEquals(acceptance.size, acceptance.distinct().size)

        val campaign = evidence.getValue("realProjectCampaign").jsonObject
        val historical = campaign.getValue("historical").jsonObject
        val current = campaign.getValue("current").jsonObject
        assertEquals(historical.int("totalBranches"), current.int("totalBranches"))
        assertTrue(current.int("coveredBranches") >= historical.int("coveredBranches"))
        assertEquals(500, current.int("defaultComparatorFamilyExecutions"))
        assertEquals(0, current.int("defaultEqualsFailures"))
        assertEquals(0, current.int("defaultComparatorFamilyUnsupported"))
        assertEquals(0, current.int("symbolIteratorUnsupported"))
    }

    @Test
    fun `tracked real project artifact derives every reported production campaign metric`() {
        val evidence = jsonResource("/interpreter/concrete-semantics-production-v1.json")
            .getValue("realProjectCampaign").jsonObject
        val current = evidence.getValue("current").jsonObject
        val artifact = current.string("artifact")
        val artifactBytes = getResourcePath(artifact).toFile().readBytes()
        val artifactSha256 = MessageDigest.getInstance("SHA-256").digest(artifactBytes)
            .joinToString("") { byte -> "%02x".format(byte) }
        assertEquals(current.string("artifactSha256"), artifactSha256)
        val report = Json.parseToJsonElement(artifactBytes.decodeToString()).jsonObject
        val config = report.getValue("config").jsonObject
        assertEquals(evidence.int("seed"), config.int("seed"))
        assertEquals(evidence.int("pbtIterations"), config.int("pbtMaxIterations"))
        assertEquals("PBT_ONLY", config.string("mode"))

        val methods = report.getValue("methods").jsonArray.map(JsonElement::jsonObject)
        assertEquals(
            current.int("coveredBranches"),
            methods.sumOf { it.int("coveredBranches") },
        )
        assertEquals(current.int("totalBranches"), methods.sumOf { it.int("totalBranches") })

        fun method(methodId: String): JsonObject = methods.single { it.string("methodId") == methodId }
        fun pbt(methodId: String): JsonObject = method(methodId).getValue("pbt").jsonObject
        val comparatorFamily = listOf("indexOf", "lastIndexOf", "remove", "frequency", "equals")
            .map { pbt("arrays.ts::%dflt::$it/3") }
        assertEquals(
            current.int("defaultComparatorFamilyExecutions"),
            comparatorFamily.sumOf { it.int("executions") },
        )
        assertEquals(
            current.int("defaultComparatorFamilyReturned"),
            comparatorFamily.sumOf { it.int("returned") },
        )
        assertEquals(
            current.int("defaultComparatorFamilyThrew"),
            comparatorFamily.sumOf { it.int("threw") },
        )
        assertEquals(
            current.int("defaultComparatorFamilyUnsupported"),
            comparatorFamily.sumOf { it.int("unsupported") },
        )
        val comparatorFailureDescriptions = comparatorFamily.flatMap { pbt ->
            pbt.getValue("failures").jsonArray.map { it.jsonObject.string("description") }
        }
        assertTrue(comparatorFailureDescriptions.none { "defaultEquals" in it })

        val forEach = pbt("arrays.ts::%dflt::forEach/2")
        assertEquals(current.int("forEachExecutions"), forEach.int("executions"))
        assertEquals(current.int("forEachReturned"), forEach.int("returned"))
        assertEquals(current.int("forEachThrew"), forEach.int("threw"))
        assertEquals(current.int("forEachUnsupported"), forEach.int("unsupported"))
        val allFailureDescriptions = methods.flatMap { entry ->
            entry.getValue("pbt").jsonObject.getValue("failures").jsonArray
                .map { it.jsonObject.string("description") }
        }
        assertEquals(0, methods.sumOf { it.getValue("pbt").jsonObject.int("unsupported") })
        assertTrue(allFailureDescriptions.none { "Symbol.iterator" in it })
    }

    @Test
    fun `frozen nine iterator and eleven callable module targets have production witnesses`() {
        val scene = scene(
            "/interpreter/ConcreteModuleUtil.ts",
            "/interpreter/ConcreteSemanticProductionFixture.ts",
            "/interpreter/ConcreteCollectionsArrays.ts",
            "/semantics/iterator/IteratorSemanticsFixture.ts",
        )
        val callback = VFunction(method(scene, "continueUntilTwo"))
        val interpreter = EtsConcreteInterpreter(scene)

        fun observed(methodName: String, vararg args: VValue): Pair<ExecutionResult, Set<Pair<Int, Boolean>>> {
            val branches = linkedSetOf<Pair<Int, Boolean>>()
            val targetMethod = method(scene, methodName)
            val listener = object : ExecutionListener {
                override fun onBranch(ifStmt: EtsIfStmt, taken: EtsStmt, condition: Boolean) {
                    val index = targetMethod.cfg.stmts.indexOf(ifStmt)
                    if (index >= 0) branches += index to condition
                }
            }
            return interpreter.execute(targetMethod, args = args.toList(), listener = listener) to branches
        }

        val find = observed("findMinimumAfterIterator", VArray(mutableListOf(VNumber(3.0), VNumber(1.0))))
        assertEquals(ExecutionResult.Returned(VNumber(1.0)), find.first)
        assertTrue(find.second.containsAll(setOf(14 to false, 14 to true, 18 to true)), find.second.toString())

        val flatten = observed("flattenRecursiveAfterIterator", VArray(mutableListOf(VNumber(1.0))))
        assertTrue(flatten.first is ExecutionResult.Returned, flatten.first.toString())
        assertTrue(flatten.second.containsAll(setOf(8 to false, 8 to true, 13 to false)), flatten.second.toString())

        val forEachEmpty = observed("collectionForEach", VArray(), callback)
        val forEachStop = observed(
            "collectionForEach",
            VArray(mutableListOf(VNumber(1.0), VNumber(2.0), VNumber(3.0))),
            callback,
        )
        assertTrue(forEachEmpty.first is ExecutionResult.Returned, forEachEmpty.first.toString())
        assertTrue(forEachStop.first is ExecutionResult.Returned, forEachStop.first.toString())
        val forEachBranches = forEachEmpty.second + forEachStop.second
        assertTrue(forEachBranches.containsAll(setOf(7 to true, 7 to false, 12 to true)), forEachBranches.toString())

        val indexWitnesses = listOf(
            observed("indexOf", VArray(), VNumber(1.0), VUndefined),
            observed("indexOf", VArray(mutableListOf(VNumber(1.0))), VNumber(1.0), VUndefined),
        )
        val lastIndexWitnesses = listOf(
            observed("lastIndexOf", VArray(), VNumber(1.0), VUndefined),
            observed("lastIndexOf", VArray(mutableListOf(VNumber(1.0))), VNumber(1.0), VUndefined),
        )
        val frequencyWitnesses = listOf(
            observed("frequency", VArray(), VNumber(1.0), VUndefined),
            observed("frequency", VArray(mutableListOf(VNumber(1.0))), VNumber(1.0), VUndefined),
        )
        val equalsWitnesses = listOf(
            observed("equals", VArray(), VArray(mutableListOf(VNumber(1.0))), VUndefined),
            observed("equals", VArray(), VArray(), VUndefined),
            observed(
                "equals",
                VArray(mutableListOf(VNumber(1.0))),
                VArray(mutableListOf(VNumber(1.0))),
                VUndefined,
            ),
        )
        val removeWitnesses = listOf(
            observed(
                "remove",
                VArray(mutableListOf(VNumber(1.0))),
                VNumber(2.0),
                VUndefined,
            ),
        )
        val callableModuleWitnesses =
            indexWitnesses + lastIndexWitnesses + frequencyWitnesses + equalsWitnesses + removeWitnesses
        assertTrue(callableModuleWitnesses.all { it.first is ExecutionResult.Returned }) {
            callableModuleWitnesses.joinToString { it.first.toString() }
        }
        fun branches(witnesses: List<Pair<ExecutionResult, Set<Pair<Int, Boolean>>>>): Set<Pair<Int, Boolean>> =
            witnesses.flatMap { it.second }.toSet()
        assertTrue(branches(indexWitnesses).containsAll(setOf(9 to true, 9 to false)))
        assertTrue(branches(lastIndexWitnesses).containsAll(setOf(9 to true, 9 to false)))
        assertTrue(branches(frequencyWitnesses).containsAll(setOf(10 to true, 10 to false)))
        assertTrue(branches(equalsWitnesses).containsAll(setOf(9 to true, 9 to false, 15 to true, 15 to false)))
        assertTrue(branches(removeWitnesses).contains(6 to true))
    }

    @Test
    fun `historical default comparator and iterator failures stay eliminated at frozen execution counts`() {
        val scene = scene(
            "/interpreter/ConcreteModuleUtil.ts",
            "/interpreter/ConcreteSemanticProductionFixture.ts",
            "/interpreter/ConcreteCollectionsArrays.ts",
        )
        val interpreter = EtsConcreteInterpreter(scene)
        assertEquals(
            ExecutionResult.Returned(VNumber(0.0)),
            interpreter.execute(
                method(scene, "indexOf"),
                args = listOf(VArray(mutableListOf(VNumber(0.0))), VNumber(0.0)),
            ),
        )
        val callback = VFunction(method(scene, "continueUntilTwo"))
        val gates = jsonResource("/interpreter/concrete-semantics-production-v1.json")
            .getValue("productionGates").jsonObject
        val iteratorGate = gates.getValue("collectionIterator").jsonObject.getValue("current").jsonObject
        var iteratorReturned = 0
        repeat(iteratorGate.int("executions")) { iteration ->
            val input = VArray(
                MutableList(iteration % 5) { index -> VNumber(((iteration + index) % 4).toDouble()) },
            )
            val result = interpreter.execute(method(scene, "forEach"), args = listOf(input, callback))
            assertFalse(result is ExecutionResult.Unsupported, "iteration $iteration: $result")
            assertTrue(result is ExecutionResult.Returned, "iteration $iteration: $result")
            iteratorReturned++
        }
        assertEquals(iteratorGate.int("returned"), iteratorReturned)
        assertEquals(0, iteratorGate.int("symbolIteratorUnsupported"))

        val comparatorGate = gates.getValue("moduleCallableDefaultComparator").jsonObject
            .getValue("current").jsonObject
        val comparatorExecutions = comparatorGate.int("executions")
        assertEquals(0, comparatorExecutions % DEFAULT_COMPARATOR_FAMILIES)
        var defaultEqualsReturned = 0
        repeat(comparatorExecutions / DEFAULT_COMPARATOR_FAMILIES) { iteration ->
            val values: List<VValue> = List(iteration % 6) { index ->
                VNumber(((iteration * 3 + index) % 7).toDouble())
            }
            val needle = if (iteration % 3 == 0 && values.isNotEmpty()) {
                values[iteration % values.size]
            } else {
                VNumber(-1.0)
            }
            val executions = listOf(
                "indexOf" to listOf(VArray(values.toMutableList()), needle, VUndefined),
                "lastIndexOf" to listOf(VArray(values.toMutableList()), needle, VUndefined),
                "frequency" to listOf(VArray(values.toMutableList()), needle, VUndefined),
                "equals" to listOf(
                    VArray(values.toMutableList()),
                    VArray(if (iteration % 2 == 0) values.toMutableList() else mutableListOf()),
                    VUndefined,
                ),
                "remove" to listOf(VArray(values.toMutableList()), needle, VUndefined),
            )
            executions.forEach { (methodName, arguments) ->
                val result = interpreter.execute(method(scene, methodName), args = arguments)
                assertFalse(result is ExecutionResult.Threw, "$methodName iteration $iteration: $result")
                assertFalse(result is ExecutionResult.Unsupported, "$methodName iteration $iteration: $result")
                assertTrue(result is ExecutionResult.Returned, "$methodName iteration $iteration: $result")
                defaultEqualsReturned++
            }
        }
        assertEquals(comparatorGate.int("returned"), defaultEqualsReturned)
        assertEquals(0, comparatorGate.int("threw"))
        assertEquals(0, comparatorGate.int("unsupported"))
    }

    private companion object {
        const val DEFAULT_COMPARATOR_FAMILIES = 5
    }
}
