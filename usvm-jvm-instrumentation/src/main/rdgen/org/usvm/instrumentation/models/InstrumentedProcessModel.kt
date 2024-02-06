package org.usvm.instrumentation.models

import com.jetbrains.rd.generator.nova.*
import com.jetbrains.rd.generator.nova.kotlin.Kotlin11Generator.Intrinsic
import com.jetbrains.rd.generator.nova.kotlin.Kotlin11Generator.Namespace
import com.jetbrains.rd.generator.nova.kotlin.KotlinIntrinsicMarshaller

object InstrumentedProcessRoot : Root()

object InstrumentedProcessModel : Ext(InstrumentedProcessRoot) {

    const val uTestInstPackage = "org.usvm.instrumentation.testcase.api"
    const val uTestInstClassName = "$uTestInstPackage.UTestInst"
    const val serializersPackage = "org.usvm.instrumentation.serializer"
    const val uTestInstSerializerMarshaller = "$serializersPackage.UTestInstSerializer"

    const val uTestValueDescriptorPackage = "org.usvm.instrumentation.testcase.descriptor"
    const val uTestValueDescriptorSimpleClassName = "UTestValueDescriptor"
    const val uTestValueDescriptorClassName = "$uTestValueDescriptorPackage.$uTestValueDescriptorSimpleClassName"
    const val uTestValueDescriptorSerializerMarshaller = "$serializersPackage.UTestValueDescriptorSerializer"

    private val UTestInst = Struct.Open("UTestInst", this, null).apply {
        settings[Namespace] = uTestInstPackage
        settings[Intrinsic] = KotlinIntrinsicMarshaller(
            "(ctx.serializers.get($uTestInstSerializerMarshaller.marshallerId)!! as IMarshaller<${uTestInstClassName}>)"
        )
    }

    private val UTestValueDescriptor = Struct.Open(uTestValueDescriptorSimpleClassName, this, null).apply {
        settings[Namespace] = uTestValueDescriptorPackage
        settings[Intrinsic] = KotlinIntrinsicMarshaller(
            "(ctx.serializers.get($uTestValueDescriptorSerializerMarshaller.marshallerId)!! as IMarshaller<${uTestValueDescriptorClassName}>)"
        )
    }

    private val executeParams = structdef {
        field("classname", PredefinedType.string)
        field("signature", PredefinedType.string)
        field("classpath", PredefinedType.string)
    }

    private val serializedUTest = structdef {
        field("initStatements", immutableList(UTestInst))
        field("callMethodExpression", UTestInst)
    }

    private val executionStateSerialized = structdef {
        field("instanceDescriptor", UTestValueDescriptor.nullable)
        field("argsDescriptors", immutableList(UTestValueDescriptor.nullable))
        field("statics", immutableList(serializedStaticField).nullable)
        field("accessedFields", immutableList(PredefinedType.string))
    }

    private val serializedStaticField = structdef {
        field("fieldName", PredefinedType.string)
        field("fieldDescriptor", UTestValueDescriptor)
    }

    private val classToId = structdef {
        field("className", PredefinedType.string)
        field("classId", PredefinedType.long)
    }

    private val tracedInstruction = structdef {
        field("instructionId", PredefinedType.long)
        field("numberOfTouches", PredefinedType.long)
    }

    private val executionResult = structdef {
        field("type", enum("ExecutionResultType") {
            +"UTestExecutionInitFailedResult"
            +"UTestExecutionSuccessResult"
            +"UTestExecutionExceptionResult"
            +"UTestExecutionFailedResult"
            +"UTestExecutionTimedOutResult"
        })
        field("classes", immutableList(classToId).nullable)
        field("trace", immutableList(tracedInstruction).nullable)
        field("cause", UTestValueDescriptor.nullable)
        field("result", UTestValueDescriptor.nullable)
        field("initialState", executionStateSerialized.nullable)
        field("resultState", executionStateSerialized.nullable)
    }


    init {
        call("callUTest", serializedUTest, executionResult).apply {
            async
        }
    }
}