package org.usvm.model

interface TsExpr : TsEntity {
    interface Visitor<out R> {
        fun visit(expr: TsNewExpr): R
        fun visit(expr: TsNewArrayExpr): R
        fun visit(expr: TsLengthExpr): R
        fun visit(expr: TsCastExpr): R
        fun visit(expr: TsInstanceOfExpr): R

        // Unary
        fun visit(expr: TsDeleteExpr): R
        fun visit(expr: TsAwaitExpr): R
        fun visit(expr: TsYieldExpr): R
        fun visit(expr: TsTypeOfExpr): R
        fun visit(expr: TsVoidExpr): R
        fun visit(expr: TsNotExpr): R
        fun visit(expr: TsBitNotExpr): R
        fun visit(expr: TsNegExpr): R
        fun visit(expr: TsUnaryPlusExpr): R
        fun visit(expr: TsPreIncExpr): R
        fun visit(expr: TsPreDecExpr): R
        fun visit(expr: TsPostIncExpr): R
        fun visit(expr: TsPostDecExpr): R

        // Relation
        fun visit(expr: TsEqExpr): R
        fun visit(expr: TsNotEqExpr): R
        fun visit(expr: TsStrictEqExpr): R
        fun visit(expr: TsStrictNotEqExpr): R
        fun visit(expr: TsLtExpr): R
        fun visit(expr: TsLtEqExpr): R
        fun visit(expr: TsGtExpr): R
        fun visit(expr: TsGtEqExpr): R
        fun visit(expr: TsInExpr): R

        // Arithmetic
        fun visit(expr: TsAddExpr): R
        fun visit(expr: TsSubExpr): R
        fun visit(expr: TsMulExpr): R
        fun visit(expr: TsDivExpr): R
        fun visit(expr: TsRemExpr): R
        fun visit(expr: TsExpExpr): R

        // Bitwise
        fun visit(expr: TsBitAndExpr): R
        fun visit(expr: TsBitOrExpr): R
        fun visit(expr: TsBitXorExpr): R
        fun visit(expr: TsLeftShiftExpr): R
        fun visit(expr: TsRightShiftExpr): R
        fun visit(expr: TsUnsignedRightShiftExpr): R

        // Logical
        fun visit(expr: TsAndExpr): R
        fun visit(expr: TsOrExpr): R

        // Call
        fun visit(expr: TsInstanceCallExpr): R
        fun visit(expr: TsStaticCallExpr): R
        fun visit(expr: TsPtrCallExpr): R

        interface Default<out R> : Visitor<R> {
            override fun visit(expr: TsNewExpr): R = defaultVisit(expr)
            override fun visit(expr: TsNewArrayExpr): R = defaultVisit(expr)
            override fun visit(expr: TsLengthExpr): R = defaultVisit(expr)
            override fun visit(expr: TsCastExpr): R = defaultVisit(expr)
            override fun visit(expr: TsInstanceOfExpr): R = defaultVisit(expr)

            override fun visit(expr: TsDeleteExpr): R = defaultVisit(expr)
            override fun visit(expr: TsAwaitExpr): R = defaultVisit(expr)
            override fun visit(expr: TsYieldExpr): R = defaultVisit(expr)
            override fun visit(expr: TsTypeOfExpr): R = defaultVisit(expr)
            override fun visit(expr: TsVoidExpr): R = defaultVisit(expr)
            override fun visit(expr: TsNotExpr): R = defaultVisit(expr)
            override fun visit(expr: TsBitNotExpr): R = defaultVisit(expr)
            override fun visit(expr: TsNegExpr): R = defaultVisit(expr)
            override fun visit(expr: TsUnaryPlusExpr): R = defaultVisit(expr)
            override fun visit(expr: TsPreIncExpr): R = defaultVisit(expr)
            override fun visit(expr: TsPreDecExpr): R = defaultVisit(expr)
            override fun visit(expr: TsPostIncExpr): R = defaultVisit(expr)
            override fun visit(expr: TsPostDecExpr): R = defaultVisit(expr)

            override fun visit(expr: TsEqExpr): R = defaultVisit(expr)
            override fun visit(expr: TsNotEqExpr): R = defaultVisit(expr)
            override fun visit(expr: TsStrictEqExpr): R = defaultVisit(expr)
            override fun visit(expr: TsStrictNotEqExpr): R = defaultVisit(expr)
            override fun visit(expr: TsLtExpr): R = defaultVisit(expr)
            override fun visit(expr: TsLtEqExpr): R = defaultVisit(expr)
            override fun visit(expr: TsGtExpr): R = defaultVisit(expr)
            override fun visit(expr: TsGtEqExpr): R = defaultVisit(expr)
            override fun visit(expr: TsInExpr): R = defaultVisit(expr)

            override fun visit(expr: TsAddExpr): R = defaultVisit(expr)
            override fun visit(expr: TsSubExpr): R = defaultVisit(expr)
            override fun visit(expr: TsMulExpr): R = defaultVisit(expr)
            override fun visit(expr: TsDivExpr): R = defaultVisit(expr)
            override fun visit(expr: TsRemExpr): R = defaultVisit(expr)
            override fun visit(expr: TsExpExpr): R = defaultVisit(expr)

            override fun visit(expr: TsBitAndExpr): R = defaultVisit(expr)
            override fun visit(expr: TsBitOrExpr): R = defaultVisit(expr)
            override fun visit(expr: TsBitXorExpr): R = defaultVisit(expr)
            override fun visit(expr: TsLeftShiftExpr): R = defaultVisit(expr)
            override fun visit(expr: TsRightShiftExpr): R = defaultVisit(expr)
            override fun visit(expr: TsUnsignedRightShiftExpr): R = defaultVisit(expr)

            override fun visit(expr: TsAndExpr): R = defaultVisit(expr)
            override fun visit(expr: TsOrExpr): R = defaultVisit(expr)

            override fun visit(expr: TsInstanceCallExpr): R = defaultVisit(expr)
            override fun visit(expr: TsStaticCallExpr): R = defaultVisit(expr)
            override fun visit(expr: TsPtrCallExpr): R = defaultVisit(expr)

            fun defaultVisit(expr: TsExpr): R
        }
    }

    override fun <R> accept(visitor: TsEntity.Visitor<R>): R {
        return accept(visitor as Visitor<R>)
    }

    fun <R> accept(visitor: Visitor<R>): R
}

data class TsNewExpr(
    val type: TypeName,
) : TsExpr {
    override fun toString(): String {
        return "new $type"
    }

    override fun <R> accept(visitor: TsExpr.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class TsNewArrayExpr(
    val elementType: TypeName,
    val size: TsEntity,
) : TsExpr {
    override fun toString(): String {
        return "new Array<$elementType>($size)"
    }

    override fun <R> accept(visitor: TsExpr.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class TsLengthExpr(
    val arg: TsEntity,
) : TsExpr {
    override fun toString(): String {
        return "${arg}.length"
    }

    override fun <R> accept(visitor: TsExpr.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class TsCastExpr(
    val arg: TsEntity,
    val type: TypeName,
) : TsExpr {
    override fun toString(): String {
        return "$arg as $type"
    }

    override fun <R> accept(visitor: TsExpr.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class TsInstanceOfExpr(
    val arg: TsEntity,
    val checkType: TsType,
) : TsExpr {
    override fun toString(): String {
        return "$arg instanceof $checkType"
    }

    override fun <R> accept(visitor: TsExpr.Visitor<R>): R {
        return visitor.visit(this)
    }
}

interface TsUnaryExpr : TsExpr {
    val arg: TsEntity
}

data class TsDeleteExpr(
    override val arg: TsEntity,
) : TsUnaryExpr {
    override fun toString(): String {
        return "delete $arg"
    }

    override fun <R> accept(visitor: TsExpr.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class TsAwaitExpr(
    override val arg: TsEntity,
) : TsUnaryExpr {
    override fun toString(): String {
        return "await $arg"
    }

    override fun <R> accept(visitor: TsExpr.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class TsYieldExpr(
    override val arg: TsEntity,
) : TsUnaryExpr {
    override fun toString(): String {
        return "yield $arg"
    }

    override fun <R> accept(visitor: TsExpr.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class TsTypeOfExpr(
    override val arg: TsEntity,
) : TsUnaryExpr {
    override fun toString(): String {
        return "typeof $arg"
    }

    override fun <R> accept(visitor: TsExpr.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class TsVoidExpr(
    override val arg: TsEntity,
) : TsUnaryExpr {
    override fun toString(): String {
        return "void $arg"
    }

    override fun <R> accept(visitor: TsExpr.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class TsNotExpr(
    override val arg: TsEntity,
) : TsUnaryExpr {
    override fun toString(): String {
        return "!$arg"
    }

    override fun <R> accept(visitor: TsExpr.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class TsBitNotExpr(
    override val arg: TsEntity,
) : TsUnaryExpr {
    override fun toString(): String {
        return "~$arg"
    }

    override fun <R> accept(visitor: TsExpr.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class TsNegExpr(
    override val arg: TsEntity,
) : TsUnaryExpr {
    override fun toString(): String {
        return "-$arg"
    }

    override fun <R> accept(visitor: TsExpr.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class TsUnaryPlusExpr(
    override val arg: TsEntity,
) : TsUnaryExpr {
    override fun toString(): String {
        return "+$arg"
    }

    override fun <R> accept(visitor: TsExpr.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class TsPreIncExpr(
    override val arg: TsEntity,
) : TsUnaryExpr {
    override fun toString(): String {
        return "++$arg"
    }

    override fun <R> accept(visitor: TsExpr.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class TsPreDecExpr(
    override val arg: TsEntity,
) : TsUnaryExpr {
    override fun toString(): String {
        return "--$arg"
    }

    override fun <R> accept(visitor: TsExpr.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class TsPostIncExpr(
    override val arg: TsEntity,
) : TsUnaryExpr {
    override fun toString(): String {
        return "$arg++"
    }

    override fun <R> accept(visitor: TsExpr.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class TsPostDecExpr(
    override val arg: TsEntity,
) : TsUnaryExpr {
    override fun toString(): String {
        return "$arg--"
    }

    override fun <R> accept(visitor: TsExpr.Visitor<R>): R {
        return visitor.visit(this)
    }
}

interface TsBinaryExpr : TsExpr {
    val left: TsEntity
    val right: TsEntity
}

interface TsRelationExpr : TsBinaryExpr

data class TsEqExpr(
    override val left: TsEntity,
    override val right: TsEntity,
) : TsRelationExpr {
    override fun toString(): String {
        return "$left == $right"
    }

    override fun <R> accept(visitor: TsExpr.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class TsNotEqExpr(
    override val left: TsEntity,
    override val right: TsEntity,
) : TsRelationExpr {
    override fun toString(): String {
        return "$left != $right"
    }

    override fun <R> accept(visitor: TsExpr.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class TsStrictEqExpr(
    override val left: TsEntity,
    override val right: TsEntity,
) : TsRelationExpr {
    override fun toString(): String {
        return "$left === $right"
    }

    override fun <R> accept(visitor: TsExpr.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class TsStrictNotEqExpr(
    override val left: TsEntity,
    override val right: TsEntity,
) : TsRelationExpr {
    override fun toString(): String {
        return "$left !== $right"
    }

    override fun <R> accept(visitor: TsExpr.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class TsLtExpr(
    override val left: TsEntity,
    override val right: TsEntity,
) : TsRelationExpr {
    override fun toString(): String {
        return "$left < $right"
    }

    override fun <R> accept(visitor: TsExpr.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class TsLtEqExpr(
    override val left: TsEntity,
    override val right: TsEntity,
) : TsRelationExpr {
    override fun toString(): String {
        return "$left <= $right"
    }

    override fun <R> accept(visitor: TsExpr.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class TsGtExpr(
    override val left: TsEntity,
    override val right: TsEntity,
) : TsRelationExpr {
    override fun toString(): String {
        return "$left > $right"
    }

    override fun <R> accept(visitor: TsExpr.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class TsGtEqExpr(
    override val left: TsEntity,
    override val right: TsEntity,
) : TsRelationExpr {
    override fun toString(): String {
        return "$left >= $right"
    }

    override fun <R> accept(visitor: TsExpr.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class TsInExpr(
    override val left: TsEntity,
    override val right: TsEntity,
) : TsRelationExpr {
    override fun toString(): String {
        return "$left in $right"
    }

    override fun <R> accept(visitor: TsExpr.Visitor<R>): R {
        return visitor.visit(this)
    }
}

interface TsArithmeticExpr : TsBinaryExpr

data class TsAddExpr(
    override val left: TsEntity,
    override val right: TsEntity,
) : TsArithmeticExpr {
    override fun toString(): String {
        return "$left + $right"
    }

    override fun <R> accept(visitor: TsExpr.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class TsSubExpr(
    override val left: TsEntity,
    override val right: TsEntity,
) : TsArithmeticExpr {
    override fun toString(): String {
        return "$left - $right"
    }

    override fun <R> accept(visitor: TsExpr.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class TsMulExpr(
    override val left: TsEntity,
    override val right: TsEntity,
) : TsArithmeticExpr {
    override fun toString(): String {
        return "$left * $right"
    }

    override fun <R> accept(visitor: TsExpr.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class TsDivExpr(
    override val left: TsEntity,
    override val right: TsEntity,
) : TsArithmeticExpr {
    override fun toString(): String {
        return "$left / $right"
    }

    override fun <R> accept(visitor: TsExpr.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class TsRemExpr(
    override val left: TsEntity,
    override val right: TsEntity,
) : TsArithmeticExpr {
    override fun toString(): String {
        return "$left % $right"
    }

    override fun <R> accept(visitor: TsExpr.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class TsExpExpr(
    override val left: TsEntity,
    override val right: TsEntity,
) : TsArithmeticExpr {
    override fun toString(): String {
        return "$left ** $right"
    }

    override fun <R> accept(visitor: TsExpr.Visitor<R>): R {
        return visitor.visit(this)
    }
}

interface TsBitwiseExpr : TsBinaryExpr

data class TsBitAndExpr(
    override val left: TsEntity,
    override val right: TsEntity,
) : TsBitwiseExpr {
    override fun toString(): String {
        return "$left & $right"
    }

    override fun <R> accept(visitor: TsExpr.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class TsBitOrExpr(
    override val left: TsEntity,
    override val right: TsEntity,
) : TsBitwiseExpr {
    override fun toString(): String {
        return "$left | $right"
    }

    override fun <R> accept(visitor: TsExpr.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class TsBitXorExpr(
    override val left: TsEntity,
    override val right: TsEntity,
) : TsBitwiseExpr {
    override fun toString(): String {
        return "$left ^ $right"
    }

    override fun <R> accept(visitor: TsExpr.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class TsLeftShiftExpr(
    override val left: TsEntity,
    override val right: TsEntity,
) : TsBitwiseExpr {
    override fun toString(): String {
        return "$left << $right"
    }

    override fun <R> accept(visitor: TsExpr.Visitor<R>): R {
        return visitor.visit(this)
    }
}

// Sign-propagating right shift
data class TsRightShiftExpr(
    override val left: TsEntity,
    override val right: TsEntity,
) : TsBitwiseExpr {
    override fun toString(): String {
        return "$left >> $right"
    }

    override fun <R> accept(visitor: TsExpr.Visitor<R>): R {
        return visitor.visit(this)
    }
}

// Zero-fill right shift
data class TsUnsignedRightShiftExpr(
    override val left: TsEntity,
    override val right: TsEntity,
) : TsBitwiseExpr {
    override fun toString(): String {
        return "$left >>> $right"
    }

    override fun <R> accept(visitor: TsExpr.Visitor<R>): R {
        return visitor.visit(this)
    }
}

interface TsLogicalExpr : TsBinaryExpr

data class TsAndExpr(
    override val left: TsEntity,
    override val right: TsEntity,
) : TsLogicalExpr {
    override fun toString(): String {
        return "$left && $right"
    }

    override fun <R> accept(visitor: TsExpr.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class TsOrExpr(
    override val left: TsEntity,
    override val right: TsEntity,
) : TsLogicalExpr {
    override fun toString(): String {
        return "$left || $right"
    }

    override fun <R> accept(visitor: TsExpr.Visitor<R>): R {
        return visitor.visit(this)
    }
}

interface TsCallExpr : TsExpr {
    val method: TsMethodSignature
    val args: List<TsValue>
}

data class TsInstanceCallExpr(
    val instance: TsLocal,
    override val method: TsMethodSignature,
    override val args: List<TsValue>,
) : TsCallExpr {
    override fun toString(): String {
        return "$instance.${method.name}(${args.joinToString()})"
    }

    override fun <R> accept(visitor: TsExpr.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class TsStaticCallExpr(
    override val method: TsMethodSignature,
    override val args: List<TsValue>,
) : TsCallExpr {
    override fun toString(): String {
        return "${method.enclosingClass.name}.${method.name}(${args.joinToString()})"
    }

    override fun <R> accept(visitor: TsExpr.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class TsPtrCallExpr(
    val ptr: TsLocal,
    override val method: TsMethodSignature,
    override val args: List<TsValue>,
) : TsCallExpr {
    override fun toString(): String {
        return "${ptr}.${method.name}(${args.joinToString()})"
    }

    override fun <R> accept(visitor: TsExpr.Visitor<R>): R {
        return visitor.visit(this)
    }
}
