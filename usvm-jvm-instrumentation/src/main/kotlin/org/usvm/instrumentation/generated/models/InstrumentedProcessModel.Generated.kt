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
            serializers.register(ExtendedValueDescriptor)
            serializers.register(ExecutionStateSerialized)
            serializers.register(SerializedStaticField)
            serializers.register(ClassToId)
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
        
        
        const val serializationHash = 3722177400659570323L
        
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
 * #### Generated from [InstrumentedProcessModel.kt:63]
 */
data class ClassToId (
    val className: String,
    val classId: Long
) : IPrintable {
    //companion
    
    companion object : IMarshaller<ClassToId> {
        override val _type: KClass<ClassToId> = ClassToId::class
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): ClassToId  {
            val className = buffer.readString()
            val classId = buffer.readLong()
            return ClassToId(className, classId)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: ClassToId)  {
            buffer.writeString(value.className)
            buffer.writeLong(value.classId)
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
        
        other as ClassToId
        
        if (className != other.className) return false
        if (classId != other.classId) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + className.hashCode()
        __r = __r*31 + classId.hashCode()
        return __r
    }
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("ClassToId (")
        printer.indent {
            print("className = "); className.print(printer); println()
            print("classId = "); classId.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    //contexts
}


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
 * #### Generated from [InstrumentedProcessModel.kt:68]
 */
data class ExecutionResult (
    val type: ExecutionResultType,
    val classes: List<ClassToId>?,
    val trace: List<Long>?,
    val cause: org.usvm.instrumentation.testcase.descriptor.UTestValueDescriptor?,
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
            val classes = buffer.readNullable { buffer.readList { ClassToId.read(ctx, buffer) } }
            val trace = buffer.readNullable { buffer.readList { buffer.readLong() } }
            val cause = buffer.readNullable { (ctx.serializers.get(org.usvm.instrumentation.serializer.UTestValueDescriptorSerializer.marshallerId)!! as IMarshaller<org.usvm.instrumentation.testcase.descriptor.UTestValueDescriptor>).read(ctx, buffer) }
            val result = buffer.readNullable { (ctx.serializers.get(org.usvm.instrumentation.serializer.UTestValueDescriptorSerializer.marshallerId)!! as IMarshaller<org.usvm.instrumentation.testcase.descriptor.UTestValueDescriptor>).read(ctx, buffer) }
            val initialState = buffer.readNullable { ExecutionStateSerialized.read(ctx, buffer) }
            val resultState = buffer.readNullable { ExecutionStateSerialized.read(ctx, buffer) }
            return ExecutionResult(type, classes, trace, cause, result, initialState, resultState)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: ExecutionResult)  {
            buffer.writeEnum(value.type)
            buffer.writeNullable(value.classes) { buffer.writeList(it) { v -> ClassToId.write(ctx, buffer, v) } }
            buffer.writeNullable(value.trace) { buffer.writeList(it) { v -> buffer.writeLong(v) } }
            buffer.writeNullable(value.cause) { (ctx.serializers.get(org.usvm.instrumentation.serializer.UTestValueDescriptorSerializer.marshallerId)!! as IMarshaller<org.usvm.instrumentation.testcase.descriptor.UTestValueDescriptor>).write(ctx,buffer, it) }
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
        if (classes != other.classes) return false
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
        __r = __r*31 + if (classes != null) classes.hashCode() else 0
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
            print("classes = "); classes.print(printer); println()
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
 * #### Generated from [InstrumentedProcessModel.kt:69]
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
 * #### Generated from [InstrumentedProcessModel.kt:52]
 */
data class ExecutionStateSerialized (
    val instanceDescriptor: ExtendedValueDescriptor?,
    val argsDescriptors: List<ExtendedValueDescriptor?>,
    val statics: List<SerializedStaticField>?
) : IPrintable {
    //companion
    
    companion object : IMarshaller<ExecutionStateSerialized> {
        override val _type: KClass<ExecutionStateSerialized> = ExecutionStateSerialized::class
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): ExecutionStateSerialized  {
            val instanceDescriptor = buffer.readNullable { ExtendedValueDescriptor.read(ctx, buffer) }
            val argsDescriptors = buffer.readList { buffer.readNullable { ExtendedValueDescriptor.read(ctx, buffer) } }
            val statics = buffer.readNullable { buffer.readList { SerializedStaticField.read(ctx, buffer) } }
            return ExecutionStateSerialized(instanceDescriptor, argsDescriptors, statics)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: ExecutionStateSerialized)  {
            buffer.writeNullable(value.instanceDescriptor) { ExtendedValueDescriptor.write(ctx, buffer, it) }
            buffer.writeList(value.argsDescriptors) { v -> buffer.writeNullable(v) { ExtendedValueDescriptor.write(ctx, buffer, it) } }
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
        __r = __r*31 + argsDescriptors.hashCode()
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
 * #### Generated from [InstrumentedProcessModel.kt:47]
 */
data class ExtendedValueDescriptor (
    val valueDescriptor: org.usvm.instrumentation.testcase.descriptor.UTestValueDescriptor?,
    val originUTestInstId: Int
) : IPrintable {
    //companion
    
    companion object : IMarshaller<ExtendedValueDescriptor> {
        override val _type: KClass<ExtendedValueDescriptor> = ExtendedValueDescriptor::class
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): ExtendedValueDescriptor  {
            val valueDescriptor = buffer.readNullable { (ctx.serializers.get(org.usvm.instrumentation.serializer.UTestValueDescriptorSerializer.marshallerId)!! as IMarshaller<org.usvm.instrumentation.testcase.descriptor.UTestValueDescriptor>).read(ctx, buffer) }
            val originUTestInstId = buffer.readInt()
            return ExtendedValueDescriptor(valueDescriptor, originUTestInstId)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: ExtendedValueDescriptor)  {
            buffer.writeNullable(value.valueDescriptor) { (ctx.serializers.get(org.usvm.instrumentation.serializer.UTestValueDescriptorSerializer.marshallerId)!! as IMarshaller<org.usvm.instrumentation.testcase.descriptor.UTestValueDescriptor>).write(ctx,buffer, it) }
            buffer.writeInt(value.originUTestInstId)
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
        
        other as ExtendedValueDescriptor
        
        if (valueDescriptor != other.valueDescriptor) return false
        if (originUTestInstId != other.originUTestInstId) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + if (valueDescriptor != null) valueDescriptor.hashCode() else 0
        __r = __r*31 + originUTestInstId.hashCode()
        return __r
    }
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("ExtendedValueDescriptor (")
        printer.indent {
            print("valueDescriptor = "); valueDescriptor.print(printer); println()
            print("originUTestInstId = "); originUTestInstId.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    //contexts
}


/**
 * #### Generated from [InstrumentedProcessModel.kt:58]
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
 * #### Generated from [InstrumentedProcessModel.kt:42]
 */
data class SerializedUTest (
    val initStatements: List<org.usvm.instrumentation.testcase.api.UTestInst>,
    val callMethodExpression: org.usvm.instrumentation.testcase.api.UTestInst
) : IPrintable {
    //companion
    
    companion object : IMarshaller<SerializedUTest> {
        override val _type: KClass<SerializedUTest> = SerializedUTest::class
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): SerializedUTest  {
            val initStatements = buffer.readList { (ctx.serializers.get(org.usvm.instrumentation.serializer.UTestInstSerializer.marshallerId)!! as IMarshaller<org.usvm.instrumentation.testcase.api.UTestInst>).read(ctx, buffer) }
            val callMethodExpression = (ctx.serializers.get(org.usvm.instrumentation.serializer.UTestInstSerializer.marshallerId)!! as IMarshaller<org.usvm.instrumentation.testcase.api.UTestInst>).read(ctx, buffer)
            return SerializedUTest(initStatements, callMethodExpression)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: SerializedUTest)  {
            buffer.writeList(value.initStatements) { v -> (ctx.serializers.get(org.usvm.instrumentation.serializer.UTestInstSerializer.marshallerId)!! as IMarshaller<org.usvm.instrumentation.testcase.api.UTestInst>).write(ctx,buffer, v) }
            (ctx.serializers.get(org.usvm.instrumentation.serializer.UTestInstSerializer.marshallerId)!! as IMarshaller<org.usvm.instrumentation.testcase.api.UTestInst>).write(ctx,buffer, value.callMethodExpression)
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
