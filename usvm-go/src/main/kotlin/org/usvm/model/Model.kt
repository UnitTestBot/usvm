package org.usvm.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Package(
    val name: String,
    val members: List<Member>,
)

@Serializable
sealed interface Member {
    val name: String

    @Serializable
    @SerialName("NamedConst")
    data class NamedConst(
        override val name: String,
        val value: NamedConstValue,
    ) : Member

    @Serializable
    @SerialName("Function")
    data class Function(
        override val name: String,
        @SerialName("basic_blocks") val basicBlocks: List<BasicBlock>,
        val parameters: List<Value>,
        @SerialName("return_types") val returnTypes: List<String>,
    ) : Member

    @Serializable
    @SerialName("Global")
    data class Global(override val name: String) : Member

    @Serializable
    @SerialName("Type")
    data class Type(override val name: String) : Member

    @Serializable
    data class BasicBlock(
        val index: Int,
        val instructions: List<Instruction>,
        val prev: List<Int>,
        val next: List<Int>,
    )
}

@Serializable
data class NamedConstValue(
    val type: String,
    val value: String,
)

@Serializable
sealed interface Instruction {
    val name: String
    val block: Int
    val line: Int

    @Serializable
    @SerialName("DebugRef")
    data class DebugRef(
        override val name: String,
        override val block: Int,
        override val line: Int,
    ) : Instruction

    @Serializable
    @SerialName("UnOp")
    data class UnOp(
        override val name: String,
        override val block: Int,
        override val line: Int,
        @SerialName("go_type") val goType: String,
        val op: String,
        val register: String,
        val argument: Value,
        @SerialName("comma_ok") val commaOk: Boolean,
    ) : Instruction

    @Serializable
    @SerialName("BinOp")
    data class BinOp(
        override val name: String,
        override val block: Int,
        override val line: Int,
        @SerialName("go_type") val goType: String,
        val op: String,
        val register: String,
        val first: Value,
        val second: Value,
    ) : Instruction

    @Serializable
    @SerialName("Call")
    data class Call(
        override val name: String,
        override val block: Int,
        override val line: Int,
        @SerialName("go_type") val goType: String,
        val register: String,
        val value: Value,
        val args: List<Value>,
    ) : Instruction

    @Serializable
    @SerialName("ChangeInterface")
    data class ChangeInterface(
        override val name: String,
        override val block: Int,
        override val line: Int,
    ) : Instruction

    @Serializable
    @SerialName("ChangeType")
    data class ChangeType(
        override val name: String,
        override val block: Int,
        override val line: Int,
    ) : Instruction

    @Serializable
    @SerialName("SliceToArrayPointer")
    data class SliceToArrayPointer(
        override val name: String,
        override val block: Int,
        override val line: Int,
    ) : Instruction

    @Serializable
    @SerialName("MakeInterface")
    data class MakeInterface(
        override val name: String,
        override val block: Int,
        override val line: Int,
    ) : Instruction

    @Serializable
    @SerialName("Extract")
    data class Extract(
        override val name: String,
        override val block: Int,
        override val line: Int,
    ) : Instruction

    @Serializable
    @SerialName("Slice")
    data class Slice(
        override val name: String,
        override val block: Int,
        override val line: Int,
    ) : Instruction

    @Serializable
    @SerialName("Return")
    data class Return(
        override val name: String,
        override val block: Int,
        override val line: Int,
        val results: List<Value>,
    ) : Instruction

    @Serializable
    @SerialName("RunDefers")
    data class RunDefers(
        override val name: String,
        override val block: Int,
        override val line: Int,
    ) : Instruction

    @Serializable
    @SerialName("Panic")
    data class Panic(
        override val name: String,
        override val block: Int,
        override val line: Int,
    ) : Instruction

    @Serializable
    @SerialName("Send")
    data class Send(
        override val name: String,
        override val block: Int,
        override val line: Int,
    ) : Instruction

    @Serializable
    @SerialName("Store")
    data class Store(
        override val name: String,
        override val block: Int,
        override val line: Int,
    ) : Instruction

    @Serializable
    @SerialName("If")
    data class If(
        override val name: String,
        override val block: Int,
        override val line: Int,
        val condition: Value,
        @SerialName("true_branch") val trueBranch: Int,
        @SerialName("false_branch") val falseBranch: Int,
    ) : Instruction

    @Serializable
    @SerialName("Jump")
    data class Jump(
        override val name: String,
        override val block: Int,
        override val line: Int,
        val index: Int,
    ) : Instruction

    @Serializable
    @SerialName("Defer")
    data class Defer(
        override val name: String,
        override val block: Int,
        override val line: Int,
    ) : Instruction

    @Serializable
    @SerialName("Go")
    data class Go(
        override val name: String,
        override val block: Int,
        override val line: Int,
    ) : Instruction

    @Serializable
    @SerialName("MakeChan")
    data class MakeChan(
        override val name: String,
        override val block: Int,
        override val line: Int,
    ) : Instruction

    @Serializable
    @SerialName("Alloc")
    data class Alloc(
        override val name: String,
        override val block: Int,
        override val line: Int,
    ) : Instruction

    @Serializable
    @SerialName("MakeSlice")
    data class MakeSlice(
        override val name: String,
        override val block: Int,
        override val line: Int,
    ) : Instruction

    @Serializable
    @SerialName("MakeMap")
    data class MakeMap(
        override val name: String,
        override val block: Int,
        override val line: Int,
    ) : Instruction

    @Serializable
    @SerialName("Range")
    data class Range(
        override val name: String,
        override val block: Int,
        override val line: Int,
    ) : Instruction

    @Serializable
    @SerialName("Next")
    data class Next(
        override val name: String,
        override val block: Int,
        override val line: Int,
    ) : Instruction

    @Serializable
    @SerialName("FieldAddr")
    data class FieldAddr(
        override val name: String,
        override val block: Int,
        override val line: Int,
    ) : Instruction

    @Serializable
    @SerialName("Field")
    data class Field(
        override val name: String,
        override val block: Int,
        override val line: Int,
    ) : Instruction

    @Serializable
    @SerialName("IndexAddr")
    data class IndexAddr(
        override val name: String,
        override val block: Int,
        override val line: Int,
    ) : Instruction

    @Serializable
    @SerialName("Index")
    data class Index(
        override val name: String,
        override val block: Int,
        override val line: Int,
    ) : Instruction

    @Serializable
    @SerialName("Lookup")
    data class Lookup(
        override val name: String,
        override val block: Int,
        override val line: Int,
    ) : Instruction

    @Serializable
    @SerialName("MapUpdate")
    data class MapUpdate(
        override val name: String,
        override val block: Int,
        override val line: Int,
    ) : Instruction

    @Serializable
    @SerialName("TypeAssert")
    data class TypeAssert(
        override val name: String,
        override val block: Int,
        override val line: Int,
    ) : Instruction

    @Serializable
    @SerialName("MakeClosure")
    data class MakeClosure(
        override val name: String,
        override val block: Int,
        override val line: Int,
    ) : Instruction

    @Serializable
    @SerialName("Phi")
    data class Phi(
        override val name: String,
        override val block: Int,
        override val line: Int,
        @SerialName("go_type") val goType: String,
        val register: String,
        val edges: List<Value>,
    ) : Instruction

    @Serializable
    @SerialName("Select")
    data class Select(
        override val name: String,
        override val block: Int,
        override val line: Int,
    ) : Instruction
}

@Serializable
sealed interface Value {
    val goType: String
    val name: String

    @Serializable
    @SerialName("Const")
    data class Const(
        @SerialName("go_type") override val goType: String,
        override val name: String,
        val value: NamedConstValue,
    ) : Value

    @Serializable
    @SerialName("Global")
    data class Global(
        @SerialName("go_type") override val goType: String,
        override val name: String,
        val index: Int,
    ) : Value

    @Serializable
    @SerialName("Parameter")
    data class Parameter(
        @SerialName("go_type") override val goType: String,
        override val name: String,
        val index: Int,
    ) : Value

    @Serializable
    @SerialName("FreeVar")
    data class FreeVar(
        @SerialName("go_type") override val goType: String,
        override val name: String,
        val index: Int,
    ) : Value

    @Serializable
    @SerialName("Var")
    data class Var(
        @SerialName("go_type") override val goType: String,
        override val name: String,
    ) : Value

    @Serializable
    @SerialName("Function")
    data class Function(
        @SerialName("go_type") override val goType: String,
        override val name: String,
    ) : Value

    @Serializable
    @SerialName("MakeClosure")
    data class MakeClosure(
        @SerialName("go_type") override val goType: String,
        override val name: String,
    ) : Value

    @Serializable
    @SerialName("Builtin")
    data class Builtin(
        @SerialName("go_type") override val goType: String,
        override val name: String,
    ) : Value
}