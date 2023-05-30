package org.usvm.instrumentation.models

import com.jetbrains.rd.generator.nova.*
import com.jetbrains.rd.generator.nova.kotlin.Kotlin11Generator.Intrinsic
import com.jetbrains.rd.generator.nova.kotlin.Kotlin11Generator.Namespace
import com.jetbrains.rd.generator.nova.kotlin.KotlinIntrinsicMarshaller

object InstrumentedProcessRoot : Root()

object InstrumentedProcessModel : Ext(InstrumentedProcessRoot) {

    const val uTestExpressionPackage = "org.usvm.instrumentation.testcase.statement"
    const val uTestExpressionClassName = "$uTestExpressionPackage.UTestExpression"
    const val serializersPackage = "org.usvm.instrumentation.serializer"
    const val uTestExpressionSerializerMarshaller = "$serializersPackage.UTestExpressionSerializer"

    const val uTestValueDescriptorPackage = "org.usvm.instrumentation.testcase.descriptor"
    const val uTestValueDescriptorSimpleClassName = "UTestValueDescriptor"
    const val uTestValueDescriptorClassName = "$uTestValueDescriptorPackage.$uTestValueDescriptorSimpleClassName"
    const val uTestValueDescriptorSerializerMarshaller = "$serializersPackage.UTestValueDescriptorSerializer"

    private val UTestExpression = Struct.Open("UTestExpression", this, null).apply {
        settings[Namespace] = uTestExpressionPackage
        settings[Intrinsic] = KotlinIntrinsicMarshaller(
            "(ctx.serializers.get($uTestExpressionSerializerMarshaller.marshallerId)!! as IMarshaller<${uTestExpressionClassName}>)"
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
        field("initStatements", immutableList(UTestExpression))
        field("callMethodExpression", UTestExpression)
    }

    private val executionStateSerialized = structdef {
        field("instanceDescriptor", UTestValueDescriptor.nullable)
        field("argsDescriptors", immutableList(UTestValueDescriptor.nullable).nullable)
        field("statics", immutableList(serializedStaticField).nullable)
    }

    private val serializedStaticField = structdef {
        field("fieldName", PredefinedType.string)
        field("fieldDescriptor", UTestValueDescriptor)
    }

    private val serializedTracedJcInst = structdef {
        field("className", PredefinedType.string)
        field("methodName", PredefinedType.string)
        field("methodDescription", PredefinedType.string)
        field("index", PredefinedType.int)
    }

    private val executionResult = structdef {
        field("type", enum("ExecutionResultType") {
            +"UTestExecutionInitFailedResult"
            +"UTestExecutionSuccessResult"
            +"UTestExecutionExceptionResult"
            +"UTestExecutionFailedResult"
            +"UTestExecutionTimedOutResult"
        })
        field("trace", immutableList(serializedTracedJcInst).nullable)
        field("cause", PredefinedType.string.nullable)
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