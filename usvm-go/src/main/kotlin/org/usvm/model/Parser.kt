package org.usvm.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import java.io.File

class Parser {
    private val jsonSerializer = Json {
        serializersModule = SerializersModule {
            polymorphic(Member::class) {
                subclass(Member.NamedConst::class, Member.NamedConst.serializer())
                subclass(Member.Function::class, Member.Function.serializer())
                subclass(Member.Global::class, Member.Global.serializer())
                subclass(Member.Type::class, Member.Type.serializer())
            }

            polymorphic(Instruction::class) {
                subclass(Instruction.DebugRef::class, Instruction.DebugRef.serializer())
                subclass(Instruction.UnOp::class, Instruction.UnOp.serializer())
                subclass(Instruction.BinOp::class, Instruction.BinOp.serializer())
                subclass(Instruction.Call::class, Instruction.Call.serializer())
                subclass(Instruction.ChangeInterface::class, Instruction.ChangeInterface.serializer())
                subclass(Instruction.ChangeType::class, Instruction.ChangeType.serializer())
                subclass(Instruction.Convert::class, Instruction.Convert.serializer())
                subclass(Instruction.SliceToArrayPointer::class, Instruction.SliceToArrayPointer.serializer())
                subclass(Instruction.MakeInterface::class, Instruction.MakeInterface.serializer())
                subclass(Instruction.Extract::class, Instruction.Extract.serializer())
                subclass(Instruction.Slice::class, Instruction.Slice.serializer())
                subclass(Instruction.Return::class, Instruction.Return.serializer())
                subclass(Instruction.RunDefers::class, Instruction.RunDefers.serializer())
                subclass(Instruction.Panic::class, Instruction.Panic.serializer())
                subclass(Instruction.Send::class, Instruction.Send.serializer())
                subclass(Instruction.Store::class, Instruction.Store.serializer())
                subclass(Instruction.If::class, Instruction.If.serializer())
                subclass(Instruction.Jump::class, Instruction.Jump.serializer())
                subclass(Instruction.Defer::class, Instruction.Defer.serializer())
                subclass(Instruction.Go::class, Instruction.Go.serializer())
                subclass(Instruction.MakeChan::class, Instruction.MakeChan.serializer())
                subclass(Instruction.Alloc::class, Instruction.Alloc.serializer())
                subclass(Instruction.MakeSlice::class, Instruction.MakeSlice.serializer())
                subclass(Instruction.MakeMap::class, Instruction.MakeMap.serializer())
                subclass(Instruction.Range::class, Instruction.Range.serializer())
                subclass(Instruction.Next::class, Instruction.Next.serializer())
                subclass(Instruction.FieldAddr::class, Instruction.FieldAddr.serializer())
                subclass(Instruction.Field::class, Instruction.Field.serializer())
                subclass(Instruction.IndexAddr::class, Instruction.IndexAddr.serializer())
                subclass(Instruction.Index::class, Instruction.Index.serializer())
                subclass(Instruction.Lookup::class, Instruction.Lookup.serializer())
                subclass(Instruction.MapUpdate::class, Instruction.MapUpdate.serializer())
                subclass(Instruction.TypeAssert::class, Instruction.TypeAssert.serializer())
                subclass(Instruction.MakeClosure::class, Instruction.MakeClosure.serializer())
                subclass(Instruction.Phi::class, Instruction.Phi.serializer())
                subclass(Instruction.Select::class, Instruction.Select.serializer())
                subclass(Instruction.MultiConvert::class, Instruction.MultiConvert.serializer())
            }

            polymorphic(Value::class) {
                subclass(Value.Const::class, Value.Const.serializer())
                subclass(Value.Global::class, Value.Global.serializer())
                subclass(Value.Parameter::class, Value.Parameter.serializer())
                subclass(Value.FreeVar::class, Value.FreeVar.serializer())
                subclass(Value.Var::class, Value.Var.serializer())
                subclass(Value.Function::class, Value.Function.serializer())
                subclass(Value.MakeClosure::class, Value.MakeClosure.serializer())
                subclass(Value.Builtin::class, Value.Builtin.serializer())
            }

            polymorphic(Type::class) {
                subclass(Type.Alias::class, Type.Alias.serializer())
                subclass(Type.Array::class, Type.Array.serializer())
                subclass(Type.Basic::class, Type.Basic.serializer())
                subclass(Type.Chan::class, Type.Chan.serializer())
                subclass(Type.Interface::class, Type.Interface.serializer())
                subclass(Type.Map::class, Type.Map.serializer())
                subclass(Type.Named::class, Type.Named.serializer())
                subclass(Type.Pointer::class, Type.Pointer.serializer())
                subclass(Type.Signature::class, Type.Signature.serializer())
                subclass(Type.Slice::class, Type.Slice.serializer())
                subclass(Type.Struct::class, Type.Struct.serializer())
                subclass(Type.Tuple::class, Type.Tuple.serializer())
                subclass(Type.TypeParam::class, Type.TypeParam.serializer())
                subclass(Type.Union::class, Type.Union.serializer())
            }
        }
    }

    fun deserialize(filename: String): Package {
        val file = File(filename)
        val jsonString = file.readText()
        return Json.decodeFromString<Package>(jsonString)
    }

    fun serialize(pkg: Package): String {
        return jsonSerializer.encodeToString(pkg)
    }
}