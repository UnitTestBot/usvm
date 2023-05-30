@file:Suppress("EXPERIMENTAL_API_USAGE","EXPERIMENTAL_UNSIGNED_LITERALS","PackageDirectoryMismatch","UnusedImport","unused","LocalVariableName","CanBeVal","PropertyName","EnumEntryName","ClassName","ObjectPropertyName","UnnecessaryVariable","SpellCheckingInspection")
package org.usvm.instrumentation.generated.models

import com.jetbrains.rd.framework.*
import com.jetbrains.rd.framework.base.*
import com.jetbrains.rd.framework.impl.*

import com.jetbrains.rd.util.lifetime.*
import com.jetbrains.rd.util.reactive.*
import com.jetbrains.rd.util.string.*
import com.jetbrains.rd.util.*
import kotlin.time.Duration
import kotlin.reflect.KClass
import kotlin.jvm.JvmStatic



/**
 * #### Generated from [InstrumentedProcessModel.kt:10]
 */
class InstrumentedProcessModel private constructor(
    private val _callUTest: RdCall<SerializedUTest, ExecutionResult>
) : RdExtBase() {
    //companion
    
    companion object : ISerializersOwner {
        
        override fun registerSerializersCore(serializers: ISerializers)  {
            serializers.register(ExecuteParams)
            serializers.register(SerializedUTest)
            serializers.register(ExecutionStateSerialized)
            serializers.register(SerializedStaticField)
            serializers.register(SerializedTracedJcInst)
            serializers.register(ExecutionResult)
            serializers.register(ExecutionResultType.marshaller)
        }
        
        
        @JvmStatic
        @JvmName("internalCreateModel")
        @Deprecated("Use create instead", ReplaceWith("create(lifetime, protocol)"))
        internal fun createModel(lifetime: Lifetime, protocol: IProtocol): InstrumentedProcessModel  {
            @Suppress("DEPRECATION")
            return create(lifetime, protocol)
        }
        
        @JvmStatic
        @Deprecated("Use protocol.instrumentedProcessModel or revise the extension scope instead", ReplaceWith("protocol.instrumentedProcessModel"))
        fun create(lifetime: Lifetime, protocol: IProtocol): InstrumentedProcessModel  {
            InstrumentedProcessRoot.register(protocol.serializers)
            
            return InstrumentedProcessModel()
        }
        
        
        const val serializationHash = 2341180415100698488L
        
    }
    override val serializersOwner: ISerializersOwner get() = InstrumentedProcessModel
    override val serializationHash: Long get() = InstrumentedProcessModel.serializationHash
    
    //fields
    val callUTest: RdCall<SerializedUTest, ExecutionResult> get() = _callUTest
    //methods
    //initializer
    init {
        _callUTest.async = true
    }
    
    init {
        bindableChildren.add("callUTest" to _callUTest)
    }
    
    //secondary constructor
    private constructor(
    ) : this(
        RdCall<SerializedUTest, ExecutionResult>(SerializedUTest, ExecutionResult)
    )
    
    //equals trait
    //hash code trait
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("InstrumentedProcessModel (")
        printer.indent {
            print("callUTest = "); _callUTest.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    override fun deepClone(): InstrumentedProcessModel   {
        return InstrumentedProcessModel(
            _callUTest.deepClonePolymorphic()
        )
    }
    //contexts
}
val IProtocol.instrumentedProcessModel get() = getOrCreateExtension(InstrumentedProcessModel::class) { @Suppress("DEPRECATION") InstrumentedProcessModel.create(lifetime, this) }



/**
 * #### Generated from [InstrumentedProcessModel.kt:36]
 */
data class ExecuteParams (
    val classname: String,
    val signature: String,
    val classpath: String
) : IPrintable {
    //companion
    
    companion object : IMarshaller<ExecuteParams> {
        override val _type: KClass<ExecuteParams> = ExecuteParams::class
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): ExecuteParams  {
            val classname = buffer.readString()
            val signature = buffer.readString()
            val classpath = buffer.readString()
            return ExecuteParams(classname, signature, classpath)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: ExecuteParams)  {
            buffer.writeString(value.classname)
            buffer.writeString(value.signature)
            buffer.writeString(value.classpath)
        }
        
        
    }
    //fields
    //methods
    //initializer
    //secondary constructor
    //equals trait
    override fun equals(other: Any?): Boolean  {
        if (this === other) return true
        if (other == null || other::class != this::class) return false
        
        other as ExecuteParams
        
        if (classname != other.classname) return false
        if (signature != other.signature) return false
        if (classpath != other.classpath) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + classname.hashCode()
        __r = __r*31 + signature.hashCode()
        __r = __r*31 + classpath.hashCode()
        return __r
    }
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("ExecuteParams (")
        printer.indent {
            print("classname = "); classname.print(printer); println()
            print("signature = "); signature.print(printer); println()
            print("classpath = "); classpath.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    //contexts
}


/**
 * #### Generated from [InstrumentedProcessModel.kt:65]
 */
data class ExecutionResult (
    val type: ExecutionResultType,
    val trace: List<SerializedTracedJcInst>?,
    val cause: String?,
    val result: org.usvm.instrumentation.testcase.descriptor.UTestValueDescriptor?,
    val initialState: ExecutionStateSerialized?,
    val resultState: ExecutionStateSerialized?
) : IPrintable {
    //companion
    
    companion object : IMarshaller<ExecutionResult> {
        override val _type: KClass<ExecutionResult> = ExecutionResult::class
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): ExecutionResult  {
            val type = buffer.readEnum<ExecutionResultType>()
            val trace = buffer.readNullable { buffer.readList { SerializedTracedJcInst.read(ctx, buffer) } }
            val cause = buffer.readNullable { buffer.readString() }
            val result = buffer.readNullable { (ctx.serializers.get(org.usvm.instrumentation.serializer.UTestValueDescriptorSerializer.marshallerId)!! as IMarshaller<org.usvm.instrumentation.testcase.descriptor.UTestValueDescriptor>).read(ctx, buffer) }
            val initialState = buffer.readNullable { ExecutionStateSerialized.read(ctx, buffer) }
            val resultState = buffer.readNullable { ExecutionStateSerialized.read(ctx, buffer) }
            return ExecutionResult(type, trace, cause, result, initialState, resultState)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: ExecutionResult)  {
            buffer.writeEnum(value.type)
            buffer.writeNullable(value.trace) { buffer.writeList(it) { v -> SerializedTracedJcInst.write(ctx, buffer, v) } }
            buffer.writeNullable(value.cause) { buffer.writeString(it) }
            buffer.writeNullable(value.result) { (ctx.serializers.get(org.usvm.instrumentation.serializer.UTestValueDescriptorSerializer.marshallerId)!! as IMarshaller<org.usvm.instrumentation.testcase.descriptor.UTestValueDescriptor>).write(ctx,buffer, it) }
            buffer.writeNullable(value.initialState) { ExecutionStateSerialized.write(ctx, buffer, it) }
            buffer.writeNullable(value.resultState) { ExecutionStateSerialized.write(ctx, buffer, it) }
        }
        
        
    }
    //fields
    //methods
    //initializer
    //secondary constructor
    //equals trait
    override fun equals(other: Any?): Boolean  {
        if (this === other) return true
        if (other == null || other::class != this::class) return false
        
        other as ExecutionResult
        
        if (type != other.type) return false
        if (trace != other.trace) return false
        if (cause != other.cause) return false
        if (result != other.result) return false
        if (initialState != other.initialState) return false
        if (resultState != other.resultState) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + type.hashCode()
        __r = __r*31 + if (trace != null) trace.hashCode() else 0
        __r = __r*31 + if (cause != null) cause.hashCode() else 0
        __r = __r*31 + if (result != null) result.hashCode() else 0
        __r = __r*31 + if (initialState != null) initialState.hashCode() else 0
        __r = __r*31 + if (resultState != null) resultState.hashCode() else 0
        return __r
    }
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("ExecutionResult (")
        printer.indent {
            print("type = "); type.print(printer); println()
            print("trace = "); trace.print(printer); println()
            print("cause = "); cause.print(printer); println()
            print("result = "); result.print(printer); println()
            print("initialState = "); initialState.print(printer); println()
            print("resultState = "); resultState.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    //contexts
}


/**
 * #### Generated from [InstrumentedProcessModel.kt:66]
 */
enum class ExecutionResultType {
    UTestExecutionInitFailedResult, 
    UTestExecutionSuccessResult, 
    UTestExecutionExceptionResult, 
    UTestExecutionFailedResult, 
    UTestExecutionTimedOutResult;
    
    companion object {
        val marshaller = FrameworkMarshallers.enum<ExecutionResultType>()
        
    }
}


/**
 * #### Generated from [InstrumentedProcessModel.kt:47]
 */
data class ExecutionStateSerialized (
    val instanceDescriptor: org.usvm.instrumentation.testcase.descriptor.UTestValueDescriptor?,
    val argsDescriptors: List<org.usvm.instrumentation.testcase.descriptor.UTestValueDescriptor?>?,
    val statics: List<SerializedStaticField>?
) : IPrintable {
    //companion
    
    companion object : IMarshaller<ExecutionStateSerialized> {
        override val _type: KClass<ExecutionStateSerialized> = ExecutionStateSerialized::class
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): ExecutionStateSerialized  {
            val instanceDescriptor = buffer.readNullable { (ctx.serializers.get(org.usvm.instrumentation.serializer.UTestValueDescriptorSerializer.marshallerId)!! as IMarshaller<org.usvm.instrumentation.testcase.descriptor.UTestValueDescriptor>).read(ctx, buffer) }
            val argsDescriptors = buffer.readNullable { buffer.readList { buffer.readNullable { (ctx.serializers.get(org.usvm.instrumentation.serializer.UTestValueDescriptorSerializer.marshallerId)!! as IMarshaller<org.usvm.instrumentation.testcase.descriptor.UTestValueDescriptor>).read(ctx, buffer) } } }
            val statics = buffer.readNullable { buffer.readList { SerializedStaticField.read(ctx, buffer) } }
            return ExecutionStateSerialized(instanceDescriptor, argsDescriptors, statics)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: ExecutionStateSerialized)  {
            buffer.writeNullable(value.instanceDescriptor) { (ctx.serializers.get(org.usvm.instrumentation.serializer.UTestValueDescriptorSerializer.marshallerId)!! as IMarshaller<org.usvm.instrumentation.testcase.descriptor.UTestValueDescriptor>).write(ctx,buffer, it) }
            buffer.writeNullable(value.argsDescriptors) { buffer.writeList(it) { v -> buffer.writeNullable(v) { (ctx.serializers.get(org.usvm.instrumentation.serializer.UTestValueDescriptorSerializer.marshallerId)!! as IMarshaller<org.usvm.instrumentation.testcase.descriptor.UTestValueDescriptor>).write(ctx,buffer, it) } } }
            buffer.writeNullable(value.statics) { buffer.writeList(it) { v -> SerializedStaticField.write(ctx, buffer, v) } }
        }
        
        
    }
    //fields
    //methods
    //initializer
    //secondary constructor
    //equals trait
    override fun equals(other: Any?): Boolean  {
        if (this === other) return true
        if (other == null || other::class != this::class) return false
        
        other as ExecutionStateSerialized
        
        if (instanceDescriptor != other.instanceDescriptor) return false
        if (argsDescriptors != other.argsDescriptors) return false
        if (statics != other.statics) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + if (instanceDescriptor != null) instanceDescriptor.hashCode() else 0
        __r = __r*31 + if (argsDescriptors != null) argsDescriptors.hashCode() else 0
        __r = __r*31 + if (statics != null) statics.hashCode() else 0
        return __r
    }
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("ExecutionStateSerialized (")
        printer.indent {
            print("instanceDescriptor = "); instanceDescriptor.print(printer); println()
            print("argsDescriptors = "); argsDescriptors.print(printer); println()
            print("statics = "); statics.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    //contexts
}


/**
 * #### Generated from [InstrumentedProcessModel.kt:53]
 */
data class SerializedStaticField (
    val fieldName: String,
    val fieldDescriptor: org.usvm.instrumentation.testcase.descriptor.UTestValueDescriptor
) : IPrintable {
    //companion
    
    companion object : IMarshaller<SerializedStaticField> {
        override val _type: KClass<SerializedStaticField> = SerializedStaticField::class
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): SerializedStaticField  {
            val fieldName = buffer.readString()
            val fieldDescriptor = (ctx.serializers.get(org.usvm.instrumentation.serializer.UTestValueDescriptorSerializer.marshallerId)!! as IMarshaller<org.usvm.instrumentation.testcase.descriptor.UTestValueDescriptor>).read(ctx, buffer)
            return SerializedStaticField(fieldName, fieldDescriptor)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: SerializedStaticField)  {
            buffer.writeString(value.fieldName)
            (ctx.serializers.get(org.usvm.instrumentation.serializer.UTestValueDescriptorSerializer.marshallerId)!! as IMarshaller<org.usvm.instrumentation.testcase.descriptor.UTestValueDescriptor>).write(ctx,buffer, value.fieldDescriptor)
        }
        
        
    }
    //fields
    //methods
    //initializer
    //secondary constructor
    //equals trait
    override fun equals(other: Any?): Boolean  {
        if (this === other) return true
        if (other == null || other::class != this::class) return false
        
        other as SerializedStaticField
        
        if (fieldName != other.fieldName) return false
        if (fieldDescriptor != other.fieldDescriptor) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + fieldName.hashCode()
        __r = __r*31 + fieldDescriptor.hashCode()
        return __r
    }
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("SerializedStaticField (")
        printer.indent {
            print("fieldName = "); fieldName.print(printer); println()
            print("fieldDescriptor = "); fieldDescriptor.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    //contexts
}


/**
 * #### Generated from [InstrumentedProcessModel.kt:58]
 */
data class SerializedTracedJcInst (
    val className: String,
    val methodName: String,
    val methodDescription: String,
    val index: Int
) : IPrintable {
    //companion
    
    companion object : IMarshaller<SerializedTracedJcInst> {
        override val _type: KClass<SerializedTracedJcInst> = SerializedTracedJcInst::class
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): SerializedTracedJcInst  {
            val className = buffer.readString()
            val methodName = buffer.readString()
            val methodDescription = buffer.readString()
            val index = buffer.readInt()
            return SerializedTracedJcInst(className, methodName, methodDescription, index)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: SerializedTracedJcInst)  {
            buffer.writeString(value.className)
            buffer.writeString(value.methodName)
            buffer.writeString(value.methodDescription)
            buffer.writeInt(value.index)
        }
        
        
    }
    //fields
    //methods
    //initializer
    //secondary constructor
    //equals trait
    override fun equals(other: Any?): Boolean  {
        if (this === other) return true
        if (other == null || other::class != this::class) return false
        
        other as SerializedTracedJcInst
        
        if (className != other.className) return false
        if (methodName != other.methodName) return false
        if (methodDescription != other.methodDescription) return false
        if (index != other.index) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + className.hashCode()
        __r = __r*31 + methodName.hashCode()
        __r = __r*31 + methodDescription.hashCode()
        __r = __r*31 + index.hashCode()
        return __r
    }
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("SerializedTracedJcInst (")
        printer.indent {
            print("className = "); className.print(printer); println()
            print("methodName = "); methodName.print(printer); println()
            print("methodDescription = "); methodDescription.print(printer); println()
            print("index = "); index.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    //contexts
}


/**
 * #### Generated from [InstrumentedProcessModel.kt:42]
 */
data class SerializedUTest (
    val initStatements: List<org.usvm.instrumentation.testcase.statement.UTestExpression>,
    val callMethodExpression: org.usvm.instrumentation.testcase.statement.UTestExpression
) : IPrintable {
    //companion
    
    companion object : IMarshaller<SerializedUTest> {
        override val _type: KClass<SerializedUTest> = SerializedUTest::class
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): SerializedUTest  {
            val initStatements = buffer.readList { (ctx.serializers.get(org.usvm.instrumentation.serializer.UTestExpressionSerializer.marshallerId)!! as IMarshaller<org.usvm.instrumentation.testcase.statement.UTestExpression>).read(ctx, buffer) }
            val callMethodExpression = (ctx.serializers.get(org.usvm.instrumentation.serializer.UTestExpressionSerializer.marshallerId)!! as IMarshaller<org.usvm.instrumentation.testcase.statement.UTestExpression>).read(ctx, buffer)
            return SerializedUTest(initStatements, callMethodExpression)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: SerializedUTest)  {
            buffer.writeList(value.initStatements) { v -> (ctx.serializers.get(org.usvm.instrumentation.serializer.UTestExpressionSerializer.marshallerId)!! as IMarshaller<org.usvm.instrumentation.testcase.statement.UTestExpression>).write(ctx,buffer, v) }
            (ctx.serializers.get(org.usvm.instrumentation.serializer.UTestExpressionSerializer.marshallerId)!! as IMarshaller<org.usvm.instrumentation.testcase.statement.UTestExpression>).write(ctx,buffer, value.callMethodExpression)
        }
        
        
    }
    //fields
    //methods
    //initializer
    //secondary constructor
    //equals trait
    override fun equals(other: Any?): Boolean  {
        if (this === other) return true
        if (other == null || other::class != this::class) return false
        
        other as SerializedUTest
        
        if (initStatements != other.initStatements) return false
        if (callMethodExpression != other.callMethodExpression) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + initStatements.hashCode()
        __r = __r*31 + callMethodExpression.hashCode()
        return __r
    }
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("SerializedUTest (")
        printer.indent {
            print("initStatements = "); initStatements.print(printer); println()
            print("callMethodExpression = "); callMethodExpression.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    //contexts
}
