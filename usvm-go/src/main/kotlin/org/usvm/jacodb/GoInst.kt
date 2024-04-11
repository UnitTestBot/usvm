package org.usvm.jacodb

import org.jacodb.api.core.cfg.CoreAssignInst
import org.jacodb.api.core.cfg.CoreCallInst
import org.jacodb.api.core.cfg.CoreExpr
import org.jacodb.api.core.cfg.CoreExprVisitor
import org.jacodb.api.core.cfg.CoreGotoInst
import org.jacodb.api.core.cfg.CoreIfInst
import org.jacodb.api.core.cfg.CoreInst
import org.jacodb.api.core.cfg.CoreReturnInst
import org.jacodb.api.core.cfg.InstVisitor

interface GoInst : CoreInst<GoInstLocation, GoMethod, GoExpr> {
    override val location: GoInstLocation
    override val operands: List<GoExpr>

    override val lineNumber: Int get() = location.lineNumber

    override fun <T> accept(visitor: InstVisitor<T>): T {
        require(visitor is GoInstVisitor<*>) { "TODO message" } // TODO this is wrong
        return accept(visitor as GoInstVisitor<T>)
    }

    fun <T> accept(visitor: GoInstVisitor<T>): T

    val parent: GoMethod
}

abstract class AbstractGoInst(override val location: GoInstLocation, override val parent: GoMethod) : GoInst {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AbstractGoInst

        return location == other.location
    }

    override fun hashCode(): Int {
        return location.hashCode()
    }
}

data class GoInstRef(
    val index: Int
)

class GoJumpInst(
    location: GoInstLocation,
    parent: GoMethod,
    val target: GoInstRef
) : AbstractGoInst(location, parent), GoBranchingInst, CoreGotoInst<GoInstLocation, GoMethod, GoExpr> {
    override val operands: List<GoExpr>
        get() = emptyList()

    override val successors: List<GoInstRef>
        get() = listOf(target)

    override fun toString(): String = "jump $target"

    override fun <T> accept(visitor: GoInstVisitor<T>): T {
        return visitor.visitGoJumpInst(this)
    }
}

class GoRunDefersInst(
    location: GoInstLocation,
    parent: GoMethod
) : AbstractGoInst(location, parent) {
    override val operands: List<GoExpr>
        get() = emptyList()

    override fun toString(): String = "run defers"

    override fun <T> accept(visitor: GoInstVisitor<T>): T {
        return visitor.visitGoRunDefersInst(this)
    }
}

class GoSendInst(
    location: GoInstLocation,
    parent: GoMethod,
    val chan: GoValue,
    val message: GoExpr
) : AbstractGoInst(location, parent) {
    override val operands: List<GoExpr>
        get() = listOf(chan, message)

    override fun toString(): String = "$chan <- $message"

    override fun <T> accept(visitor: GoInstVisitor<T>): T {
        return visitor.visitGoSendInst(this)
    }
}

class GoStoreInst(
    location: GoInstLocation,
    parent: GoMethod,
    override val lhv: GoValue,
    override val rhv: GoExpr
) : AbstractGoInst(location, parent), CoreAssignInst<GoInstLocation, GoMethod, GoValue, GoExpr, GoType> {
    override val operands: List<GoExpr>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "*$lhv = $rhv"

    override fun <T> accept(visitor: GoInstVisitor<T>): T {
        return visitor.visitGoStoreInst(this)
    }
}

class GoCallInst(
    location: GoInstLocation,
    parent: GoMethod,
    val callExpr: GoCallExpr
) : AbstractGoInst(location, parent), CoreCallInst<GoInstLocation, GoMethod, GoExpr> {
    override val operands: List<GoExpr>
        get() = listOf(callExpr)

    override fun toString(): String = "$callExpr"

    override fun <T> accept(visitor: GoInstVisitor<T>): T {
        return visitor.visitGoCallInst(this)
    }
}

interface GoTerminatingInst : GoInst

class GoReturnInst(
    location: GoInstLocation,
    parent: GoMethod,
    val returnValue: List<GoValue>
) : AbstractGoInst(location, parent), GoTerminatingInst, CoreReturnInst<GoInstLocation, GoMethod, GoExpr> {
    override val operands: List<GoExpr>
        get() = returnValue

    override fun toString(): String = "return" + (returnValue.let { " $it" } ?: "")

    override fun <T> accept(visitor: GoInstVisitor<T>): T {
        return visitor.visitGoReturnInst(this)
    }
}

class GoPanicInst(
    location: GoInstLocation,
    parent: GoMethod,
    val throwable: GoValue
) : AbstractGoInst(location, parent), GoTerminatingInst {
    override val operands: List<GoExpr>
        get() = listOf(throwable)

    override fun toString(): String = "throw $throwable"

    override fun <T> accept(visitor: GoInstVisitor<T>): T {
        return visitor.visitGoPanicInst(this)
    }
}

class GoGoInst(
    location: GoInstLocation,
    parent: GoMethod,
    //val method: GoMethod?,
    val func: GoValue,
    val args: List<GoValue>
) : AbstractGoInst(location, parent), CoreCallInst<GoInstLocation, GoMethod, GoExpr> {
    override val operands: List<GoExpr>
        get() = args

    override fun toString(): String = "go $func"

    override fun <T> accept(visitor: GoInstVisitor<T>): T {
        return visitor.visitGoGoInst(this)
    }
}

class GoDeferInst(
    location: GoInstLocation,
    parent: GoMethod,
    //val method: GoMethod?,
    val func: GoValue,
    val args: List<GoValue>
) : AbstractGoInst(location, parent), CoreCallInst<GoInstLocation, GoMethod, GoExpr> {
    override val operands: List<GoExpr>
        get() = args

    override fun toString(): String = "defer $func"

    override fun <T> accept(visitor: GoInstVisitor<T>): T {
        return visitor.visitGoDeferInst(this)
    }
}

interface GoBranchingInst : GoInst {
    val successors: List<GoInstRef>
}

class GoIfInst(
    location: GoInstLocation,
    parent: GoMethod,
    val condition: GoConditionExpr,
    val trueBranch: GoInstRef,
    val falseBranch: GoInstRef
) : AbstractGoInst(location, parent), GoBranchingInst, CoreIfInst<GoInstLocation, GoMethod, GoExpr> {
    override val operands: List<GoExpr>
        get() = listOf(condition)

    override val successors: List<GoInstRef>
        get() = listOf(trueBranch, falseBranch)

    override fun toString(): String = "if ($condition) then $trueBranch else $falseBranch"

    override fun <T> accept(visitor: GoInstVisitor<T>): T {
        return visitor.visitGoIfInst(this)
    }
}

class GoMapUpdateInst(
    location: GoInstLocation,
    parent: GoMethod,
    val map: GoValue,
    val key: GoExpr,
    val value: GoExpr
) : AbstractGoInst(location, parent) {
    override val operands: List<GoExpr>
        get() = listOf(map, key, value)

    override fun toString(): String = "$map[$key] = $value"

    override fun <T> accept(visitor: GoInstVisitor<T>): T {
        return visitor.visitGoMapUpdateInst(this)
    }
}

class GoDebugRefInst(
    location: GoInstLocation,
    parent: GoMethod,
) : AbstractGoInst(location, parent) {
    override val operands: List<GoExpr>
        get() = emptyList()

    override fun toString(): String = "debug ref"

    override fun <T> accept(visitor: GoInstVisitor<T>): T {
        return visitor.visitGoDebugRefInst(this)
    }
}

interface GoExpr : CoreExpr<GoType, GoValue> {
    override val type: GoType
    override val operands: List<GoValue>

    fun <T> accept(visitor: GoExprVisitor<T>): T // TODO visitor for CoreExpr?

    override fun <T> accept(visitor: CoreExprVisitor<T>): T {
        TODO("Not yet implemented")
    }
}

interface GoBinaryExpr : GoExpr {
    val lhv: GoValue
    val rhv: GoValue
}

interface GoConditionExpr : GoBinaryExpr

data class GoAllocExpr(
    override val type: GoType
) : GoExpr, GoValue {
    override val operands: List<GoValue>
        get() = emptyList()

    override fun toString(): String = "new ${type.typeName}"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoAllocExpr(this)
    }
}

data class GoAddExpr(
    override val type: GoType,
    override val lhv: GoValue,
    override val rhv: GoValue
) : GoBinaryExpr, GoValue {
    override val operands: List<GoValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv + $rhv"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoAddExpr(this)
    }
}

data class GoSubExpr(
    override val type: GoType,
    override val lhv: GoValue,
    override val rhv: GoValue
) : GoBinaryExpr, GoValue {
    override val operands: List<GoValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv - $rhv"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoSubExpr(this)
    }
}

data class GoMulExpr(
    override val type: GoType,
    override val lhv: GoValue,
    override val rhv: GoValue
) : GoBinaryExpr, GoValue {
    override val operands: List<GoValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv * $rhv"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoMulExpr(this)
    }
}

data class GoDivExpr(
    override val type: GoType,
    override val lhv: GoValue,
    override val rhv: GoValue
) : GoBinaryExpr, GoValue {
    override val operands: List<GoValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv / $rhv"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoDivExpr(this)
    }
}

data class GoModExpr(
    override val type: GoType,
    override val lhv: GoValue,
    override val rhv: GoValue
) : GoBinaryExpr, GoValue {
    override val operands: List<GoValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv % $rhv"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoModExpr(this)
    }
}

data class GoAndExpr(
    override val type: GoType,
    override val lhv: GoValue,
    override val rhv: GoValue
) : GoBinaryExpr, GoValue {
    override val operands: List<GoValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv & $rhv"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoAndExpr(this)
    }
}

data class GoOrExpr(
    override val type: GoType,
    override val lhv: GoValue,
    override val rhv: GoValue
) : GoBinaryExpr, GoValue {
    override val operands: List<GoValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv | $rhv"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoOrExpr(this)
    }
}

data class GoXorExpr(
    override val type: GoType,
    override val lhv: GoValue,
    override val rhv: GoValue
) : GoBinaryExpr, GoValue {
    override val operands: List<GoValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv ^ $rhv"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoXorExpr(this)
    }
}

data class GoShlExpr(
    override val type: GoType,
    override val lhv: GoValue,
    override val rhv: GoValue
) : GoBinaryExpr, GoValue {
    override val operands: List<GoValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv << $rhv"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoShlExpr(this)
    }
}

data class GoShrExpr(
    override val type: GoType,
    override val lhv: GoValue,
    override val rhv: GoValue
) : GoBinaryExpr, GoValue {
    override val operands: List<GoValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv >> $rhv"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoShrExpr(this)
    }
}

data class GoAndNotExpr(
    override val type: GoType,
    override val lhv: GoValue,
    override val rhv: GoValue
) : GoBinaryExpr, GoValue {
    override val operands: List<GoValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv &^ $rhv"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoAndNotExpr(this)
    }
}

data class GoEqlExpr(
    override val type: GoType,
    override val lhv: GoValue,
    override val rhv: GoValue
) : GoConditionExpr, GoValue {
    override val operands: List<GoValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv == $rhv"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoEqlExpr(this)
    }
}

data class GoNeqExpr(
    override val type: GoType,
    override val lhv: GoValue,
    override val rhv: GoValue
) : GoConditionExpr, GoValue {
    override val operands: List<GoValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv != $rhv"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoNeqExpr(this)
    }
}

data class GoLssExpr(
    override val type: GoType,
    override val lhv: GoValue,
    override val rhv: GoValue
) : GoConditionExpr, GoValue {
    override val operands: List<GoValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv < $rhv"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoLssExpr(this)
    }
}

data class GoLeqExpr(
    override val type: GoType,
    override val lhv: GoValue,
    override val rhv: GoValue
) : GoConditionExpr, GoValue {
    override val operands: List<GoValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv <= $rhv"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoLeqExpr(this)
    }
}

data class GoGtrExpr(
    override val type: GoType,
    override val lhv: GoValue,
    override val rhv: GoValue
) : GoConditionExpr, GoValue {
    override val operands: List<GoValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv > $rhv"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoGtrExpr(this)
    }
}

data class GoGeqExpr(
    override val type: GoType,
    override val lhv: GoValue,
    override val rhv: GoValue
) : GoConditionExpr, GoValue {
    override val operands: List<GoValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv >= $rhv"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoGeqExpr(this)
    }
}

interface GoUnaryExpr : GoExpr, GoValue {
    val value: GoValue
}

data class GoUnNotExpr(
    override val type: GoType,
    override val value: GoValue
) : GoUnaryExpr {
    override val operands: List<GoValue>
        get() = listOf(value)

    override fun toString(): String = "!$value"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoUnNotExpr(this)
    }
}

data class GoUnSubExpr(
    override val type: GoType,
    override val value: GoValue
) : GoUnaryExpr {
    override val operands: List<GoValue>
        get() = listOf(value)

    override fun toString(): String = "-$value"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoUnSubExpr(this)
    }
}

data class GoUnArrowExpr(
    override val type: GoType,
    override val value: GoValue,
    val commaOk: Boolean
) : GoUnaryExpr {
    override val operands: List<GoValue>
        get() = listOf(value)

    override fun toString(): String = "<-$value${if (commaOk) ",ok" else ""}"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoUnArrowExpr(this)
    }
}

data class GoUnMulExpr(
    override val type: GoType,
    override val value: GoValue
) : GoUnaryExpr {
    override val operands: List<GoValue>
        get() = listOf(value)

    override fun toString(): String = "*$value"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoUnMulExpr(this)
    }
}

data class GoUnXorExpr(
    override val type: GoType,
    override val value: GoValue
) : GoUnaryExpr {
    override val operands: List<GoValue>
        get() = listOf(value)

    override fun toString(): String = "^$value"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoUnXorExpr(this)
    }
}

data class GoCallExpr(
    override val type: GoType,
    val value: GoValue,
    val args: List<GoValue>,
) : GoExpr, GoValue {
    override val operands: List<GoValue>
        get() = listOf(value) + args

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoCallExpr(this)
    }
}

data class GoPhiExpr(
    override val type: GoType,
    var edges: List<GoValue>
) : GoExpr, GoValue {

    override val operands: List<GoValue>
        get() = edges

    override fun toString(): String = "phi [$edges]"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoPhiExpr(this)
    }
}

data class GoChangeTypeExpr(
    override val type: GoType,
    val operand: GoValue
) : GoExpr, GoValue {
    override val operands: List<GoValue>
        get() = listOf(operand)

    override fun toString(): String = "${type.typeName} -> $operand"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoChangeTypeExpr(this)
    }
}

data class GoConvertExpr(
    override val type: GoType,
    val operand: GoValue
) : GoExpr, GoValue {
    override val operands: List<GoValue>
        get() = listOf(operand)

    override fun toString(): String = "(${type.typeName}) $operand"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoConvertExpr(this)
    }
}

data class GoMultiConvertExpr(
    override val type: GoType,
    val operand: GoValue
) : GoExpr, GoValue {
    override val operands: List<GoValue>
        get() = listOf(operand)

    override fun toString(): String = "multi (${type.typeName}) $operand"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoMultiConvertExpr(this)
    }
}

data class GoChangeInterfaceExpr(
    override val type: GoType,
    val operand: GoValue
) : GoExpr, GoValue {
    override val operands: List<GoValue>
        get() = listOf(operand)

    override fun toString(): String = "change interface (${type.typeName}) $operand"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoChangeInterfaceExpr(this)
    }
}

data class GoSliceToArrayPointerExpr(
    override val type: GoType,
    val operand: GoValue
) : GoExpr, GoValue {
    override val operands: List<GoValue>
        get() = listOf(operand)

    override fun toString(): String = "slice to array pointer (${type.typeName}) $operand"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoSliceToArrayPointerExpr(this)
    }
}

data class GoMakeInterfaceExpr(
    override val type: GoType,
    val value: GoValue
) : GoExpr, GoValue {

    override val operands: List<GoValue>
        get() = listOf(value)

    override fun toString(): String = "interface{} <- ${type.typeName} ($value)"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoMakeInterfaceExpr(this)
    }
}

data class GoMakeClosureExpr(
    override val type: GoType,
    val func: GoMethod,
    val bindings: List<GoValue>
) : GoExpr, GoValue {

    override val operands: List<GoValue>
        get() = listOf(func) + bindings

    override fun toString(): String = "make closure $func [$bindings]"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoMakeClosureExpr(this)
    }
}

data class GoMakeSliceExpr(
    override val type: GoType,
    val len: GoValue,
    val cap: GoValue
) : GoExpr, GoValue {

    override val operands: List<GoValue>
        get() = listOf(len, cap)

    override fun toString(): String = "new []${type.typeName}($len, $cap)"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoMakeSliceExpr(this)
    }
}

data class GoSliceExpr(
    override val type: GoType,
    val array: GoValue,
    val low: GoValue,
    val high: GoValue,
    val max: GoValue
) : GoExpr, GoValue {

    override val operands: List<GoValue>
        get() = listOf(array, low, high, max)

    override fun toString(): String = "slice $array[$low:$high]:$max"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoSliceExpr(this)
    }
}

data class GoMakeMapExpr(
    override val type: GoType,
    val reserve: GoValue
) : GoExpr, GoValue {

    override val operands: List<GoValue>
        get() = listOf(reserve)

    override fun toString(): String = "make map ${type.typeName} ($reserve)"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoMakeMapExpr(this)
    }
}

data class GoMakeChanExpr(
    override val type: GoType,
    val size: GoValue
) : GoExpr, GoValue {

    override val operands: List<GoValue>
        get() = listOf(size)

    override fun toString(): String = "make chan ${type.typeName} ($size)"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoMakeChanExpr(this)
    }
}

data class GoFieldAddrExpr(
    override val type: GoType,
    val instance: GoValue,
    val field: Int
) : GoValue {
    override val operands: List<GoValue>
        get() = listOf(instance)

    override fun toString(): String = "addr $instance.[${field}]"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoFieldAddrExpr(this)
    }
}

data class GoFieldExpr(
    override val type: GoType,
    val instance: GoValue,
    val field: Int
) : GoValue {
    override val operands: List<GoValue>
        get() = listOf(instance)

    override fun toString(): String = "$instance.[${field}]"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoFieldExpr(this)
    }
}

data class GoIndexAddrExpr(
    override val type: GoType,
    val instance: GoValue,
    val index: GoValue
) : GoValue {
    override val operands: List<GoValue>
        get() = listOf(instance, index)

    override fun toString(): String = "addr ${instance}[${index}]"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoIndexAddrExpr(this)
    }
}

data class GoIndexExpr(
    override val type: GoType,
    val instance: GoValue,
    val index: GoValue
) : GoValue {
    override val operands: List<GoValue>
        get() = listOf(instance, index)

    override fun toString(): String = "${instance}[${index}]"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoIndexExpr(this)
    }
}

data class GoLookupExpr(
    override val type: GoType,
    val instance: GoValue,
    val index: GoValue
) : GoValue {
    override val operands: List<GoValue>
        get() = listOf(instance, index)

    override fun toString(): String = "lookup ${instance}[${index}]"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoLookupExpr(this)
    }
}

data class GoSelectExpr(
    override val type: GoType,
    val chans: List<GoValue>,
    val sends: List<GoValue?>,
    val blocking: Boolean
) : GoValue {
    override val operands: List<GoValue>
        get(): List<GoValue> {
            val res = mutableListOf<GoValue>()
            for (i in 0..chans.size) {
                res.add(chans[i])
                sends[i]?.let { res.add(it) }
            }
            return res
        }

    override fun toString(): String = "select ${if (blocking) "blocking" else "nonblocking"} [$chans, $sends]"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoSelectExpr(this)
    }
}

data class GoRangeExpr(
    override val type: GoType,
    val instance: GoValue
) : GoValue {
    override val operands: List<GoValue>
        get() = listOf(instance)

    override fun toString(): String = "range ${instance}:${type.typeName}"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoRangeExpr(this)
    }
}

data class GoNextExpr(
    override val type: GoType,
    val instance: GoValue
) : GoValue {
    override val operands: List<GoValue>
        get() = listOf(instance)

    override fun toString(): String = "next $instance"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoNextExpr(this)
    }
}

data class GoTypeAssertExpr(
    override val type: GoType,
    val instance: GoValue,
    val assertType: GoType
) : GoValue {
    override val operands: List<GoValue>
        get() = listOf(instance)

    override fun toString(): String = "typeassert $instance.($assertType)"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoTypeAssertExpr(this)
    }
}

data class GoExtractExpr(
    override val type: GoType,
    val instance: GoValue,
    val index: Int
) : GoValue {
    override val operands: List<GoValue>
        get() = listOf(instance)

    override fun toString(): String = "extract $instance [$index]"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoExtractExpr(this)
    }
}

interface GoSimpleValue : GoValue {

    override val operands: List<GoValue>
        get() = emptyList()

}

interface GoLocal : GoSimpleValue {
    val name: String
}

data class GoFreeVar(val index: Int, override val name: String, override val type: GoType) : GoLocal {
    override fun toString(): String = name

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoFreeVar(this)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GoFreeVar

        if (index != other.index) return false
        if (type != other.type) return false

        return true
    }

    override fun hashCode(): Int {
        var result = index
        result = 31 * result + type.hashCode()
        return result
    }
}

data class GoConst(val index: Int, override val name: String, override val type: GoType) : GoLocal {
    override fun toString(): String = "const $name"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoConst(this)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GoConst

        if (index != other.index) return false
        if (type != other.type) return false

        return true
    }

    override fun hashCode(): Int {
        var result = index
        result = 31 * result + type.hashCode()
        return result
    }
}

data class GoGlobal(val index: Int, override val name: String, override val type: GoType) : GoLocal {
    override fun toString(): String = "global $name"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoGlobal(this)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GoGlobal

        if (index != other.index) return false
        if (type != other.type) return false

        return true
    }

    override fun hashCode(): Int {
        var result = index
        result = 31 * result + type.hashCode()
        return result
    }
}

data class GoBuiltin(val index: Int, override val name: String, override val type: GoType) : GoLocal {
    override fun toString(): String = "builtin $name"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoBuiltin(this)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GoBuiltin

        if (index != other.index) return false
        if (type != other.type) return false

        return true
    }

    override fun hashCode(): Int {
        var result = index
        result = 31 * result + type.hashCode()
        return result
    }
}

data class GoFunction(
    override val type: GoType,
    override val operands: List<GoValue>,
    override val metName: String,
    override var blocks: List<GoBasicBlock>,
    val returnTypes: List<GoType>,
    val packageName: String
) : GoMethod {
    private var flowGraph: GoGraph? = null

    override fun toString(): String = "${packageName}::${metName}${
        operands.joinToString(
            prefix = "(",
            postfix = ")",
            separator = ", "
        )
    }:${returnTypes.joinToString( 
        prefix = "(",
        postfix = ")",
        separator = ", "
    )}"

    override fun flowGraph(): GoGraph {
        if (flowGraph == null) {
            flowGraph = GoBlockGraph(
                blocks,
                listOf<GoInst>() // TODO
            ).graph
        }
        return flowGraph!!
    }

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoFunction(this)
    }
}

data class GoParameter(val index: Int, override val name: String, override val type: GoType) : GoLocal {
    companion object {
        @JvmStatic
        fun of(index: Int, name: String?, type: GoType): GoParameter {
            return GoParameter(index, name ?: "arg$$index", type)
        }
    }

    override fun toString(): String = name

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoParameter(this)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GoParameter

        if (index != other.index) return false
        if (type != other.type) return false

        return true
    }

    override fun hashCode(): Int {
        var result = index
        result = 31 * result + type.hashCode()
        return result
    }
}

interface GoConstant : GoSimpleValue

interface GoNumericConstant : GoConstant {

    val value: Number

    fun isEqual(c: GoNumericConstant): Boolean = c.value == value

    fun isNotEqual(c: GoNumericConstant): Boolean = !isEqual(c)

    fun isLessThan(c: GoNumericConstant): Boolean

    fun isLessThanOrEqual(c: GoNumericConstant): Boolean = isLessThan(c) || isEqual(c)

    fun isGreaterThan(c: GoNumericConstant): Boolean

    fun isGreaterThanOrEqual(c: GoNumericConstant): Boolean = isGreaterThan(c) || isEqual(c)

    operator fun plus(c: GoNumericConstant): GoNumericConstant

    operator fun minus(c: GoNumericConstant): GoNumericConstant

    operator fun times(c: GoNumericConstant): GoNumericConstant

    operator fun div(c: GoNumericConstant): GoNumericConstant

    operator fun rem(c: GoNumericConstant): GoNumericConstant

    operator fun unaryMinus(): GoNumericConstant

}


data class GoBool(val value: Boolean, override val type: GoType) : GoConstant {
    override fun toString(): String = "$value"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoBool(this)
    }
}

data class GoByte(override val value: Byte, override val type: GoType) : GoNumericConstant {
    override fun toString(): String = "$value"

    override fun plus(c: GoNumericConstant): GoNumericConstant {
        return GoInt(value + c.value.toByte(), type)
    }

    override fun minus(c: GoNumericConstant): GoNumericConstant {
        return GoInt(value - c.value.toByte(), type)
    }

    override fun times(c: GoNumericConstant): GoNumericConstant {
        return GoInt(value * c.value.toByte(), type)
    }

    override fun div(c: GoNumericConstant): GoNumericConstant {
        return GoInt(value / c.value.toByte(), type)
    }

    override fun rem(c: GoNumericConstant): GoNumericConstant {
        return GoInt(value % c.value.toByte(), type)
    }

    override fun unaryMinus(): GoNumericConstant {
        return GoInt(-value, type)
    }

    override fun isLessThan(c: GoNumericConstant): Boolean {
        return value < c.value.toByte()
    }

    override fun isGreaterThan(c: GoNumericConstant): Boolean {
        return value > c.value.toByte()
    }

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoByte(this)
    }
}

data class GoChar(val value: Char, override val type: GoType) : GoConstant {
    override fun toString(): String = "$value"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoChar(this)
    }
}

data class GoShort(override val value: Short, override val type: GoType) : GoNumericConstant {
    override fun toString(): String = "$value"

    override fun plus(c: GoNumericConstant): GoNumericConstant {
        return GoInt(value + c.value.toShort(), type)
    }

    override fun minus(c: GoNumericConstant): GoNumericConstant {
        return GoInt(value - c.value.toShort(), type)
    }

    override fun times(c: GoNumericConstant): GoNumericConstant {
        return GoInt(value * c.value.toInt(), type)
    }

    override fun div(c: GoNumericConstant): GoNumericConstant {
        return GoInt(value / c.value.toShort(), type)
    }

    override fun rem(c: GoNumericConstant): GoNumericConstant {
        return GoInt(value % c.value.toShort(), type)
    }

    override fun unaryMinus(): GoNumericConstant {
        return GoInt(-value, type)
    }

    override fun isLessThan(c: GoNumericConstant): Boolean {
        return value < c.value.toShort()
    }

    override fun isGreaterThan(c: GoNumericConstant): Boolean {
        return value > c.value.toShort()
    }

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoShort(this)
    }
}

data class GoInt(override val value: Int, override val type: GoType) : GoNumericConstant {
    override fun toString(): String = "$value"

    override fun plus(c: GoNumericConstant): GoNumericConstant {
        return GoInt(value + c.value.toInt(), type)
    }

    override fun minus(c: GoNumericConstant): GoNumericConstant {
        return GoInt(value - c.value.toInt(), type)
    }

    override fun times(c: GoNumericConstant): GoNumericConstant {
        return GoInt(value * c.value.toInt(), type)
    }

    override fun div(c: GoNumericConstant): GoNumericConstant {
        return GoInt(value / c.value.toInt(), type)
    }

    override fun rem(c: GoNumericConstant): GoNumericConstant {
        return GoInt(value % c.value.toInt(), type)
    }

    override fun unaryMinus(): GoNumericConstant {
        return GoInt(-value, type)
    }

    override fun isLessThan(c: GoNumericConstant): Boolean {
        return value < c.value.toInt()
    }

    override fun isGreaterThan(c: GoNumericConstant): Boolean {
        return value > c.value.toInt()
    }

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoInt(this)
    }
}

data class GoLong(override val value: Long, override val type: GoType) : GoNumericConstant {
    override fun toString(): String = "$value"

    override fun plus(c: GoNumericConstant): GoNumericConstant {
        return GoLong(value + c.value.toLong(), type)
    }

    override fun minus(c: GoNumericConstant): GoNumericConstant {
        return GoLong(value - c.value.toLong(), type)
    }

    override fun times(c: GoNumericConstant): GoNumericConstant {
        return GoLong(value * c.value.toLong(), type)
    }

    override fun div(c: GoNumericConstant): GoNumericConstant {
        return GoLong(value / c.value.toLong(), type)
    }

    override fun rem(c: GoNumericConstant): GoNumericConstant {
        return GoLong(value % c.value.toLong(), type)
    }

    override fun unaryMinus(): GoNumericConstant {
        return GoLong(-value, type)
    }

    override fun isLessThan(c: GoNumericConstant): Boolean {
        return value < c.value.toLong()
    }

    override fun isGreaterThan(c: GoNumericConstant): Boolean {
        return value > c.value.toLong()
    }

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoLong(this)
    }
}

data class GoFloat(override val value: Float, override val type: GoType) : GoNumericConstant {
    override fun toString(): String = "$value"

    override fun plus(c: GoNumericConstant): GoNumericConstant {
        return GoFloat(value + c.value.toFloat(), type)
    }

    override fun minus(c: GoNumericConstant): GoNumericConstant {
        return GoFloat(value - c.value.toFloat(), type)
    }

    override fun times(c: GoNumericConstant): GoNumericConstant {
        return GoFloat(value * c.value.toFloat(), type)
    }

    override fun div(c: GoNumericConstant): GoNumericConstant {
        return GoFloat(value / c.value.toFloat(), type)
    }

    override fun rem(c: GoNumericConstant): GoNumericConstant {
        return GoFloat(value % c.value.toFloat(), type)
    }

    override fun unaryMinus(): GoNumericConstant {
        return GoFloat(-value, type)
    }

    override fun isLessThan(c: GoNumericConstant): Boolean {
        return value < c.value.toFloat()
    }

    override fun isGreaterThan(c: GoNumericConstant): Boolean {
        return value > c.value.toFloat()
    }

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoFloat(this)
    }
}

data class GoDouble(override val value: Double, override val type: GoType) : GoNumericConstant {
    override fun toString(): String = "$value"

    override fun plus(c: GoNumericConstant): GoNumericConstant {
        return GoDouble(value + c.value.toDouble(), type)
    }

    override fun minus(c: GoNumericConstant): GoNumericConstant {
        return GoDouble(value.div(c.value.toDouble()), type)
    }

    override fun times(c: GoNumericConstant): GoNumericConstant {
        return GoDouble(value * c.value.toDouble(), type)
    }

    override fun div(c: GoNumericConstant): GoNumericConstant {
        return GoDouble(value.div(c.value.toDouble()), type)
    }

    override fun rem(c: GoNumericConstant): GoNumericConstant {
        return GoDouble(value.rem(c.value.toDouble()), type)
    }

    override fun unaryMinus(): GoNumericConstant {
        return GoDouble(-value, type)
    }

    override fun isLessThan(c: GoNumericConstant): Boolean {
        return value < c.value.toDouble()
    }

    override fun isGreaterThan(c: GoNumericConstant): Boolean {
        return value > c.value.toDouble()
    }

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoDouble(this)
    }
}

class GoNullConstant() : GoConstant {
    override val type: GoType = NullType()

    override fun toString(): String = "null"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoNullConstant(this)
    }
}

data class GoStringConstant(val value: String, override val type: GoType) : GoConstant {
    override fun toString(): String = "\"$value\""

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoStringConstant(this)
    }
}
